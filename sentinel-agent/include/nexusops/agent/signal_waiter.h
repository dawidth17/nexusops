#ifndef NEXUSOPS_SIGNAL_WAITER_H
#define NEXUSOPS_SIGNAL_WAITER_H

#include <chrono>
#include <signal.h>

namespace nexusops::agent {

enum class SignalEventType {
    shutdown,
    reload,
    timeout
};

struct SignalEvent {
    SignalEventType type;
    int signalNumber;
};

class SignalWaiter {
public:
    SignalWaiter();

    ~SignalWaiter();

    SignalWaiter(
        const SignalWaiter &
    ) = delete;

    SignalWaiter &operator=(
        const SignalWaiter &
    ) = delete;

    SignalWaiter(
        SignalWaiter &&
    ) = delete;

    SignalWaiter &operator=(
        SignalWaiter &&
    ) = delete;

    SignalEvent wait();

    SignalEvent waitFor(
        std::chrono::milliseconds timeout
    );

private:
    [[nodiscard]]
    SignalEvent classifySignal(
        int signalNumber
    ) const noexcept;

    sigset_t waitSet_{};
    sigset_t previousMask_{};

    bool maskInstalled_{false};
};

}

#endif