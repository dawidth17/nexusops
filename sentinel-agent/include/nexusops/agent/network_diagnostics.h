#ifndef NEXUSOPS_NETWORK_DIAGNOSTICS_H
#define NEXUSOPS_NETWORK_DIAGNOSTICS_H

#include <chrono>
#include <cstdint>
#include <string>
#include <vector>

namespace nexusops::agent {

enum class DiagnosticStatus {
    ok,
    invalid_argument,
    resolution_failed,
    connect_failed,
    timeout,
    io_error,
    protocol_error,
    tls_error,
    http_unhealthy,
    certificate_not_yet_valid,
    certificate_expired
};

struct DnsCheckResult {
    DiagnosticStatus status{
        DiagnosticStatus::invalid_argument
    };

    std::chrono::milliseconds latency{0};

    std::vector<std::string> addresses;

    std::string error;
};

struct TcpCheckResult {
    DiagnosticStatus status{
        DiagnosticStatus::invalid_argument
    };

    std::chrono::milliseconds latency{0};

    std::string address;

    std::string error;
};

struct HttpCheckOptions {
    std::chrono::milliseconds timeout{
        3000
    };

    std::string caFile;
};

struct HttpCheckResult {
    DiagnosticStatus status{
        DiagnosticStatus::invalid_argument
    };

    std::chrono::milliseconds latency{0};

    int statusCode{0};

    bool tls{false};

    std::string address;

    std::string error;
};

struct TlsCheckOptions {
    std::chrono::milliseconds timeout{
        3000
    };

    std::string caFile;
};

struct TlsCertificateResult {
    DiagnosticStatus status{
        DiagnosticStatus::invalid_argument
    };

    std::chrono::milliseconds latency{0};

    std::int64_t daysToExpiry{0};

    std::string address;

    std::string subject;

    std::string issuer;

    std::string error;
};

[[nodiscard]]
const char *diagnosticStatusName(
    DiagnosticStatus status
) noexcept;

[[nodiscard]]
DnsCheckResult runDnsCheck(
    const std::string &host
);

[[nodiscard]]
TcpCheckResult runTcpCheck(
    const std::string &host,
    std::uint16_t port,
    std::chrono::milliseconds timeout
);

[[nodiscard]]
HttpCheckResult runHttpCheck(
    const std::string &url,
    const HttpCheckOptions &options = {}
);

[[nodiscard]]
TlsCertificateResult runTlsCertificateCheck(
    const std::string &host,
    std::uint16_t port,
    const TlsCheckOptions &options = {}
);

}

#endif