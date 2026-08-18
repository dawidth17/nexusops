#include "nexusops/sysprobe.h"

#include <chrono>
#include <iomanip>
#include <iostream>
#include <thread>

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

    return 0;
}