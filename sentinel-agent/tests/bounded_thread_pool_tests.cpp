#include "nexusops/agent/bounded_thread_pool.h"

#include <gtest/gtest.h>

#include <atomic>
#include <chrono>
#include <future>
#include <stdexcept>

using namespace std::chrono_literals;

namespace {

using nexusops::agent::BoundedThreadPool;

TEST(
    BoundedThreadPoolTests,
    rejectsInvalidConfiguration
)
{
    EXPECT_THROW(
        BoundedThreadPool(0, 1),
        std::invalid_argument
    );

    EXPECT_THROW(
        BoundedThreadPool(1, 0),
        std::invalid_argument
    );
}

TEST(
    BoundedThreadPoolTests,
    rejectsEmptyTask
)
{
    BoundedThreadPool pool(
        1,
        2
    );

    BoundedThreadPool::Task task;

    EXPECT_EQ(
        pool.submit(
            std::move(task)
        ),
        BoundedThreadPool::
            SubmitResult::invalid_task
    );

    pool.stop();
}

TEST(
    BoundedThreadPoolTests,
    executesAcceptedTasksBeforeStop
)
{
    BoundedThreadPool pool(
        2,
        8
    );

    std::atomic<int> executed{0};

    for (
        int index = 0;
        index < 6;
        ++index
    ) {
        ASSERT_EQ(
            pool.submit(
                [&executed]() {
                    ++executed;
                }
            ),
            BoundedThreadPool::
                SubmitResult::accepted
        );
    }

    pool.stop();

    EXPECT_EQ(
        executed.load(),
        6
    );
}

TEST(
    BoundedThreadPoolTests,
    returnsQueueFullWhenCapacityIsReached
)
{
    BoundedThreadPool pool(
        1,
        1
    );

    std::promise<void> startedPromise;

    auto startedFuture =
        startedPromise.get_future();

    std::promise<void> releasePromise;

    auto releaseFuture =
        releasePromise
            .get_future()
            .share();

    ASSERT_EQ(
        pool.submit(
            [
                &startedPromise,
                releaseFuture
            ]() {
                startedPromise.set_value();

                releaseFuture.wait();
            }
        ),
        BoundedThreadPool::
            SubmitResult::accepted
    );

    ASSERT_EQ(
        startedFuture.wait_for(
            500ms
        ),
        std::future_status::ready
    );

    ASSERT_EQ(
        pool.submit(
            []() {
            }
        ),
        BoundedThreadPool::
            SubmitResult::accepted
    );

    EXPECT_EQ(
        pool.submit(
            []() {
            }
        ),
        BoundedThreadPool::
            SubmitResult::queue_full
    );

    releasePromise.set_value();

    pool.stop();
}

TEST(
    BoundedThreadPoolTests,
    drainsAcceptedWorkAndRejectsAfterStop
)
{
    BoundedThreadPool pool(
        1,
        4
    );

    std::atomic<int> executed{0};

    ASSERT_EQ(
        pool.submit(
            [&executed]() {
                ++executed;
            }
        ),
        BoundedThreadPool::
            SubmitResult::accepted
    );

    pool.stop();

    EXPECT_EQ(
        executed.load(),
        1
    );

    EXPECT_EQ(
        pool.submit(
            []() {
            }
        ),
        BoundedThreadPool::
            SubmitResult::stopped
    );
}

TEST(
    BoundedThreadPoolTests,
    workerSurvivesTaskException
)
{
    BoundedThreadPool pool(
        1,
        4
    );

    std::atomic<int> executed{0};

    ASSERT_EQ(
        pool.submit(
            []() {
                throw std::runtime_error(
                    "test failure"
                );
            }
        ),
        BoundedThreadPool::
            SubmitResult::accepted
    );

    ASSERT_EQ(
        pool.submit(
            [&executed]() {
                ++executed;
            }
        ),
        BoundedThreadPool::
            SubmitResult::accepted
    );

    pool.stop();

    EXPECT_EQ(
        executed.load(),
        1
    );
}

}