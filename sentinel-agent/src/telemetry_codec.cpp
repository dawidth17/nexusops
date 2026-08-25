#include "nexusops/agent/telemetry_codec.h"

#include "nexusops/telemetry/v1/telemetry.pb.h"

#include <cstddef>
#include <string>

namespace {

std::string boundedString(
    const char *value,
    std::size_t capacity
)
{
    std::size_t length = 0;

    while (
        length < capacity &&
        value[length] != '\0'
    ) {
        ++length;
    }

    return std::string(
        value,
        length
    );
}

}

namespace nexusops::agent {

bool encodeSystemMetrics(
    double cpuUsagePercent,
    const sysprobe_memory_info &memory,
    const sysprobe_filesystem_info &filesystem,
    const sysprobe_uptime_info &uptime,
    std::string &payload
)
{
    nexusops::telemetry::v1::
        SystemMetrics message;

    message.set_cpu_usage_percent(
        cpuUsagePercent
    );

    message.set_memory_used_bytes(
        memory.used_bytes
    );

    message.set_filesystem_used_bytes(
        filesystem.used_bytes
    );

    message.set_uptime_seconds(
        uptime.uptime_seconds
    );

    return message.SerializeToString(
        &payload
    );
}

bool encodeNetworkSnapshot(
    const std::vector<
        sysprobe_network_interface
    > &interfaces,
    std::string &payload
)
{
    nexusops::telemetry::v1::
        NetworkSnapshot message;

    for (
        const auto &interfaceInfo :
        interfaces
    ) {
        auto *interfaceMessage =
            message.add_interfaces();

        interfaceMessage->set_name(
            boundedString(
                interfaceInfo.name,
                SYSPROBE_NETWORK_NAME_MAX
            )
        );

        interfaceMessage->
            set_ipv4_address(
                boundedString(
                    interfaceInfo.ipv4_address,
                    SYSPROBE_IPV4_TEXT_MAX
                )
            );

        interfaceMessage->
            set_ipv6_address(
                boundedString(
                    interfaceInfo.ipv6_address,
                    SYSPROBE_IPV6_TEXT_MAX
                )
            );

        interfaceMessage->set_rx_bytes(
            interfaceInfo.rx_bytes
        );

        interfaceMessage->
            set_rx_packets(
                interfaceInfo.rx_packets
            );

        interfaceMessage->
            set_rx_errors(
                interfaceInfo.rx_errors
            );

        interfaceMessage->
            set_rx_dropped(
                interfaceInfo.rx_dropped
            );

        interfaceMessage->set_tx_bytes(
            interfaceInfo.tx_bytes
        );

        interfaceMessage->
            set_tx_packets(
                interfaceInfo.tx_packets
            );

        interfaceMessage->
            set_tx_errors(
                interfaceInfo.tx_errors
            );

        interfaceMessage->
            set_tx_dropped(
                interfaceInfo.tx_dropped
            );
    }

    return message.SerializeToString(
        &payload
    );
}

bool encodeProcessSnapshot(
    const std::vector<
        sysprobe_process_info
    > &processes,
    std::string &payload
)
{
    nexusops::telemetry::v1::
        ProcessSnapshot message;

    for (
        const auto &process :
        processes
    ) {
        auto *processMessage =
            message.add_processes();

        processMessage->set_pid(
            process.pid
        );

        processMessage->set_parent_pid(
            process.parent_pid
        );

        if (process.state != '\0') {
            processMessage->set_state(
                std::string(
                    1,
                    process.state
                )
            );
        }

        processMessage->set_name(
            boundedString(
                process.name,
                SYSPROBE_PROCESS_NAME_MAX
            )
        );

        processMessage->
            set_resident_memory_bytes(
                process.
                    resident_memory_bytes
            );
    }

    return message.SerializeToString(
        &payload
    );
}

}