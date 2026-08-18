#include "nexusops/agent/bounded_thread_pool.h"
#include "nexusops/agent/scheduler.h"
#include "nexusops/sysprobe.h"

#include <atomic>
#include <chrono>
#include <cstddef>
#include <iomanip>
#include <iostream>
#include <mutex>
#include <thread>
#include <vector>

namespace {

using nexusops::agent::BoundedThreadPool;
using nexusops::agent::Scheduler;

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

bool readNetworkInterfaces(
    std::vector<sysprobe_network_interface> &interfaces
)
{
    size_t count = 0;

    sysprobe_status status =
        sysprobe_read_network_interfaces(
            nullptr,
            0,
            &count
        );

    if (status != SYSPROBE_OK) {
        return false;
    }

    interfaces.resize(count);

    for (
        int attempt = 0;
        attempt < 3;
        ++attempt
    ) {
        size_t actualCount = 0;

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
                actualCount
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
    std::vector<sysprobe_process_info> &processes
)
{
    size_t count = 0;

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
        count + 16
    );

    for (
        int attempt = 0;
        attempt < 3;
        ++attempt
    ) {
        size_t actualCount = 0;

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
                actualCount + 16
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

}

int main()
{
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

    constexpr std::size_t workerCount = 3;
    constexpr std::size_t queueCapacity = 8;

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
        systemRuns{0};

    std::atomic<std::size_t>
        networkRuns{0};

    std::atomic<std::size_t>
        processRuns{0};

    sysprobe_cpu_times previousCpu{};

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
            std::chrono::milliseconds(400),
            [&]() {
                sysprobe_cpu_times currentCpu{};

                sysprobe_memory_info memory{};

                sysprobe_filesystem_info
                    filesystem{};

                sysprobe_uptime_info uptime{};

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

                double cpuUsagePercent = 0.0;

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
            std::chrono::milliseconds(600),
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

                {
                    std::lock_guard lock(
                        outputMutex
                    );

                    std::cout
                        << "network interfaces="
                        << interfaces.size();

                    if (!interfaces.empty()) {
                        std::cout
                            << " first="
                            << interfaces.front().name
                            << " rx_bytes="
                            << interfaces.front().rx_bytes
                            << " tx_bytes="
                            << interfaces.front().tx_bytes;
                    }

                    std::cout << '\n';
                }

                ++networkRuns;
            },
            true
        );

    const auto processResult =
        scheduler.schedulePeriodic(
            "processes",
            std::chrono::milliseconds(800),
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
            Scheduler::ScheduleResult::scheduled ||
        networkResult !=
            Scheduler::ScheduleResult::scheduled ||
        processResult !=
            Scheduler::ScheduleResult::scheduled
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
        std::chrono::milliseconds(1300)
    );

    scheduler.stop();

    threadPool.stop();

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
        << '\n';

    return collectionErrors.load() == 0
        ? 0
        : 1;
}