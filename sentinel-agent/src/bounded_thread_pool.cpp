#include "nexusops/agent/bounded_thread_pool.h"

#include <stdexcept>
#include <utility>

namespace nexusops::agent {

BoundedThreadPool::BoundedThreadPool(
    std::size_t workerCount,
    std::size_t queueCapacity
)
    : queueCapacity_(queueCapacity)
{
    if (workerCount == 0) {
        throw std::invalid_argument(
            "worker count must be greater than zero"
        );
    }

    if (queueCapacity == 0) {
        throw std::invalid_argument(
            "queue capacity must be greater than zero"
        );
    }

    workers_.reserve(workerCount);

    try {
        for (
            std::size_t index = 0;
            index < workerCount;
            ++index
        ) {
            workers_.emplace_back(
                [this]() {
                    workerLoop();
                }
            );
        }
    } catch (...) {
        {
            std::lock_guard lock(mutex_);

            accepting_ = false;
            stopping_ = true;
        }

        taskAvailable_.notify_all();

        for (auto &worker : workers_) {
            if (worker.joinable()) {
                worker.join();
            }
        }

        throw;
    }
}

BoundedThreadPool::~BoundedThreadPool()
{
    stop();
}

BoundedThreadPool::SubmitResult
BoundedThreadPool::submit(
    Task task
)
{
    if (!task) {
        return SubmitResult::invalid_task;
    }

    {
        std::lock_guard lock(mutex_);

        if (!accepting_) {
            return SubmitResult::stopped;
        }

        if (
            tasks_.size() >=
            queueCapacity_
        ) {
            return SubmitResult::queue_full;
        }

        tasks_.push(
            std::move(task)
        );
    }

    taskAvailable_.notify_one();

    return SubmitResult::accepted;
}

void BoundedThreadPool::stop()
{
    {
        std::lock_guard lock(mutex_);

        if (stopping_) {
            return;
        }

        accepting_ = false;
        stopping_ = true;
    }

    taskAvailable_.notify_all();

    for (auto &worker : workers_) {
        if (worker.joinable()) {
            worker.join();
        }
    }
}

std::size_t
BoundedThreadPool::workerCount() const noexcept
{
    return workers_.size();
}

std::size_t
BoundedThreadPool::queueCapacity() const noexcept
{
    return queueCapacity_;
}

std::size_t
BoundedThreadPool::queuedTaskCount() const
{
    std::lock_guard lock(mutex_);

    return tasks_.size();
}

std::size_t
BoundedThreadPool::activeTaskCount() const
{
    std::lock_guard lock(mutex_);

    return activeTasks_;
}

bool BoundedThreadPool::isStopped() const
{
    std::lock_guard lock(mutex_);

    return !accepting_;
}

void BoundedThreadPool::workerLoop()
{
    for (;;) {
        Task task;

        {
            std::unique_lock lock(mutex_);

            taskAvailable_.wait(
                lock,
                [this]() {
                    return stopping_ ||
                        !tasks_.empty();
                }
            );

            if (
                stopping_ &&
                tasks_.empty()
            ) {
                return;
            }

            task = std::move(
                tasks_.front()
            );

            tasks_.pop();

            ++activeTasks_;
        }

        try {
            task();
        } catch (...) {
        }

        {
            std::lock_guard lock(mutex_);

            --activeTasks_;
        }
    }
}

}