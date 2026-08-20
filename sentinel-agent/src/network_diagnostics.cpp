#include "nexusops/agent/network_diagnostics.h"

#include <arpa/inet.h>
#include <fcntl.h>
#include <netdb.h>
#include <poll.h>
#include <sys/socket.h>
#include <unistd.h>

#include <openssl/asn1.h>
#include <openssl/bio.h>
#include <openssl/err.h>
#include <openssl/ssl.h>
#include <openssl/x509.h>
#include <openssl/x509_vfy.h>

#include <algorithm>
#include <cerrno>
#include <charconv>
#include <chrono>
#include <climits>
#include <cstring>
#include <memory>
#include <optional>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

namespace {

using nexusops::agent::DiagnosticStatus;
using nexusops::agent::HttpCheckOptions;
using nexusops::agent::TlsCheckOptions;

using Clock =
    std::chrono::steady_clock;

class UniqueFd {
public:
    UniqueFd() = default;

    explicit UniqueFd(
        int fd
    )
        : fd_(fd)
    {
    }

    ~UniqueFd()
    {
        reset();
    }

    UniqueFd(
        const UniqueFd &
    ) = delete;

    UniqueFd &operator=(
        const UniqueFd &
    ) = delete;

    UniqueFd(
        UniqueFd &&other
    ) noexcept
        : fd_(other.release())
    {
    }

    UniqueFd &operator=(
        UniqueFd &&other
    ) noexcept
    {
        if (this != &other) {
            reset(
                other.release()
            );
        }

        return *this;
    }

    [[nodiscard]]
    int get() const noexcept
    {
        return fd_;
    }

    [[nodiscard]]
    bool valid() const noexcept
    {
        return fd_ >= 0;
    }

    int release() noexcept
    {
        const int result =
            fd_;

        fd_ = -1;

        return result;
    }

    void reset(
        int fd = -1
    ) noexcept
    {
        if (fd_ >= 0) {
            close(fd_);
        }

        fd_ = fd;
    }

private:
    int fd_{-1};
};

struct SslContextDeleter {
    void operator()(
        SSL_CTX *context
    ) const noexcept
    {
        if (context != nullptr) {
            SSL_CTX_free(
                context
            );
        }
    }
};

struct SslDeleter {
    void operator()(
        SSL *ssl
    ) const noexcept
    {
        if (ssl != nullptr) {
            SSL_free(
                ssl
            );
        }
    }
};

struct BioDeleter {
    void operator()(
        BIO *bio
    ) const noexcept
    {
        if (bio != nullptr) {
            BIO_free(
                bio
            );
        }
    }
};

struct X509Deleter {
    void operator()(
        X509 *certificate
    ) const noexcept
    {
        if (certificate != nullptr) {
            X509_free(
                certificate
            );
        }
    }
};

using SslContextPtr =
    std::unique_ptr<
        SSL_CTX,
        SslContextDeleter
    >;

using SslPtr =
    std::unique_ptr<
        SSL,
        SslDeleter
    >;

using BioPtr =
    std::unique_ptr<
        BIO,
        BioDeleter
    >;

using X509Ptr =
    std::unique_ptr<
        X509,
        X509Deleter
    >;

struct ConnectedSocket {
    DiagnosticStatus status{
        DiagnosticStatus::connect_failed
    };

    UniqueFd socket;

    std::chrono::milliseconds latency{0};

    std::string address;

    std::string error;
};

struct TlsConnection {
    DiagnosticStatus status{
        DiagnosticStatus::tls_error
    };

    UniqueFd socket;

    SslContextPtr context;

    SslPtr ssl;

    std::chrono::milliseconds latency{0};

    std::string address;

    std::string error;
};

struct ParsedUrl {
    bool tls{false};

    std::string host;

    std::uint16_t port{0};

    std::string target;

