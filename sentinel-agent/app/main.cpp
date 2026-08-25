#include "nexusops/agent/bounded_thread_pool.h"
#include "nexusops/agent/diagnostic_cli.h"
#include "nexusops/agent/grpc_telemetry_client.h"
#include "nexusops/agent/scheduler.h"
#include "nexusops/agent/signal_waiter.h"
#include "nexusops/agent/sqlite_spool.h"
#include "nexusops/agent/telemetry_codec.h"
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
#include <system_error>
#include <utility>
#include <vector>

#include <signal.h>
#include <unistd.h>

namespace {

using nexusops::agent::BoundedThreadPool;
using nexusops::agent::GrpcTelemetryClient;
using nexusops::agent::GrpcTelemetryClientOptions;
using nexusops::agent::Scheduler;
using nexusops::agent::SignalEventType;
using nexusops::agent::SignalWaiter;
using nexusops::agent::SQLiteSpool;
using nexusops::agent::SpoolStatus;
using nexusops::agent::TelemetryFlushStatus;
using nexusops::agent::encodeNetworkSnapshot;
using nexusops::agent::encodeProcessSnapshot;
using nexusops::agent::encodeSystemMetrics;

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

std::string resolveHostname()
{
    const char *configuredHostname =
        std::getenv(
            "NEXUSOPS_SENTINEL_HOSTNAME"
        );

    if (
        configuredHostname != nullptr &&
        configuredHostname[0] != '\0'
    ) {
        return configuredHostname;
    }

    char hostname[256]{};

    if (
        gethostname(
            hostname,
            sizeof(hostname) - 1
        ) != 0
    ) {
        throw std::runtime_error(
            "failed to resolve local hostname"
        );
    }

    hostname[
        sizeof(hostname) - 1
    ] = '\0';

    if (hostname[0] == '\0') {
        throw std::runtime_error(
            "local hostname is empty"
        );
    }

    return hostname;
}

std::string resolveAgentId(
    const std::string &hostname
)
{
    const char *configuredAgentId =
        std::getenv(
            "NEXUSOPS_SENTINEL_AGENT_ID"
        );

    if (
        configuredAgentId != nullptr &&
        configuredAgentId[0] != '\0'
    ) {
        return configuredAgentId;
    }

    return
        "sentinel-" +
        hostname;
}

std::string resolveOpsSightEndpoint()
{
    const char *configuredEndpoint =
        std::getenv(
            "NEXUSOPS_SENTINEL_OPSSIGHT_ENDPOINT"
        );

    if (
        configuredEndpoint != nullptr &&
        configuredEndpoint[0] != '\0'
    ) {
        return configuredEndpoint;
    }

    return "127.0.0.1:50051";
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

const char *telemetryFlushStatusName(
    TelemetryFlushStatus status
)
{
    switch (status) {
        case TelemetryFlushStatus::ok:
            return "ok";

        case TelemetryFlushStatus::
            nothing_to_send:
            return "nothing_to_send";

        case TelemetryFlushStatus::
            transport_error:
            return "transport_error";

        case TelemetryFlushStatus::
            protocol_error:
            return "protocol_error";

        case TelemetryFlushStatus::
            spool_error:
            return "spool_error";

        case TelemetryFlushStatus::
            invalid_payload:
            return "invalid_payload";

        case TelemetryFlushStatus::
            backoff:
            return "backoff";
    }

    return "unknown";
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
    const std::string &kind,
    const std::string &payload,
    std::atomic<std::size_t>
        &spoolFullCount,
    std::atomic<std::size_t>
        &spoolErrorCount
)
{
    std::int64_t sequence = 0;

    const auto status =
        spool.enqueueProtobuf(
            utcNow(),
            kind,
            payload,
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
        workerCount = 4;

    constexpr std::size_t
        queueCapacity = 8;

    constexpr std::size_t
        maxSpoolRecords = 100000;

    constexpr std::size_t
        maxTelemetryBatchRecords = 16;

    constexpr auto
        transportInterval =
            std::chrono::seconds(5);

    constexpr auto
        transportDeadline =
            std::chrono::milliseconds(
                1500
            );

    SQLiteSpool spool(
        spoolPath,
        maxSpoolRecords
    );

    const std::string hostname =
        resolveHostname();

    const std::string agentId =
        resolveAgentId(
            hostname
        );

    const std::string opsSightEndpoint =
        resolveOpsSightEndpoint();

    GrpcTelemetryClientOptions
        telemetryOptions;

    telemetryOptions.endpoint =
        opsSightEndpoint;

    telemetryOptions.agentId =
        agentId;

    telemetryOptions.hostname =
        hostname;

    telemetryOptions.agentVersion =
        SENTINEL_AGENT_VERSION;

    telemetryOptions.maxBatchRecords =
        maxTelemetryBatchRecords;

    telemetryOptions.rpcDeadline =
        transportDeadline;

    GrpcTelemetryClient
        telemetryClient(
            std::move(
                telemetryOptions
            )
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

    std::atomic<std::size_t>
        transportRuns{0};

    std::atomic<std::size_t>
        transportErrors{0};

    std::atomic<std::size_t>
        transportAcknowledgedRecords{0};

    auto flushTelemetry =
        [&]() {
            const auto result =
                telemetryClient.flush(
                    spool
                );

            ++transportRuns;

            if (
                result.status ==
                TelemetryFlushStatus::ok
            ) {
                transportAcknowledgedRecords.
                    fetch_add(
                        result.
                            acknowledgedRecords
                    );

                if (
                    result.
                        acknowledgedRecords >
                    0
                ) {
                    std::lock_guard lock(
                        outputMutex
                    );

                    std::cout
                        << "transport status=ok"
                        << " sent_records="
                        << result.sentRecords
                        << " acknowledged_records="
                        << result.
                            acknowledgedRecords
                        << '\n';
                }

                return;
            }

            if (
                result.status ==
                TelemetryFlushStatus::
                    nothing_to_send
            ) {
                return;
            }

            ++transportErrors;

            std::lock_guard lock(
                outputMutex
            );

            std::cerr
                << "transport status="
                << telemetryFlushStatusName(
                    result.status
                )
                << " sent_records="
                << result.sentRecords
                << " acknowledged_records="
                << result.
                    acknowledgedRecords;

            if (!result.message.empty()) {
                std::cerr
                    << " message="
                    << result.message;
            }

            std::cerr << '\n';
        };

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

                std::string payload;

                if (
                    !encodeSystemMetrics(
                        cpuUsagePercent,
                        memory,
                        filesystem,
                        uptime,
                        payload
                    )
                ) {
                    ++collectionErrors;

                    return;
                }

                persistTelemetry(
                    spool,
                    "system",
                    payload,
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

                std::string payload;

                if (
                    !encodeNetworkSnapshot(
                        interfaces,
                        payload
                    )
                ) {
                    ++collectionErrors;

                    return;
                }

                persistTelemetry(
                    spool,
                    "network",
                    payload,
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

                std::string payload;

                if (
                    !encodeProcessSnapshot(
                        processes,
                        payload
                    )
                ) {
                    ++collectionErrors;

                    return;
                }

                persistTelemetry(
                    spool,
                    "processes",
                    payload,
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

    const auto transportResult =
        scheduler.schedulePeriodic(
            "telemetry_transport",
            transportInterval,
            flushTelemetry,
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
                scheduled ||
        transportResult !=
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
            << " opssight_endpoint="
            << opsSightEndpoint
            << " agent_id="
            << agentId
            << " hostname="
            << hostname
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

    flushTelemetry();

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
        << " transport_runs="
        << transportRuns.load()
        << " transport_errors="
        << transportErrors.load()
        << " transport_acknowledged_records="
        << transportAcknowledgedRecords.load()
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