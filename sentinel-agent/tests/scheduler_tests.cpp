#include "nexusops/agent/bounded_thread_pool.h"
#include "nexusops/agent/scheduler.h"

#include <gtest/gtest.h>

#include <atomic>
#include <chrono>
#include <condition_variable>
#include <future>
#include <mutex>

using namespace std::chrono_literals;

namespace {

using nexusops::agent::BoundedThreadPool;
using nexusops::agent::Scheduler;

TEST(
    SchedulerTests,
    rejectsInvalidJobs
)
{
    BoundedThreadPool pool(
        1,
        4
    );

    Scheduler scheduler(
        pool
    );

    EXPECT_EQ(
        scheduler.schedulePeriodic(
            "",
            10ms,
            []() {
            },
            true
        ),
        Scheduler::
            ScheduleResult::invalid_argument
    );

    EXPECT_EQ(
        scheduler.schedulePeriodic(
            "zero",
            0ms,
            []() {
            },
            true
        ),
        Scheduler::
            ScheduleResult::invalid_argument
    );

    Scheduler::Task emptyTask;

    EXPECT_EQ(
        scheduler.schedulePeriodic(
            "empty",
            10ms,
            std::move(emptyTask),
            true
        ),
        Scheduler::
            ScheduleResult::invalid_argument
    );

    scheduler.stop();

    pool.stop();
}

TEST(
    SchedulerTests,
    executesPeriodicJobs
)
{
    BoundedThreadPool pool(
        2,
        8
    );

    Scheduler scheduler(
        pool
    );

    std::atomic<int> executions{0};

    std::mutex mutex;
    std::condition_variable condition;

    ASSERT_EQ(
        scheduler.schedulePeriodic(
            "periodic",
            20ms,
            [&]() {
                ++executions;

                condition.notify_all();
            },
            true
        ),
        Scheduler::
            ScheduleResult::scheduled
    );

    ASSERT_TRUE(
        scheduler.start()
    );

    {
        std::unique_lock lock(
            mutex
        );

        ASSERT_TRUE(
            condition.wait_for(
                lock,
                500ms,
                [&]() {
                    return executions.load() >= 3;
                }
            )
        );
    }

    scheduler.stop();

    pool.stop();

    EXPECT_GE(
        executions.load(),
        3
    );
}

TEST(
    SchedulerTests,
    recordsBackpressureWhenPoolIsFull
)
{
    BoundedThreadPool pool(
        1,
        1
    );

    Scheduler scheduler(
        pool
    );

    std::promise<void> startedPromise;

    auto startedFuture =
        startedPromise.get_future();

    std::promise<void> releasePromise;

    auto releaseFuture =
        releasePromise
            .get_future()
            .share();

    std::atomic<bool> started{false};

    ASSERT_EQ(
        scheduler.schedulePeriodic(
            "overload",
            1ms,
            [
                &started,
                &startedPromise,
                releaseFuture
            ]() {
                if (
                    !started.exchange(true)
                ) {
                    startedPromise.set_value();
                }

                releaseFuture.wait();
            },
            true
        ),
        Scheduler::
            ScheduleResult::scheduled
    );

    ASSERT_TRUE(
        scheduler.start()
    );

    ASSERT_EQ(
        startedFuture.wait_for(
            500ms
        ),
        std::future_status::ready
    );

    std::this_thread::sleep_for(
        30ms
    );

    scheduler.stop();

    EXPECT_GT(
        scheduler.droppedSubmissionCount(),
        0U
    );

    releasePromise.set_value();

    pool.stop();
}

TEST(
    SchedulerTests,
    startsOnlyOnce
)
{
    BoundedThreadPool pool(
        1,
        4
    );

    Scheduler scheduler(
        pool
    );

    EXPECT_TRUE(
        scheduler.start()
    );

    EXPECT_FALSE(
        scheduler.start()
    );

    scheduler.stop();

    EXPECT_FALSE(
        scheduler.start()
    );

    pool.stop();
}

TEST(
    SchedulerTests,
    stopPreventsFutureExecutions
)
{
    BoundedThreadPool pool(
        1,
        8
    );

    Scheduler scheduler(
        pool
    );

    std::atomic<int> executions{0};

    std::mutex mutex;
    std::condition_variable condition;

    ASSERT_EQ(
        scheduler.schedulePeriodic(
            "stop-test",
            10ms,
            [&]() {
                ++executions;

                condition.notify_all();
            },
            true
        ),
        Scheduler::
            ScheduleResult::scheduled
    );

    ASSERT_TRUE(
        scheduler.start()
    );

    {
        std::unique_lock lock(
            mutex
        );

        ASSERT_TRUE(
            condition.wait_for(
                lock,
                500ms,
                [&]() {
                    return executions.load() >= 1;
                }
            )
        );
    }

    scheduler.stop();

    pool.stop();

    const int finalCount =
        executions.load();

    std::this_thread::sleep_for(
        30ms
    );

    EXPECT_EQ(
        executions.load(),
        finalCount
    );
}

}