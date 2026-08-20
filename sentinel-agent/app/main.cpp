#include "nexusops/agent/bounded_thread_pool.h"
#include "nexusops/agent/diagnostic_cli.h"
#include "nexusops/agent/scheduler.h"
#include "nexusops/agent/signal_waiter.h"
#include "nexusops/agent/sqlite_spool.h"
#include "nexusops/sysprobe.h"

#include <atomic>
#include <charconv>
#include <chrono>
#include <cstddef>
#include <cstdint>
#include <cstdlib>
#include <ctime>
#include <exception>
#include <filesystem>
#include <iomanip>
#include <iostream>
#include <mutex>
#include <optional>
#include <sstream>
#include <stdexcept>
#include <string>
#include <string_view>
#include <vector>

#include <signal.h>

namespace {

using nexusops::agent::BoundedThreadPool;
using nexusops::agent::Scheduler;
using nexusops::agent::SignalEventType;
using nexusops::agent::SignalWaiter;
using nexusops::agent::SQLiteSpool;
using nexusops::agent::SpoolStatus;

struct RuntimeOptions {
    std::optional<
        std::chrono::seconds
    > runDuration;
};

struct StopRequest {
    std::string reason;

    int signalNumber{0};

    std::size_t reloadRequests{0};
};

RuntimeOptions parseOptions(
    int argc,
    char **argv
)
{
    RuntimeOptions options;

    for (
        int index = 1;
        index < argc;
        ++index
    ) {
        const std::string_view argument(
            argv[index]
        );

        if (
            argument ==
            "--run-seconds"
        ) {
            if (
                index + 1 >=
                argc
            ) {
                throw std::invalid_argument(
                    "--run-seconds requires a value"
                );
            }

            const std::string_view value(
                argv[++index]
            );

            long long seconds = 0;

            const auto result =
                std::from_chars(
                    value.data(),
                    value.data() +
                        value.size(),
                    seconds
                );

            if (
                result.ec !=
                    std::errc{} ||
                result.ptr !=
                    value.data() +
                        value.size() ||
                seconds <= 0 ||
                seconds > 86400
            ) {
                throw std::invalid_argument(
                    "--run-seconds must be between 1 and 86400"
                );
            }

            options.runDuration =
                std::chrono::seconds(
                    seconds
                );

            continue;
        }

        throw std::invalid_argument(
            "unknown argument: " +
            std::string(argument)
        );
    }

    return options;
}

std::string resolveSpoolPath()
{
    const char *configuredPath =
        std::getenv(
            "NEXUSOPS_SENTINEL_SPOOL_PATH"
        );

    if (
        configuredPath != nullptr &&
        configuredPath[0] != '\0'
    ) {
        return configuredPath;
    }

    const char *xdgStateHome =
        std::getenv(
            "XDG_STATE_HOME"
        );

    if (
        xdgStateHome != nullptr &&
        xdgStateHome[0] != '\0'
    ) {
        return (
            std::filesystem::path(
                xdgStateHome
            ) /
            "nexusops-sentinel" /
            "telemetry.db"
        ).string();
    }

    const char *home =
        std::getenv(
            "HOME"
        );

    if (
        home != nullptr &&
        home[0] != '\0'
    ) {
        return (
            std::filesystem::path(
                home
            ) /
            ".local" /
            "state" /
            "nexusops-sentinel" /
            "telemetry.db"
        ).string();
    }

    return
        "/tmp/nexusops-sentinel-telemetry.db";
}

void ensureSpoolDirectory(
    const std::string &spoolPath
)
{
    const std::filesystem::path path(
        spoolPath
    );

    const auto parent =
        path.parent_path();

    if (parent.empty()) {
        return;
    }

    std::error_code error;

    std::filesystem::create_directories(
        parent,
        error
    );

    if (error) {
        throw std::runtime_error(
            "failed to create spool directory: " +
            error.message()
        );
    }
}

const char *signalName(
    int signalNumber
)
{
    switch (signalNumber) {
        case SIGTERM:
            return "SIGTERM";

        case SIGINT:
            return "SIGINT";

        case SIGHUP:
            return "SIGHUP";

        default:
            return "UNKNOWN";
    }
}

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

StopRequest waitForStop(
    SignalWaiter &signalWaiter,
    const RuntimeOptions &options,
    std::mutex &outputMutex
)
{
    StopRequest request;

    if (!options.runDuration.has_value()) {
        for (;;) {
            const auto event =
                signalWaiter.wait();

            if (
                event.type ==
                SignalEventType::reload
            ) {
                ++request.reloadRequests;

                std::lock_guard lock(
                    outputMutex
                );

                std::cout
                    << "lifecycle reload_requested signal="
                    << signalName(
                        event.signalNumber
                    )
                    << '\n';

                continue;
            }

            request.reason =
                "signal";

            request.signalNumber =
                event.signalNumber;

            return request;
        }
    }

    const auto deadline =
        std::chrono::steady_clock::now() +
        options.runDuration.value();

    for (;;) {
        const auto now =
            std::chrono::steady_clock::now();

        if (now >= deadline) {
            request.reason =
                "runtime_limit";

            return request;
        }

        const auto remaining =
            std::chrono::duration_cast<
                std::chrono::milliseconds
            >(
                deadline - now
            );

        if (
            remaining <=
            std::chrono::milliseconds::zero()
        ) {
            request.reason =
                "runtime_limit";

            return request;
        }

        const auto event =
            signalWaiter.waitFor(
                remaining
            );

        if (
            event.type ==
            SignalEventType::timeout
        ) {
            request.reason =
                "runtime_limit";

            return request;
        }

        if (
            event.type ==
            SignalEventType::reload
        ) {
            ++request.reloadRequests;

            std::lock_guard lock(
                outputMutex
            );

            std::cout
                << "lifecycle reload_requested signal="
                << signalName(
                    event.signalNumber
                )
                << '\n';

            continue;
        }

        request.reason =
            "signal";

        request.signalNumber =
            event.signalNumber;

        return request;
    }
}

int runAgent(
    const RuntimeOptions &options,
    SignalWaiter &signalWaiter,
    const std::string &spoolPath
)
{
    constexpr std::size_t
        workerCount = 3;

    constexpr std::size_t
        queueCapacity = 8;

    constexpr std::size_t
        maxSpoolRecords = 100000;

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
            std::chrono::seconds(5),
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
            std::chrono::seconds(10),
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
                        << interfaces.size()
                        << '\n';
                }

