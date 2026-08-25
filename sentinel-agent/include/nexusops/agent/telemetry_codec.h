#ifndef NEXUSOPS_TELEMETRY_CODEC_H
#define NEXUSOPS_TELEMETRY_CODEC_H

#include "nexusops/sysprobe.h"

#include <string>
#include <vector>

namespace nexusops::agent {

bool encodeSystemMetrics(
    double cpuUsagePercent,
    const sysprobe_memory_info &memory,
    const sysprobe_filesystem_info &filesystem,
    const sysprobe_uptime_info &uptime,
    std::string &payload
);

bool encodeNetworkSnapshot(
    const std::vector<
        sysprobe_network_interface
    > &interfaces,
    std::string &payload
);

bool encodeProcessSnapshot(
    const std::vector<
        sysprobe_process_info
    > &processes,
    std::string &payload
);

}

#endif