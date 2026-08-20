#include "nexusops/agent/bounded_thread_pool.h"
#include "nexusops/agent/scheduler.h"
#include "nexusops/agent/sqlite_spool.h"
#include "nexusops/sysprobe.h"

#include <atomic>
#include <chrono>
#include <cstddef>
#include <cstdint>
#include <ctime>
#include <exception>
#include <filesystem>
#include <iomanip>
#include <iostream>
#include <mutex>
#include <sstream>
#include <string>
#include <thread>
#include <vector>

#include <unistd.h>

namespace {

using nexusops::agent::BoundedThreadPool;
using nexusops::agent::Scheduler;
using nexusops::agent::SQLiteSpool;
using nexusops::agent::SpoolStatus;
using nexusops::agent::TelemetryRecord;

class SpoolFileCleanup {
public:
    explicit SpoolFileCleanup(
        std::string path
    )
        : path_(std::move(path))
    {
    }

    ~SpoolFileCleanup()
    {
        std::error_code error;

        std::filesystem::remove(
            path_,
            error
        );

        error.clear();

        std::filesystem::remove(
            path_ + "-wal",
            error
        );

        error.clear();

        std::filesystem::remove(
            path_ + "-shm",
            error
        );
    }

private:
    std::string path_;
};

std::string utcNow()
{
    const auto now =
        std::chrono::system_clock::now();

    const std::time_t time =
        std::chrono::system_clock::
            to_time_t(now);

    std::tm utc{};

    gmtime_r(
        &time,
        &utc
    );

    std::ostringstream output;

    output
        << std::put_time(
            &utc,
            "%Y-%m-%dT%H:%M:%SZ"
        );

    return output.str();
}

bool readNetworkInterfaces(
    std::vector<
        sysprobe_network_interface
    > &interfaces
)
{
    std::size_t count = 0;

    sysprobe_status status =
        sysprobe_read_network_interfaces(
            nullptr,
            0,
            &count
        );

    if (status != SYSPROBE_OK) {
        return false;
    }

    interfaces.resize(
        count + 4
    );

    for (
        int attempt = 0;
        attempt < 3;
        ++attempt
    ) {
        std::size_t actualCount = 0;

        status =
            sysprobe_read_network_interfaces(
                interfaces.data(),
                interfaces.size(),
                &actualCount
            );

        if (
            status ==
            SYSPROBE_ERROR_BUFFER_TOO_SMALL
        ) {
            interfaces.resize(
                actualCount + 4
            );

            continue;
        }

        if (status != SYSPROBE_OK) {
            return false;
        }

        interfaces.resize(
            actualCount
        );

        return true;
    }

    return false;
}

bool readProcesses(
    std::vector<
        sysprobe_process_info
    > &processes
)
{
    std::size_t count = 0;

    sysprobe_status status =
        sysprobe_read_processes(
            nullptr,
            0,
            &count
        );

    if (status != SYSPROBE_OK) {
        return false;
    }

    processes.resize(
        count + 32
    );

    for (
        int attempt = 0;
        attempt < 3;
        ++attempt
    ) {
        std::size_t actualCount = 0;

        status =
            sysprobe_read_processes(
                processes.data(),
                processes.size(),
                &actualCount
            );

        if (
            status ==
            SYSPROBE_ERROR_BUFFER_TOO_SMALL
        ) {
            processes.resize(
                actualCount + 32
            );

            continue;
        }

        if (status != SYSPROBE_OK) {
            return false;
        }

        processes.resize(
            actualCount
        );

        return true;
    }

    return false;
}

void persistTelemetry(
    SQLiteSpool &spool,
    std::string kind,
    std::string payload,
    std::atomic<std::size_t>
        &spoolFullCount,
    std::atomic<std::size_t>
        &spoolErrorCount
)
{
    std::int64_t sequence = 0;

    const auto status =
        spool.enqueue(
            utcNow(),
            std::move(kind),
            std::move(payload),
            &sequence
        );

    if (
        status ==
        SpoolStatus::full
    ) {
        ++spoolFullCount;

        return;
    }

    if (
        status !=
        SpoolStatus::ok
    ) {
        ++spoolErrorCount;
    }
}

std::string buildSystemPayload(
    double cpuUsagePercent,
    const sysprobe_memory_info &memory,
    const sysprobe_filesystem_info
        &filesystem,
    const sysprobe_uptime_info &uptime
)
{
    std::ostringstream payload;

    payload
        << std::fixed
        << std::setprecision(2)
        << "cpu_usage_percent="
        << cpuUsagePercent
        << ";memory_used_bytes="
        << memory.used_bytes
        << ";filesystem_used_bytes="
        << filesystem.used_bytes
        << ";uptime_seconds="
        << uptime.uptime_seconds;

    return payload.str();
}

std::string buildNetworkPayload(
    const std::vector<
        sysprobe_network_interface
    > &interfaces
)
{
    std::ostringstream payload;

    payload
        << "interface_count="
        << interfaces.size();

    if (!interfaces.empty()) {
        payload
            << ";first="
            << interfaces.front().name
            << ";rx_bytes="
            << interfaces.front().
                rx_bytes
            << ";tx_bytes="
            << interfaces.front().
                tx_bytes;
    }

    return payload.str();
}

std::string buildProcessPayload(
    const std::vector<
        sysprobe_process_info
    > &processes
)
{
    std::ostringstream payload;

    payload
        << "process_count="
        << processes.size();

    return payload.str();
}

int runDemo(
    const std::string &spoolPath
)
{
    constexpr std::size_t
        workerCount = 3;

    constexpr std::size_t
        queueCapacity = 8;

    constexpr std::size_t
        maxSpoolRecords = 4096;

    std::atomic<std::size_t>
        collectionErrors{0};

    std::atomic<std::size_t>
        spoolFullCount{0};

    std::atomic<std::size_t>
        spoolErrorCount{0};

    std::atomic<std::size_t>
        systemRuns{0};

    std::atomic<std::size_t>
        networkRuns{0};

    std::atomic<std::size_t>
        processRuns{0};

    std::size_t
        recordsBeforeReopen = 0;

    {
        SQLiteSpool spool(
            spoolPath,
            maxSpoolRecords
        );

        BoundedThreadPool threadPool(
            workerCount,
            queueCapacity
        );

        Scheduler scheduler(
            threadPool
        );

        std::mutex outputMutex;
        std::mutex cpuMutex;

        sysprobe_cpu_times
            previousCpu{};

        if (
            sysprobe_read_cpu_times(
                &previousCpu
            ) != SYSPROBE_OK
        ) {
            std::cerr
                << "failed to read initial cpu snapshot\n";

            return 1;
        }

        const auto systemResult =
            scheduler.schedulePeriodic(
                "system",
                std::chrono::
                    milliseconds(400),
                [&]() {
                    sysprobe_cpu_times
                        currentCpu{};

                    sysprobe_memory_info
                        memory{};

                    sysprobe_filesystem_info
                        filesystem{};

                    sysprobe_uptime_info
                        uptime{};

                    if (
                        sysprobe_read_cpu_times(
                            &currentCpu
                        ) != SYSPROBE_OK ||
                        sysprobe_read_memory_info(
                            &memory
                        ) != SYSPROBE_OK ||
                        sysprobe_read_filesystem_info(
                            "/",
                            &filesystem
                        ) != SYSPROBE_OK ||
                        sysprobe_read_uptime(
                            &uptime
                        ) != SYSPROBE_OK
                    ) {
                        ++collectionErrors;

                        return;
                    }

                    double cpuUsagePercent =
                        0.0;

                    {
                        std::lock_guard lock(
                            cpuMutex
                        );

                        if (
                            sysprobe_calculate_cpu_usage_percent(
                                &previousCpu,
                                &currentCpu,
                                &cpuUsagePercent
                            ) != SYSPROBE_OK
                        ) {
                            previousCpu =
                                currentCpu;

                            ++collectionErrors;

                            return;
                        }

                        previousCpu =
                            currentCpu;
                    }

                    persistTelemetry(
                        spool,
                        "system",
                        buildSystemPayload(
                            cpuUsagePercent,
                            memory,
                            filesystem,
                            uptime
                        ),
                        spoolFullCount,
                        spoolErrorCount
                    );

                    {
                        std::lock_guard lock(
                            outputMutex
                        );

                        std::cout
                            << std::fixed
                            << std::setprecision(2)
                            << "system cpu_usage_percent="
                            << cpuUsagePercent
                            << " memory_used_bytes="
                            << memory.used_bytes
                            << " filesystem_used_bytes="
                            << filesystem.used_bytes
                            << " uptime_seconds="
                            << uptime.uptime_seconds
                            << '\n';
                    }

                    ++systemRuns;
                },
                false
            );

        const auto networkResult =
            scheduler.schedulePeriodic(
                "network",
                std::chrono::
                    milliseconds(600),
                [&]() {
                    std::vector<
                        sysprobe_network_interface
                    > interfaces;

                    if (
                        !readNetworkInterfaces(
                            interfaces
                        )
                    ) {
                        ++collectionErrors;

                        return;
                    }

                    persistTelemetry(
                        spool,
                        "network",
                        buildNetworkPayload(
                            interfaces
                        ),
                        spoolFullCount,
                        spoolErrorCount
                    );

                    {
                        std::lock_guard lock(
                            outputMutex
                        );

                        std::cout
                            << "network interfaces="
                            << interfaces.size();

                        if (
                            !interfaces.empty()
                        ) {
                            std::cout
                                << " first="
                                << interfaces.front().
                                    name
                                << " rx_bytes="
                                << interfaces.front().
                                    rx_bytes
                                << " tx_bytes="
                                << interfaces.front().
                                    tx_bytes;
                        }

                        std::cout
                            << '\n';
                    }

                    ++networkRuns;
                },
                true
            );

        const auto processResult =
            scheduler.schedulePeriodic(
                "processes",
                std::chrono::
                    milliseconds(800),
                [&]() {
                    std::vector<
                        sysprobe_process_info
                    > processes;

                    if (
                        !readProcesses(
                            processes
                        )
                    ) {
                        ++collectionErrors;

                        return;
                    }

                    persistTelemetry(
                        spool,
                        "processes",
                        buildProcessPayload(
                            processes
                        ),
                        spoolFullCount,
                        spoolErrorCount
                    );

                    {
                        std::lock_guard lock(
                            outputMutex
                        );

                        std::cout
                            << "processes count="
                            << processes.size()
                            << '\n';
                    }

                    ++processRuns;
                },
                true
            );

        if (
            systemResult !=
                Scheduler::
                    ScheduleResult::
                    scheduled ||
            networkResult !=
                Scheduler::
                    ScheduleResult::
                    scheduled ||
            processResult !=
                Scheduler::
                    ScheduleResult::
                    scheduled
        ) {
            std::cerr
                << "failed to register scheduled jobs\n";

            return 1;
        }

        if (!scheduler.start()) {
            std::cerr
                << "failed to start scheduler\n";

            return 1;
        }

        std::this_thread::sleep_for(
            std::chrono::
                milliseconds(1300)
        );

        scheduler.stop();

        threadPool.stop();

        if (
            spool.count(
                recordsBeforeReopen
            ) != SpoolStatus::ok
        ) {
            std::cerr
                << "failed to count spool records: "
                << spool.lastError()
                << '\n';

            return 1;
        }

        std::cout
            << "runtime worker_count="
            << threadPool.workerCount()
            << " queue_capacity="
            << threadPool.queueCapacity()
            << " system_runs="
            << systemRuns.load()
            << " network_runs="
            << networkRuns.load()
            << " process_runs="
            << processRuns.load()
            << " dropped_submissions="
            << scheduler.
                droppedSubmissionCount()
            << " collection_errors="
            << collectionErrors.load()
            << " spool_records="
            << recordsBeforeReopen
            << " spool_full="
            << spoolFullCount.load()
            << " spool_errors="
            << spoolErrorCount.load()
            << '\n';
    }

    std::size_t
        reopenedCount = 0;

    std::size_t
        acknowledgedCount = 0;

    std::size_t
        remainingCount = 0;

    {
        SQLiteSpool reopenedSpool(
            spoolPath,
            maxSpoolRecords
        );

        if (
            reopenedSpool.count(
                reopenedCount
            ) != SpoolStatus::ok
        ) {
            std::cerr
                << "failed to count reopened spool\n";

            return 1;
        }

        std::vector<
            TelemetryRecord
        > records;

        if (reopenedCount > 0) {
            if (
                reopenedSpool.peekOldest(
                    maxSpoolRecords,
                    records
                ) != SpoolStatus::ok
            ) {
                std::cerr
                    << "failed to read reopened spool\n";

                return 1;
            }

            if (!records.empty()) {
                if (
                    reopenedSpool.
                        acknowledgeThrough(
                            records.back().
                                sequence,
                            acknowledgedCount
                        ) != SpoolStatus::ok
                ) {
                    std::cerr
                        << "failed to acknowledge spool records\n";

                    return 1;
                }
            }
        }

        if (
            reopenedSpool.count(
                remainingCount
            ) != SpoolStatus::ok
        ) {
            std::cerr
                << "failed to count remaining spool records\n";

            return 1;
        }
    }

    std::cout
        << "spool persisted_records="
        << reopenedCount
        << " acknowledged_records="
        << acknowledgedCount
        << " remaining_records="
        << remainingCount
        << '\n';

    if (
        reopenedCount !=
        recordsBeforeReopen
    ) {
        return 1;
    }

    if (
        remainingCount != 0
    ) {
        return 1;
    }

    if (
        collectionErrors.load() != 0 ||
        spoolFullCount.load() != 0 ||
        spoolErrorCount.load() != 0
    ) {
        return 1;
    }

    return 0;
}

}

int main()
{
    const std::string spoolPath =
        "/tmp/nexusops-sentinel-spool-" +
        std::to_string(
            getpid()
        ) +
        ".db";

    SpoolFileCleanup cleanup(
        spoolPath
    );

    try {
        std::cout
            << "SentinelAgent "
            << SENTINEL_AGENT_VERSION
            << " using libsysprobe "
            << sysprobe_version_major()
            << '.'
            << sysprobe_version_minor()
            << '.'
            << sysprobe_version_patch()
            << '\n';

        return runDemo(
            spoolPath
        );
    } catch (
        const std::exception &error
    ) {
        std::cerr
            << "SentinelAgent failed: "
            << error.what()
            << '\n';

        return 1;
    }
}