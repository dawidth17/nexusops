#include "nexusops/agent/signal_waiter.h"

#include <gtest/gtest.h>

#include <atomic>
#include <chrono>
#include <thread>

#include <pthread.h>
#include <signal.h>

using namespace std::chrono_literals;

namespace {

using nexusops::agent::SignalEventType;
using nexusops::agent::SignalWaiter;

TEST(
    SignalWaiterTests,
    returnsTimeoutWhenNoSignalArrives
)
{
    SignalWaiter waiter;

    const auto event =
        waiter.waitFor(
            20ms
        );

    EXPECT_EQ(
        event.type,
        SignalEventType::timeout
    );

    EXPECT_EQ(
        event.signalNumber,
        0
    );
}

TEST(
    SignalWaiterTests,
    classifiesSighupAsReload
)
{
    SignalWaiter waiter;

    const pthread_t target =
        pthread_self();

    std::atomic<int>
        sendResult{-1};

    std::thread sender(
        [
            target,
            &sendResult
        ]() {
            std::this_thread::sleep_for(
                20ms
            );

            sendResult.store(
                pthread_kill(
                    target,
                    SIGHUP
                )
            );
        }
    );

    const auto event =
        waiter.waitFor(
            500ms
        );

    sender.join();

    EXPECT_EQ(
        sendResult.load(),
        0
    );

    EXPECT_EQ(
        event.type,
        SignalEventType::reload
    );

    EXPECT_EQ(
        event.signalNumber,
        SIGHUP
    );
}

TEST(
    SignalWaiterTests,
    classifiesSigtermAsShutdown
)
{
    SignalWaiter waiter;

    const pthread_t target =
        pthread_self();

    std::atomic<int>
        sendResult{-1};

    std::thread sender(
        [
            target,
            &sendResult
        ]() {
            std::this_thread::sleep_for(
                20ms
            );

            sendResult.store(
                pthread_kill(
                    target,
                    SIGTERM
                )
            );
        }
    );

    const auto event =
        waiter.waitFor(
            500ms
        );

    sender.join();

    EXPECT_EQ(
        sendResult.load(),
        0
    );

    EXPECT_EQ(
        event.type,
        SignalEventType::shutdown
    );

    EXPECT_EQ(
        event.signalNumber,
        SIGTERM
    );
}

TEST(
    SignalWaiterTests,
    classifiesSigintAsShutdown
)
{
    SignalWaiter waiter;

    const pthread_t target =
        pthread_self();

    std::atomic<int>
        sendResult{-1};

    std::thread sender(
        [
            target,
            &sendResult
        ]() {
            std::this_thread::sleep_for(
                20ms
            );

            sendResult.store(
                pthread_kill(
                    target,
                    SIGINT
                )
            );
        }
    );

    const auto event =
        waiter.waitFor(
            500ms
        );

    sender.join();

    EXPECT_EQ(
        sendResult.load(),
        0
    );

    EXPECT_EQ(
        event.type,
        SignalEventType::shutdown
    );

    EXPECT_EQ(
        event.signalNumber,
        SIGINT
    );
}

}