#include "nexusops/agent/network_diagnostics.h"

#include <gtest/gtest.h>

#include <arpa/inet.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <unistd.h>

#include <chrono>
#include <cstdint>
#include <stdexcept>
#include <string>
#include <thread>

using namespace std::chrono_literals;

namespace {

using nexusops::agent::DiagnosticStatus;
using nexusops::agent::runDnsCheck;
using nexusops::agent::runHttpCheck;
using nexusops::agent::runTcpCheck;

class LocalServer {
public:
    explicit LocalServer(
        std::string response
    )
        : response_(
              std::move(response)
          )
    {
        listenSocket_ =
            socket(
                AF_INET,
                SOCK_STREAM |
                    SOCK_CLOEXEC,
                0
            );

        if (listenSocket_ < 0) {
            throw std::runtime_error(
                "failed to create local test socket"
            );
        }

        int reuse = 1;

        setsockopt(
            listenSocket_,
            SOL_SOCKET,
            SO_REUSEADDR,
            &reuse,
            sizeof(reuse)
        );

        sockaddr_in address{};

        address.sin_family =
            AF_INET;

        address.sin_addr.s_addr =
            htonl(
                INADDR_LOOPBACK
            );

        address.sin_port = 0;

        if (
            bind(
                listenSocket_,
                reinterpret_cast<
                    sockaddr *
                >(&address),
                sizeof(address)
            ) != 0
        ) {
            close(
                listenSocket_
            );

            throw std::runtime_error(
                "failed to bind local test socket"
            );
        }

        socklen_t addressLength =
            sizeof(address);

        if (
            getsockname(
                listenSocket_,
                reinterpret_cast<
                    sockaddr *
                >(&address),
                &addressLength
            ) != 0
        ) {
            close(
                listenSocket_
            );

            throw std::runtime_error(
                "failed to read local test port"
            );
        }

        port_ =
            ntohs(
                address.sin_port
            );

        if (
            listen(
                listenSocket_,
                1
            ) != 0
        ) {
            close(
                listenSocket_
            );

            throw std::runtime_error(
                "failed to listen on local test socket"
            );
        }

        thread_ =
            std::thread(
                [this]() {
                    serveOne();
                }
            );
    }

    ~LocalServer()
    {
        if (thread_.joinable()) {
            thread_.join();
        }

        if (listenSocket_ >= 0) {
            close(
                listenSocket_
            );
        }
    }

    LocalServer(
        const LocalServer &
    ) = delete;

    LocalServer &operator=(
        const LocalServer &
    ) = delete;

    [[nodiscard]]
    std::uint16_t port() const noexcept
    {
        return port_;
    }

private:
    void serveOne()
    {
        const int client =
            accept4(
                listenSocket_,
                nullptr,
                nullptr,
                SOCK_CLOEXEC
            );

        if (client < 0) {
            return;
        }

        if (!response_.empty()) {
            char buffer[4096];

            recv(
                client,
                buffer,
                sizeof(buffer),
                0
            );

            std::size_t offset = 0;

            while (
                offset <
                response_.size()
            ) {
                const ssize_t written =
                    send(
                        client,
                        response_.data() +
                            offset,
                        response_.size() -
                            offset,
                        MSG_NOSIGNAL
                    );

                if (written <= 0) {
                    break;
                }

                offset +=
                    static_cast<
                        std::size_t
                    >(written);
            }
        }

        shutdown(
            client,
            SHUT_RDWR
        );

        close(
            client
        );
    }

    int listenSocket_{-1};

    std::uint16_t port_{0};

    std::string response_;

    std::thread thread_;
};

TEST(
    NetworkDiagnosticsTests,
    resolvesLocalhost
)
{
    const auto result =
        runDnsCheck(
            "localhost"
        );

    EXPECT_EQ(
        result.status,
        DiagnosticStatus::ok
    );

    EXPECT_FALSE(
        result.addresses.empty()
    );
}

TEST(
    NetworkDiagnosticsTests,
    rejectsEmptyDnsHost
)
{
    const auto result =
        runDnsCheck(
            ""
        );

    EXPECT_EQ(
        result.status,
        DiagnosticStatus::
            invalid_argument
    );
}

TEST(
    NetworkDiagnosticsTests,
    connectsToLocalTcpServer
)
{
    LocalServer server(
        ""
    );

    const auto result =
        runTcpCheck(
            "127.0.0.1",
            server.port(),
            1000ms
        );

    EXPECT_EQ(
        result.status,
        DiagnosticStatus::ok
    );

    EXPECT_FALSE(
        result.address.empty()
    );
}

TEST(
    NetworkDiagnosticsTests,
    rejectsInvalidTcpArguments
)
{
    const auto result =
        runTcpCheck(
            "",
            0,
            1000ms
        );

    EXPECT_EQ(
        result.status,
        DiagnosticStatus::
            invalid_argument
    );
}

TEST(
    NetworkDiagnosticsTests,
    acceptsHealthyHttpResponse
)
{
    LocalServer server(
        "HTTP/1.1 204 No Content\r\n"
        "Content-Length: 0\r\n"
        "Connection: close\r\n"
        "\r\n"
    );

    const auto result =
        runHttpCheck(
            "http://127.0.0.1:" +
            std::to_string(
                server.port()
            ) +
            "/health"
        );

    EXPECT_EQ(
        result.status,
        DiagnosticStatus::ok
    );

    EXPECT_EQ(
        result.statusCode,
        204
    );

    EXPECT_FALSE(
        result.tls
    );
}

TEST(
    NetworkDiagnosticsTests,
    reportsUnhealthyHttpStatus
)
{
    LocalServer server(
        "HTTP/1.1 503 Service Unavailable\r\n"
        "Content-Length: 0\r\n"
        "Connection: close\r\n"
        "\r\n"
    );

    const auto result =
        runHttpCheck(
            "http://127.0.0.1:" +
            std::to_string(
                server.port()
            ) +
            "/health"
        );

    EXPECT_EQ(
        result.status,
        DiagnosticStatus::
            http_unhealthy
    );

    EXPECT_EQ(
        result.statusCode,
        503
    );
}

TEST(
    NetworkDiagnosticsTests,
    rejectsMalformedHttpUrl
)
{
    const auto result =
        runHttpCheck(
            "ftp://localhost/test"
        );

    EXPECT_EQ(
        result.status,
        DiagnosticStatus::
            invalid_argument
    );
}

}