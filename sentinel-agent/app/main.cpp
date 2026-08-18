#include "nexusops/sysprobe.h"

#include <algorithm>
#include <chrono>
#include <iomanip>
#include <iostream>
#include <thread>
#include <vector>

namespace {

bool isSuccess(
    sysprobe_status status,
    const char *collectorName
)
{
    if (status == SYSPROBE_OK) {
        return true;
    }

    std::cerr
        << "collector failed: "
        << collectorName
        << ", status="
        << static_cast<int>(status)
        << '\n';

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
        << "SentinelAgent using libsysprobe "
        << sysprobe_version_major()
        << '.'
        << sysprobe_version_minor()
        << '.'
        << sysprobe_version_patch()
        << '\n';

    sysprobe_cpu_times firstCpu{};
    sysprobe_cpu_times secondCpu{};

    if (
        !isSuccess(
            sysprobe_read_cpu_times(
                &firstCpu
            ),
            "cpu"
        )
    ) {
        return 1;
    }

    std::this_thread::sleep_for(
        std::chrono::milliseconds(500)
    );

    if (
        !isSuccess(
            sysprobe_read_cpu_times(
                &secondCpu
            ),
            "cpu"
        )
    ) {
        return 1;
    }

    double cpuUsagePercent = 0.0;

    if (
        !isSuccess(
            sysprobe_calculate_cpu_usage_percent(
                &firstCpu,
                &secondCpu,
                &cpuUsagePercent
            ),
            "cpu"
        )
    ) {
        return 1;
    }

    sysprobe_memory_info memory{};

    if (
        !isSuccess(
            sysprobe_read_memory_info(
                &memory
            ),
            "memory"
        )
    ) {
        return 1;
    }

    sysprobe_filesystem_info filesystem{};

    if (
        !isSuccess(
            sysprobe_read_filesystem_info(
                "/",
                &filesystem
            ),
            "filesystem"
        )
    ) {
        return 1;
    }

    sysprobe_uptime_info uptime{};

    if (
        !isSuccess(
            sysprobe_read_uptime(
                &uptime
            ),
            "uptime"
        )
    ) {
        return 1;
    }

    std::vector<
        sysprobe_network_interface
    > interfaces;

    if (!readNetworkInterfaces(interfaces)) {
        std::cerr
            << "collector failed: network\n";

        return 1;
    }

    std::vector<
        sysprobe_process_info
    > processes;

    if (!readProcesses(processes)) {
        std::cerr
            << "collector failed: processes\n";

        return 1;
    }

    std::cout
        << std::fixed
        << std::setprecision(2);

    std::cout
        << "cpu_usage_percent="
        << cpuUsagePercent
        << '\n';

    std::cout
        << "memory_total_bytes="
        << memory.total_bytes
        << '\n';

    std::cout
        << "memory_available_bytes="
        << memory.available_bytes
        << '\n';

    std::cout
        << "memory_used_bytes="
        << memory.used_bytes
        << '\n';

    std::cout
        << "filesystem_total_bytes="
        << filesystem.total_bytes
        << '\n';

    std::cout
        << "filesystem_available_bytes="
        << filesystem.available_bytes
        << '\n';

    std::cout
        << "filesystem_used_bytes="
        << filesystem.used_bytes
        << '\n';

    std::cout
        << "uptime_seconds="
        << uptime.uptime_seconds
        << '\n';

    std::cout
        << "network_interface_count="
        << interfaces.size()
        << '\n';

    for (
        const auto &interface :
        interfaces
    ) {
        std::cout
            << "interface="
            << interface.name
            << " ipv4="
            << (
                interface.ipv4_address[0] != '\0'
                    ? interface.ipv4_address
                    : "-"
            )
            << " ipv6="
            << (
                interface.ipv6_address[0] != '\0'
                    ? interface.ipv6_address
                    : "-"
            )
            << " rx_bytes="
            << interface.rx_bytes
            << " tx_bytes="
            << interface.tx_bytes
            << '\n';
    }

    std::cout
        << "process_count="
        << processes.size()
        << '\n';

    size_t visibleProcessCount =
        std::min<size_t>(
            processes.size(),
            5
        );

    for (
        size_t index = 0;
        index < visibleProcessCount;
        ++index
    ) {
        const auto &process =
            processes[index];

        std::cout
            << "process pid="
            << process.pid
            << " ppid="
            << process.parent_pid
            << " state="
            << process.state
            << " rss_bytes="
            << process.resident_memory_bytes
            << " name="
            << process.name
            << '\n';
    }

    return 0;
}