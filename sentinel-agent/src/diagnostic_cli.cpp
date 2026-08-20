#include "nexusops/agent/diagnostic_cli.h"

#include "nexusops/agent/network_diagnostics.h"

#include <charconv>
#include <chrono>
#include <cstdint>
#include <iostream>
#include <string>
#include <string_view>

namespace {

using nexusops::agent::DiagnosticStatus;
using nexusops::agent::HttpCheckOptions;
using nexusops::agent::TlsCheckOptions;

bool parsePort(
    std::string_view value,
    std::uint16_t &port
)
{
    unsigned int parsed = 0;

    const auto result =
        std::from_chars(
            value.data(),
            value.data() +
                value.size(),
            parsed
        );

    if (
        result.ec !=
            std::errc{} ||
        result.ptr !=
            value.data() +
                value.size() ||
        parsed == 0 ||
        parsed > 65535
    ) {
        return false;
    }

    port =
        static_cast<std::uint16_t>(
            parsed
        );

    return true;
}

int statusExitCode(
    DiagnosticStatus status
)
{
    return status ==
        DiagnosticStatus::ok
        ? 0
        : 1;
}

void printUsage()
{
    std::cerr
        << "usage:\n"
        << "  sentinel-agent diagnose dns <host>\n"
        << "  sentinel-agent diagnose tcp <host> <port>\n"
        << "  sentinel-agent diagnose http <url> [ca-file]\n"
        << "  sentinel-agent diagnose tls <host> <port> [ca-file]\n";
}

}

namespace nexusops::agent {

std::optional<int>
tryRunDiagnosticCommand(
    int argc,
    char **argv
)
{
    if (
        argc < 2 ||
        std::string_view(
            argv[1]
        ) != "diagnose"
    ) {
        return std::nullopt;
    }

    if (argc < 3) {
        printUsage();

        return 2;
    }

    const std::string_view type(
        argv[2]
    );

    if (type == "dns") {
        if (argc != 4) {
            printUsage();

            return 2;
        }

        const auto result =
            runDnsCheck(
                argv[3]
            );

        std::cout
            << "diagnostic type=dns"
            << " status="
            << diagnosticStatusName(
                result.status
            )
            << " host="
            << argv[3]
            << " latency_ms="
            << result.latency.count()
            << " addresses=";

        for (
            std::size_t index = 0;
            index <
                result.addresses.size();
            ++index
        ) {
            if (index != 0) {
                std::cout << ',';
            }

            std::cout
                << result.addresses[
                       index
                   ];
        }

        if (!result.error.empty()) {
            std::cout
                << " error="
                << result.error;
        }

        std::cout << '\n';

        return statusExitCode(
            result.status
        );
    }

    if (type == "tcp") {
        if (argc != 5) {
            printUsage();

            return 2;
        }

        std::uint16_t port = 0;

        if (
            !parsePort(
                argv[4],
                port
            )
        ) {
            std::cerr
                << "invalid tcp port\n";

            return 2;
        }

        const auto result =
            runTcpCheck(
                argv[3],
                port,
                std::chrono::
                    milliseconds(3000)
            );

        std::cout
            << "diagnostic type=tcp"
            << " status="
            << diagnosticStatusName(
                result.status
            )
            << " host="
            << argv[3]
            << " port="
            << port
            << " address="
            << (
                result.address.empty()
                    ? "-"
                    : result.address
            )
            << " latency_ms="
            << result.latency.count();

        if (!result.error.empty()) {
            std::cout
                << " error="
                << result.error;
        }

        std::cout << '\n';

        return statusExitCode(
            result.status
        );
    }

    if (type == "http") {
        if (
            argc != 4 &&
            argc != 5
        ) {
            printUsage();

            return 2;
        }

        HttpCheckOptions options;

        if (argc == 5) {
            options.caFile =
                argv[4];
        }

        const auto result =
            runHttpCheck(
                argv[3],
                options
            );

        std::cout
            << "diagnostic type=http"
            << " status="
            << diagnosticStatusName(
                result.status
            )
            << " url="
            << argv[3]
            << " tls="
            << (
                result.tls
                    ? "true"
                    : "false"
            )
            << " http_status="
            << result.statusCode
            << " address="
            << (
                result.address.empty()
                    ? "-"
                    : result.address
            )
            << " latency_ms="
            << result.latency.count();

        if (!result.error.empty()) {
            std::cout
                << " error="
                << result.error;
        }

        std::cout << '\n';

        return statusExitCode(
            result.status
        );
    }

    if (type == "tls") {
        if (
            argc != 5 &&
            argc != 6
        ) {
            printUsage();

            return 2;
        }

        std::uint16_t port = 0;

        if (
            !parsePort(
                argv[4],
                port
            )
        ) {
            std::cerr
                << "invalid tls port\n";

            return 2;
        }

        TlsCheckOptions options;

        if (argc == 6) {
            options.caFile =
                argv[5];
        }

        const auto result =
            runTlsCertificateCheck(
                argv[3],
                port,
                options
            );

        std::cout
            << "diagnostic type=tls"
            << " status="
            << diagnosticStatusName(
                result.status
            )
            << " host="
            << argv[3]
            << " port="
            << port
            << " address="
            << (
                result.address.empty()
                    ? "-"
                    : result.address
            )
            << " latency_ms="
            << result.latency.count()
            << " days_to_expiry="
            << result.daysToExpiry
            << " subject="
            << (
                result.subject.empty()
                    ? "-"
                    : result.subject
            )
            << " issuer="
            << (
                result.issuer.empty()
                    ? "-"
                    : result.issuer
            );

        if (!result.error.empty()) {
            std::cout
                << " error="
                << result.error;
        }

        std::cout << '\n';

        return statusExitCode(
            result.status
        );
    }

    printUsage();

    return 2;
}

}