    bool defaultPort{true};
};

std::chrono::milliseconds elapsedSince(
    Clock::time_point start
)
{
    return std::chrono::duration_cast<
        std::chrono::milliseconds
    >(
        Clock::now() - start
    );
}

int pollTimeoutUntil(
    Clock::time_point deadline
)
{
    const auto now =
        Clock::now();

    if (now >= deadline) {
        return 0;
    }

    auto remaining =
        std::chrono::duration_cast<
            std::chrono::milliseconds
        >(
            deadline - now
        );

    if (
        remaining <=
        std::chrono::milliseconds::zero()
    ) {
        return 1;
    }

    if (
        remaining.count() >
        INT_MAX
    ) {
        return INT_MAX;
    }

    return std::max(
        1,
        static_cast<int>(
            remaining.count()
        )
    );
}

bool setSocketTimeout(
    int socket,
    std::chrono::milliseconds timeout
)
{
    if (
        timeout <=
        std::chrono::milliseconds::zero()
    ) {
        return false;
    }

    timeval value{};

    value.tv_sec =
        static_cast<time_t>(
            timeout.count() / 1000
        );

    value.tv_usec =
        static_cast<suseconds_t>(
            (
                timeout.count() %
                1000
            ) *
            1000
        );

    if (
        value.tv_sec == 0 &&
        value.tv_usec == 0
    ) {
        value.tv_usec =
            1000;
    }

    return
        setsockopt(
            socket,
            SOL_SOCKET,
            SO_RCVTIMEO,
            &value,
            sizeof(value)
        ) == 0 &&
        setsockopt(
            socket,
            SOL_SOCKET,
            SO_SNDTIMEO,
            &value,
            sizeof(value)
        ) == 0;
}

std::string numericAddress(
    const sockaddr *address,
    socklen_t length
)
{
    char buffer[
        NI_MAXHOST
    ]{};

    if (
        getnameinfo(
            address,
            length,
            buffer,
            sizeof(buffer),
            nullptr,
            0,
            NI_NUMERICHOST
        ) != 0
    ) {
        return {};
    }

    return buffer;
}

ConnectedSocket connectSocket(
    const std::string &host,
    std::uint16_t port,
    std::chrono::milliseconds timeout
)
{
    ConnectedSocket result;

    if (
        host.empty() ||
        port == 0 ||
        timeout <=
            std::chrono::milliseconds::zero()
    ) {
        result.status =
            DiagnosticStatus::
                invalid_argument;

        result.error =
            "host, port and timeout must be valid";

        return result;
    }

    const auto start =
        Clock::now();

    const auto deadline =
        start + timeout;

    addrinfo hints{};

    hints.ai_family =
        AF_UNSPEC;

    hints.ai_socktype =
        SOCK_STREAM;

    hints.ai_protocol =
        IPPROTO_TCP;

    addrinfo *rawAddresses =
        nullptr;

    const std::string service =
        std::to_string(
            port
        );

    const int resolveResult =
        getaddrinfo(
            host.c_str(),
            service.c_str(),
            &hints,
            &rawAddresses
        );

    std::unique_ptr<
        addrinfo,
        decltype(&freeaddrinfo)
    > addresses(
        rawAddresses,
        freeaddrinfo
    );

    if (
        resolveResult != 0 ||
        rawAddresses == nullptr
    ) {
        result.status =
            DiagnosticStatus::
                resolution_failed;

        result.error =
            gai_strerror(
                resolveResult
            );

        result.latency =
            elapsedSince(start);

        return result;
    }

    if (
        Clock::now() >=
        deadline
    ) {
        result.status =
            DiagnosticStatus::timeout;

        result.error =
            "resolution exceeded timeout";

        result.latency =
            elapsedSince(start);

        return result;
    }

    int lastSocketError = 0;
    bool observedTimeout = false;

    for (
        addrinfo *current =
            rawAddresses;
        current != nullptr;
        current =
            current->ai_next
    ) {
        if (
            Clock::now() >=
            deadline
        ) {
            observedTimeout =
                true;

            break;
        }

        UniqueFd socket(
            ::socket(
                current->ai_family,
                current->ai_socktype |
                    SOCK_CLOEXEC,
                current->ai_protocol
            )
        );

        if (!socket.valid()) {
            lastSocketError =
                errno;

            continue;
        }

        const int originalFlags =
            fcntl(
                socket.get(),
                F_GETFL,
                0
            );

        if (originalFlags < 0) {
            lastSocketError =
                errno;

            continue;
        }

        if (
            fcntl(
                socket.get(),
                F_SETFL,
                originalFlags |
                    O_NONBLOCK
            ) < 0
        ) {
            lastSocketError =
                errno;

            continue;
        }

        int connectResult =
            connect(
                socket.get(),
                current->ai_addr,
                current->ai_addrlen
            );

        if (
            connectResult != 0 &&
            errno != EINPROGRESS
        ) {
            lastSocketError =
                errno;

            continue;
        }

        if (connectResult != 0) {
            pollfd descriptor{};

            descriptor.fd =
                socket.get();

            descriptor.events =
                POLLOUT;

            for (;;) {
                const int timeoutMs =
                    pollTimeoutUntil(
                        deadline
                    );

                if (timeoutMs == 0) {
                    observedTimeout =
                        true;

                    break;
                }

                const int pollResult =
                    poll(
                        &descriptor,
                        1,
                        timeoutMs
                    );

                if (pollResult == 0) {
                    observedTimeout =
                        true;

                    break;
                }

                if (pollResult < 0) {
                    if (errno == EINTR) {
                        continue;
                    }

                    lastSocketError =
                        errno;

                    break;
                }

                int socketError = 0;

                socklen_t errorLength =
                    sizeof(socketError);

                if (
                    getsockopt(
                        socket.get(),
                        SOL_SOCKET,
                        SO_ERROR,
                        &socketError,
                        &errorLength
                    ) != 0
                ) {
                    lastSocketError =
                        errno;

                    break;
                }

                if (socketError != 0) {
                    lastSocketError =
                        socketError;

                    break;
                }

                connectResult =
                    0;

                break;
            }
        }

        if (connectResult != 0) {
            continue;
        }

        if (
            fcntl(
                socket.get(),
                F_SETFL,
                originalFlags
            ) < 0
        ) {
            lastSocketError =
                errno;

            continue;
        }

        if (
            !setSocketTimeout(
                socket.get(),
                timeout
            )
        ) {
            lastSocketError =
                errno;

            continue;
        }

        result.status =
            DiagnosticStatus::ok;

        result.socket =
            std::move(socket);

        result.address =
            numericAddress(
                current->ai_addr,
                static_cast<socklen_t>(
                    current->ai_addrlen
                )
            );

        result.latency =
            elapsedSince(start);

        return result;
    }

    result.latency =
        elapsedSince(start);

    if (
        observedTimeout ||
        Clock::now() >= deadline
    ) {
        result.status =
            DiagnosticStatus::timeout;

        result.error =
            "connection timed out";

        return result;
    }

    result.status =
        DiagnosticStatus::
            connect_failed;

    if (lastSocketError != 0) {
        result.error =
            std::strerror(
                lastSocketError
            );
    } else {
        result.error =
            "unable to connect to target";
    }

    return result;
}

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

bool hasUnsafeUrlCharacter(
    const std::string &value
)
{
    return std::any_of(
        value.begin(),
        value.end(),
        [](unsigned char character) {
            return
                character <= 0x20 ||
                character == 0x7f;
        }
    );
}

std::optional<ParsedUrl> parseUrl(
    const std::string &url
)
{
    constexpr std::string_view
        httpPrefix =
            "http://";

    constexpr std::string_view
        httpsPrefix =
            "https://";

    ParsedUrl result;

    std::string_view remaining;

    if (
        url.starts_with(
            httpPrefix
        )
    ) {
        result.tls = false;

        result.port = 80;

        remaining =
            std::string_view(url).
                substr(
                    httpPrefix.size()
                );
    } else if (
        url.starts_with(
            httpsPrefix
        )
    ) {
        result.tls = true;

        result.port = 443;

        remaining =
            std::string_view(url).
                substr(
                    httpsPrefix.size()
                );
    } else {
        return std::nullopt;
    }

    if (remaining.empty()) {
        return std::nullopt;
    }

    const auto targetStart =
        remaining.find_first_of(
            "/?#"
        );

    std::string_view authority =
        targetStart ==
            std::string_view::npos
        ? remaining
        : remaining.substr(
              0,
              targetStart
          );

    if (
        authority.empty() ||
        authority.find('@') !=
            std::string_view::npos
    ) {
        return std::nullopt;
    }

    if (
        targetStart ==
        std::string_view::npos
    ) {
        result.target = "/";
    } else {
        const char firstTarget =
            remaining[targetStart];

        if (firstTarget == '#') {
            return std::nullopt;
        }

        if (firstTarget == '?') {
            result.target =
                "/" +
                std::string(
                    remaining.substr(
                        targetStart
                    )
                );
        } else {
            result.target =
                std::string(
                    remaining.substr(
                        targetStart
                    )
                );
        }

        if (
            result.target.find('#') !=
            std::string::npos
        ) {
            return std::nullopt;
        }
    }

    if (
        authority.front() ==
        '['
    ) {
        const auto closingBracket =
            authority.find(']');

        if (
            closingBracket ==
            std::string_view::npos
        ) {
            return std::nullopt;
        }

        result.host =
            std::string(
                authority.substr(
                    1,
                    closingBracket - 1
                )
            );

        const auto suffix =
            authority.substr(
                closingBracket + 1
            );

        if (!suffix.empty()) {
            if (
                suffix.front() != ':' ||
                suffix.size() == 1
            ) {
                return std::nullopt;
            }

            if (
                !parsePort(
                    suffix.substr(1),
                    result.port
                )
            ) {
                return std::nullopt;
            }

            result.defaultPort =
                false;
        }
    } else {
        const auto firstColon =
            authority.find(':');

        const auto lastColon =
            authority.rfind(':');

        if (
            firstColon !=
                std::string_view::npos &&
            firstColon != lastColon
        ) {
            return std::nullopt;
        }

        if (
            lastColon !=
            std::string_view::npos
        ) {
            result.host =
                std::string(
                    authority.substr(
                        0,
                        lastColon
                    )
                );

            if (
                !parsePort(
                    authority.substr(
                        lastColon + 1
                    ),
                    result.port
                )
            ) {
                return std::nullopt;
            }

            result.defaultPort =
                false;
        } else {
            result.host =
                std::string(
                    authority
                );
        }
    }

    if (
        result.host.empty() ||
        result.target.empty() ||
        hasUnsafeUrlCharacter(
            result.host
        ) ||
        hasUnsafeUrlCharacter(
            result.target
        )
    ) {
        return std::nullopt;
    }

    return result;
}

std::string buildHttpRequest(
    const ParsedUrl &url
)
{
    std::string hostHeader =
        url.host;

    if (
        url.host.find(':') !=
        std::string::npos
    ) {
        hostHeader =
            "[" +
            url.host +
            "]";
    }

    if (!url.defaultPort) {
        hostHeader +=
            ":" +
            std::to_string(
                url.port
            );
    }

    return
        "GET " +
        url.target +
        " HTTP/1.1\r\n"
        "Host: " +
        hostHeader +
        "\r\n"
        "User-Agent: NexusOps-Sentinel/0.7\r\n"
        "Accept: */*\r\n"
        "Connection: close\r\n"
        "\r\n";
}

bool sendPlain(
    int socket,
    const std::string &request,
    std::string &error
)
{
    std::size_t offset = 0;

    while (
        offset <
        request.size()
    ) {
        const ssize_t written =
            send(
                socket,
                request.data() +
                    offset,
                request.size() -
                    offset,
                MSG_NOSIGNAL
            );

        if (written < 0) {
            if (errno == EINTR) {
                continue;
            }

            error =
                std::strerror(
                    errno
                );

            return false;
        }

        if (written == 0) {
            error =
                "socket closed while sending";

            return false;
        }

        offset +=
            static_cast<std::size_t>(
                written
            );
    }

    return true;
}

bool readPlainHeaders(
    int socket,
    std::string &headers,
    DiagnosticStatus &status,
    std::string &error
)
{
    constexpr std::size_t
        maxHeaderBytes =
            64U * 1024U;

    char buffer[4096];

    while (
        headers.find(
            "\r\n\r\n"
        ) == std::string::npos
    ) {
        const ssize_t readCount =
            recv(
                socket,
                buffer,
                sizeof(buffer),
                0
            );

        if (readCount < 0) {
            if (errno == EINTR) {
                continue;
            }

            if (
                errno == EAGAIN ||
                errno == EWOULDBLOCK
            ) {
                status =
                    DiagnosticStatus::
                        timeout;

                error =
                    "http read timed out";
            } else {
                status =
                    DiagnosticStatus::
                        io_error;

                error =
                    std::strerror(
                        errno
                    );
            }

            return false;
        }

        if (readCount == 0) {
            status =
                DiagnosticStatus::
                    protocol_error;

            error =
                "connection closed before http headers completed";

            return false;
        }

        headers.append(
            buffer,
            static_cast<std::size_t>(
                readCount
            )
        );

        if (
            headers.size() >
            maxHeaderBytes
        ) {
            status =
                DiagnosticStatus::
                    protocol_error;

            error =
                "http headers exceeded size limit";

            return false;
        }
    }

    return true;
}

bool parseHttpStatusCode(
    const std::string &headers,
    int &statusCode
)
{
    const auto lineEnd =
        headers.find(
            "\r\n"
        );

    if (
        lineEnd ==
        std::string::npos
    ) {
        return false;
    }

    const std::string_view line(
        headers.data(),
        lineEnd
    );

    if (
        !line.starts_with(
            "HTTP/"
        )
    ) {
        return false;
    }

    const auto firstSpace =
        line.find(' ');

    if (
        firstSpace ==
        std::string_view::npos
    ) {
        return false;
    }

    const auto codeStart =
        line.find_first_not_of(
            ' ',
            firstSpace
        );

    if (
        codeStart ==
        std::string_view::npos ||
        codeStart + 3 >
            line.size()
    ) {
        return false;
    }

    int parsed = 0;

    const auto result =
        std::from_chars(
            line.data() +
                codeStart,
            line.data() +
                codeStart + 3,
            parsed
        );

    if (
        result.ec !=
            std::errc{} ||
        parsed < 100 ||
        parsed > 599
    ) {
        return false;
    }

    statusCode =
        parsed;

    return true;
}

std::string lastOpenSslError()
{
    const unsigned long code =
        ERR_get_error();

    if (code == 0) {
        return
            "unknown openssl error";
    }

    char buffer[256]{};

    ERR_error_string_n(
        code,
        buffer,
        sizeof(buffer)
    );

    return buffer;
}

TlsConnection connectTls(
    const std::string &host,
    std::uint16_t port,
    const TlsCheckOptions &options,
    bool ignoreCertificateTime
)
{
    TlsConnection result;

    const auto start =
        Clock::now();

    ConnectedSocket connected =
        connectSocket(
            host,
            port,
            options.timeout
        );

    if (
        connected.status !=
        DiagnosticStatus::ok
    ) {
        result.status =
            connected.status;

        result.latency =
            connected.latency;

        result.address =
            std::move(
                connected.address
            );

        result.error =
            std::move(
                connected.error
            );

        return result;
    }

    result.socket =
        std::move(
            connected.socket
        );

    result.address =
        std::move(
            connected.address
        );

    ERR_clear_error();

    result.context.reset(
        SSL_CTX_new(
            TLS_client_method()
        )
    );

    if (!result.context) {
        result.error =
            lastOpenSslError();

        return result;
    }

    if (
        SSL_CTX_set_min_proto_version(
            result.context.get(),
            TLS1_2_VERSION
        ) != 1
    ) {
        result.error =
            lastOpenSslError();

        return result;
    }

    SSL_CTX_set_verify(
        result.context.get(),
        SSL_VERIFY_PEER,
        nullptr
    );

    if (
        options.caFile.empty()
    ) {
        if (
            SSL_CTX_set_default_verify_paths(
                result.context.get()
            ) != 1
        ) {
            result.error =
                lastOpenSslError();

            return result;
        }
    } else {
        if (
            SSL_CTX_load_verify_locations(
                result.context.get(),
                options.caFile.c_str(),
                nullptr
            ) != 1
        ) {
            result.error =
                lastOpenSslError();

            return result;
        }
    }

    if (ignoreCertificateTime) {
        X509_VERIFY_PARAM *parameters =
            SSL_CTX_get0_param(
                result.context.get()
            );

        if (
            parameters == nullptr ||
            X509_VERIFY_PARAM_set_flags(
                parameters,
                X509_V_FLAG_NO_CHECK_TIME
            ) != 1
        ) {
            result.error =
                lastOpenSslError();

            return result;
        }
    }

    result.ssl.reset(
        SSL_new(
            result.context.get()
        )
    );

    if (!result.ssl) {
        result.error =
            lastOpenSslError();

        return result;
    }

    if (
        SSL_set_fd(
            result.ssl.get(),
            result.socket.get()
        ) != 1
    ) {
        result.error =
            lastOpenSslError();

        return result;
    }

    if (
        SSL_set_tlsext_host_name(
            result.ssl.get(),
            host.c_str()
        ) != 1
    ) {
        result.error =
            lastOpenSslError();

        return result;
    }

    if (
        SSL_set1_host(
            result.ssl.get(),
            host.c_str()
        ) != 1
    ) {
        result.error =
            lastOpenSslError();

        return result;
    }

    if (
        SSL_connect(
            result.ssl.get()
        ) != 1
    ) {
        const long verifyResult =
            SSL_get_verify_result(
                result.ssl.get()
            );

        if (
            verifyResult !=
            X509_V_OK
        ) {
            result.error =
                X509_verify_cert_error_string(
                    verifyResult
                );
        } else {
            result.error =
                lastOpenSslError();
        }

        result.latency =
            elapsedSince(start);

        return result;
    }

    const long verifyResult =
        SSL_get_verify_result(
            result.ssl.get()
        );

    if (
        verifyResult !=
        X509_V_OK
    ) {
        result.error =
            X509_verify_cert_error_string(
                verifyResult
            );

        result.latency =
            elapsedSince(start);

        return result;
    }

    result.status =
        DiagnosticStatus::ok;

    result.latency =
        elapsedSince(start);

    return result;
}

bool sendTls(
    SSL *ssl,
    const std::string &request,
    std::string &error
)
{
    std::size_t offset = 0;

    while (
        offset <
        request.size()
    ) {
        const int remaining =
            static_cast<int>(
                std::min<std::size_t>(
                    request.size() -
                        offset,
                    static_cast<std::size_t>(
                        INT_MAX
                    )
                )
            );

        const int written =
            SSL_write(
                ssl,
                request.data() +
                    offset,
                remaining
            );

        if (written <= 0) {
            error =
                lastOpenSslError();

            return false;
        }

        offset +=
            static_cast<std::size_t>(
                written
            );
    }

    return true;
}

bool readTlsHeaders(
    SSL *ssl,
    std::string &headers,
    DiagnosticStatus &status,
    std::string &error
)
{
    constexpr std::size_t
        maxHeaderBytes =
            64U * 1024U;

    char buffer[4096];

    while (
        headers.find(
            "\r\n\r\n"
        ) == std::string::npos
    ) {
        const int readCount =
            SSL_read(
                ssl,
                buffer,
                sizeof(buffer)
            );

        if (readCount <= 0) {
            const int sslError =
                SSL_get_error(
                    ssl,
                    readCount
                );

            if (
                sslError ==
                    SSL_ERROR_WANT_READ ||
                sslError ==
                    SSL_ERROR_WANT_WRITE
            ) {
                status =
                    DiagnosticStatus::
                        timeout;

                error =
                    "https read timed out";
            } else if (
                sslError ==
                SSL_ERROR_ZERO_RETURN
            ) {
                status =
                    DiagnosticStatus::
                        protocol_error;

                error =
                    "tls connection closed before http headers completed";
            } else {
                status =
                    DiagnosticStatus::
                        tls_error;

                error =
                    lastOpenSslError();
            }

            return false;
        }

        headers.append(
            buffer,
            static_cast<std::size_t>(
                readCount
            )
        );

        if (
            headers.size() >
            maxHeaderBytes
        ) {
            status =
                DiagnosticStatus::
                    protocol_error;

            error =
                "http headers exceeded size limit";

            return false;
        }
    }

    return true;
}

std::string x509NameToString(
    X509_NAME *name
)
{
    if (name == nullptr) {
        return {};
    }

    BioPtr bio(
        BIO_new(
            BIO_s_mem()
        )
    );

    if (!bio) {
        return {};
    }

    if (
        X509_NAME_print_ex(
            bio.get(),
            name,
            0,
            XN_FLAG_RFC2253
        ) < 0
    ) {
        return {};
    }

    char *data = nullptr;

    const long length =
        BIO_get_mem_data(
            bio.get(),
            &data
        );

    if (
        length <= 0 ||
        data == nullptr
    ) {
        return {};
    }

    return std::string(
        data,
        static_cast<std::size_t>(
            length
        )
    );
}

}