                ++networkRuns;
            },
            true
        );

    const auto processResult =
        scheduler.schedulePeriodic(
            "processes",
            std::chrono::seconds(15),
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
            Scheduler::ScheduleResult::
                scheduled ||
        networkResult !=
            Scheduler::ScheduleResult::
                scheduled ||
        processResult !=
            Scheduler::ScheduleResult::
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

    {
        std::lock_guard lock(
            outputMutex
        );

        std::cout
            << "lifecycle state=running"
            << " spool_path="
            << spoolPath
            << '\n';
    }

    const StopRequest stopRequest =
        waitForStop(
            signalWaiter,
            options,
            outputMutex
        );

    if (
        stopRequest.reason ==
        "signal"
    ) {
        std::lock_guard lock(
            outputMutex
        );

        std::cout
            << "lifecycle shutdown_requested signal="
            << signalName(
                stopRequest.signalNumber
            )
            << '\n';
    } else {
        std::lock_guard lock(
            outputMutex
        );

        std::cout
            << "lifecycle runtime_limit_reached"
            << '\n';
    }

    scheduler.stop();

    threadPool.stop();

    std::size_t spoolRecords = 0;

    if (
        spool.count(
            spoolRecords
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
        << scheduler.droppedSubmissionCount()
        << " collection_errors="
        << collectionErrors.load()
        << " spool_records="
        << spoolRecords
        << " spool_full="
        << spoolFullCount.load()
        << " spool_errors="
        << spoolErrorCount.load()
        << '\n';

    std::cout
        << "lifecycle graceful_shutdown=true"
        << " stop_reason="
        << stopRequest.reason
        << " signal="
        << (
            stopRequest.signalNumber == 0
                ? "none"
                : signalName(
                    stopRequest.signalNumber
                )
        )
        << " reload_requests="
        << stopRequest.reloadRequests
        << '\n';

    return 0;
}

}

int main(
    int argc,
    char **argv
)
{
    try {
        const auto diagnosticExit =
            nexusops::agent::
                tryRunDiagnosticCommand(
                    argc,
                    argv
                );

        if (
            diagnosticExit.has_value()
        ) {
            return diagnosticExit.value();
        }

        const RuntimeOptions options =
            parseOptions(
                argc,
                argv
            );

        SignalWaiter signalWaiter;

        const std::string spoolPath =
            resolveSpoolPath();

        ensureSpoolDirectory(
            spoolPath
        );

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

        return runAgent(
            options,
            signalWaiter,
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