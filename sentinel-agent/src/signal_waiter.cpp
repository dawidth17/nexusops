#include "nexusops/agent/signal_waiter.h"

#include <cerrno>
#include <stdexcept>
#include <system_error>

#include <pthread.h>
#include <signal.h>
#include <time.h>

namespace nexusops::agent {

SignalWaiter::SignalWaiter()
{
    if (
        sigemptyset(
            &waitSet_
        ) != 0
    ) {
        throw std::system_error(
            errno,
            std::generic_category(),
            "failed to initialize signal set"
        );
    }

    if (
        sigaddset(
            &waitSet_,
            SIGTERM
        ) != 0 ||
        sigaddset(
            &waitSet_,
            SIGINT
        ) != 0 ||
        sigaddset(
            &waitSet_,
            SIGHUP
        ) != 0
    ) {
        throw std::system_error(
            errno,
            std::generic_category(),
            "failed to configure signal set"
        );
    }

    const int result =
        pthread_sigmask(
            SIG_BLOCK,
            &waitSet_,
            &previousMask_
        );

    if (result != 0) {
        throw std::system_error(
            result,
            std::generic_category(),
            "failed to block lifecycle signals"
        );
    }

    maskInstalled_ = true;
}

SignalWaiter::~SignalWaiter()
{
    if (maskInstalled_) {
        pthread_sigmask(
            SIG_SETMASK,
            &previousMask_,
            nullptr
        );
    }
}

SignalEvent SignalWaiter::wait()
{
    int signalNumber = 0;

    const int result =
        sigwait(
            &waitSet_,
            &signalNumber
        );

    if (result != 0) {
        throw std::system_error(
            result,
            std::generic_category(),
            "failed while waiting for signal"
        );
    }

    return classifySignal(
        signalNumber
    );
}

SignalEvent SignalWaiter::waitFor(
    std::chrono::milliseconds timeout
)
{
    if (
        timeout <
        std::chrono::milliseconds::zero()
    ) {
        throw std::invalid_argument(
            "signal timeout must not be negative"
        );
    }

    const auto seconds =
        std::chrono::duration_cast<
            std::chrono::seconds
        >(timeout);

    const auto nanoseconds =
        std::chrono::duration_cast<
            std::chrono::nanoseconds
        >(
            timeout - seconds
        );

    timespec timeoutValue{};

    timeoutValue.tv_sec =
        static_cast<time_t>(
            seconds.count()
        );

    timeoutValue.tv_nsec =
        static_cast<long>(
            nanoseconds.count()
        );

    const int signalNumber =
        sigtimedwait(
            &waitSet_,
            nullptr,
            &timeoutValue
        );

    if (signalNumber >= 0) {
        return classifySignal(
            signalNumber
        );
    }

    if (errno == EAGAIN) {
        return SignalEvent{
            SignalEventType::timeout,
            0
        };
    }

    throw std::system_error(
        errno,
        std::generic_category(),
        "failed while waiting for timed signal"
    );
}

SignalEvent SignalWaiter::classifySignal(
    int signalNumber
) const noexcept
{
    if (signalNumber == SIGHUP) {
        return SignalEvent{
            SignalEventType::reload,
            signalNumber
        };
    }

    return SignalEvent{
        SignalEventType::shutdown,
        signalNumber
    };
}

}