namespace nexusops::agent {

const char *diagnosticStatusName(
    DiagnosticStatus status
) noexcept
{
    switch (status) {
        case DiagnosticStatus::ok:
            return "ok";

        case DiagnosticStatus::
            invalid_argument:
            return "invalid_argument";

        case DiagnosticStatus::
            resolution_failed:
            return "resolution_failed";

        case DiagnosticStatus::
            connect_failed:
            return "connect_failed";

        case DiagnosticStatus::timeout:
            return "timeout";

        case DiagnosticStatus::io_error:
            return "io_error";

        case DiagnosticStatus::
            protocol_error:
            return "protocol_error";

        case DiagnosticStatus::tls_error:
            return "tls_error";

        case DiagnosticStatus::
            http_unhealthy:
            return "http_unhealthy";

        case DiagnosticStatus::
            certificate_not_yet_valid:
            return "certificate_not_yet_valid";

        case DiagnosticStatus::
            certificate_expired:
            return "certificate_expired";
    }

    return "unknown";
}

DnsCheckResult runDnsCheck(
    const std::string &host
)
{
    DnsCheckResult result;

    if (host.empty()) {
        result.status =
            DiagnosticStatus::
                invalid_argument;

        result.error =
            "host must not be empty";

        return result;
    }

    const auto start =
        Clock::now();

    addrinfo hints{};

    hints.ai_family =
        AF_UNSPEC;

    hints.ai_socktype =
        SOCK_STREAM;

    addrinfo *rawAddresses =
        nullptr;

    const int resolveResult =
        getaddrinfo(
            host.c_str(),
            nullptr,
            &hints,
            &rawAddresses
        );

    std::unique_ptr<
        addrinfo,
        decltype(&freeaddrinfo)
    > addresses(
        rawAddresses,
        freeaddrinfo
    );

    result.latency =
        elapsedSince(start);

    if (
        resolveResult != 0 ||
        rawAddresses == nullptr
    ) {
        result.status =
            DiagnosticStatus::
                resolution_failed;

        result.error =
            gai_strerror(
                resolveResult
            );

        return result;
    }

    for (
        addrinfo *current =
            rawAddresses;
        current != nullptr;
        current =
            current->ai_next
    ) {
        const std::string address =
            numericAddress(
                current->ai_addr,
                static_cast<socklen_t>(
                    current->ai_addrlen
                )
            );

        if (address.empty()) {
            continue;
        }

        if (
            std::find(
                result.addresses.begin(),
                result.addresses.end(),
                address
            ) ==
            result.addresses.end()
        ) {
            result.addresses.push_back(
                address
            );
        }
    }

    if (result.addresses.empty()) {
        result.status =
            DiagnosticStatus::
                resolution_failed;

        result.error =
            "resolver returned no usable addresses";

        return result;
    }

    result.status =
        DiagnosticStatus::ok;

    return result;
}

TcpCheckResult runTcpCheck(
    const std::string &host,
    std::uint16_t port,
    std::chrono::milliseconds timeout
)
{
    TcpCheckResult result;

    ConnectedSocket connected =
        connectSocket(
            host,
            port,
            timeout
        );

    result.status =
        connected.status;

    result.latency =
        connected.latency;

    result.address =
        std::move(
            connected.address
        );

    result.error =
        std::move(
            connected.error
        );

    return result;
}

HttpCheckResult runHttpCheck(
    const std::string &url,
    const HttpCheckOptions &options
)
{
    HttpCheckResult result;

    const auto parsed =
        parseUrl(
            url
        );

    if (
        !parsed.has_value() ||
        options.timeout <=
            std::chrono::milliseconds::zero()
    ) {
        result.status =
            DiagnosticStatus::
                invalid_argument;

        result.error =
            "invalid http or https url";

        return result;
    }

    const auto start =
        Clock::now();

    result.tls =
        parsed->tls;

    const std::string request =
        buildHttpRequest(
            parsed.value()
        );

    std::string headers;

    if (!parsed->tls) {
        ConnectedSocket connected =
            connectSocket(
                parsed->host,
                parsed->port,
                options.timeout
            );

        if (
            connected.status !=
            DiagnosticStatus::ok
        ) {
            result.status =
                connected.status;

            result.latency =
                connected.latency;

            result.address =
                std::move(
                    connected.address
                );

            result.error =
                std::move(
                    connected.error
                );

            return result;
        }

        result.address =
            connected.address;

        if (
            !sendPlain(
                connected.socket.get(),
                request,
                result.error
            )
        ) {
            result.status =
                DiagnosticStatus::
                    io_error;

            result.latency =
                elapsedSince(start);

            return result;
        }

        if (
            !readPlainHeaders(
                connected.socket.get(),
                headers,
                result.status,
                result.error
            )
        ) {
            result.latency =
                elapsedSince(start);

            return result;
        }
    } else {
        TlsCheckOptions tlsOptions{
            options.timeout,
            options.caFile
        };

        TlsConnection connection =
            connectTls(
                parsed->host,
                parsed->port,
                tlsOptions,
                false
            );

        if (
            connection.status !=
            DiagnosticStatus::ok
        ) {
            result.status =
                connection.status;

            result.latency =
                connection.latency;

            result.address =
                std::move(
                    connection.address
                );

            result.error =
                std::move(
                    connection.error
                );

            return result;
        }

        result.address =
            connection.address;

        if (
            !sendTls(
                connection.ssl.get(),
                request,
                result.error
            )
        ) {
            result.status =
                DiagnosticStatus::
                    tls_error;

            result.latency =
                elapsedSince(start);

            return result;
        }

        if (
            !readTlsHeaders(
                connection.ssl.get(),
                headers,
                result.status,
                result.error
            )
        ) {
            result.latency =
                elapsedSince(start);

            return result;
        }
    }

    if (
        !parseHttpStatusCode(
            headers,
            result.statusCode
        )
    ) {
        result.status =
            DiagnosticStatus::
                protocol_error;

        result.error =
            "invalid http status line";

        result.latency =
            elapsedSince(start);

        return result;
    }

    result.latency =
        elapsedSince(start);

    if (
        result.statusCode >= 200 &&
        result.statusCode < 400
    ) {
        result.status =
            DiagnosticStatus::ok;
    } else {
        result.status =
            DiagnosticStatus::
                http_unhealthy;

        result.error =
            "http status is outside healthy range";
    }

    return result;
}

TlsCertificateResult
runTlsCertificateCheck(
    const std::string &host,
    std::uint16_t port,
    const TlsCheckOptions &options
)
{
    TlsCertificateResult result;

    if (
        host.empty() ||
        port == 0 ||
        options.timeout <=
            std::chrono::milliseconds::zero()
    ) {
        result.status =
            DiagnosticStatus::
                invalid_argument;

        result.error =
            "host, port and timeout must be valid";

        return result;
    }

    TlsConnection connection =
        connectTls(
            host,
            port,
            options,
            true
        );

    result.latency =
        connection.latency;

    result.address =
        connection.address;

    if (
        connection.status !=
        DiagnosticStatus::ok
    ) {
        result.status =
            connection.status;

        result.error =
            std::move(
                connection.error
            );

        return result;
    }

    X509Ptr certificate(
        SSL_get1_peer_certificate(
            connection.ssl.get()
        )
    );

    if (!certificate) {
        result.status =
            DiagnosticStatus::
                tls_error;

        result.error =
            "server did not provide a certificate";

        return result;
    }

    result.subject =
        x509NameToString(
            X509_get_subject_name(
                certificate.get()
            )
        );

    result.issuer =
        x509NameToString(
            X509_get_issuer_name(
                certificate.get()
            )
        );

    const ASN1_TIME *notBefore =
        X509_get0_notBefore(
            certificate.get()
        );

    const ASN1_TIME *notAfter =
        X509_get0_notAfter(
            certificate.get()
        );

    if (
        notBefore == nullptr ||
        notAfter == nullptr
    ) {
        result.status =
            DiagnosticStatus::
                tls_error;

        result.error =
            "certificate validity window is missing";

        return result;
    }

    int daysToStart = 0;
    int secondsToStart = 0;

    if (
        ASN1_TIME_diff(
            &daysToStart,
            &secondsToStart,
            nullptr,
            notBefore
        ) != 1
    ) {
        result.status =
            DiagnosticStatus::
                tls_error;

        result.error =
            "failed to parse certificate notBefore";

        return result;
    }

    if (
        daysToStart > 0 ||
        (
            daysToStart == 0 &&
            secondsToStart > 0
        )
    ) {
        result.status =
            DiagnosticStatus::
                certificate_not_yet_valid;

        result.error =
            "certificate is not yet valid";

        return result;
    }

    int daysToExpiry = 0;
    int secondsToExpiry = 0;

    if (
        ASN1_TIME_diff(
            &daysToExpiry,
            &secondsToExpiry,
            nullptr,
            notAfter
        ) != 1
    ) {
        result.status =
            DiagnosticStatus::
                tls_error;

        result.error =
            "failed to parse certificate notAfter";

        return result;
    }

    result.daysToExpiry =
        daysToExpiry;

    if (
        daysToExpiry < 0 ||
        (
            daysToExpiry == 0 &&
            secondsToExpiry < 0
        )
    ) {
        result.status =
            DiagnosticStatus::
                certificate_expired;

        result.error =
            "certificate has expired";

        return result;
    }

    result.status =
        DiagnosticStatus::ok;

    return result;
}

}