#include "nexusops/agent/scheduler.h"

#include <algorithm>
#include <utility>

namespace nexusops::agent {

Scheduler::Scheduler(
    BoundedThreadPool &threadPool
)
    : threadPool_(threadPool)
{
}

Scheduler::~Scheduler()
{
    stop();
}

Scheduler::ScheduleResult
Scheduler::schedulePeriodic(
    std::string name,
    Duration interval,
    Task task,
    bool runImmediately
)
{
    if (
        name.empty() ||
        interval <= Duration::zero() ||
        !task
    ) {
        return ScheduleResult::invalid_argument;
    }

    {
        std::lock_guard lock(mutex_);

        if (
            stopRequested_ ||
            stopped_
        ) {
            return ScheduleResult::stopped;
        }

        Clock::time_point nextRun{};

        if (running_) {
            const auto now =
                Clock::now();

            nextRun =
                runImmediately
                    ? now
                    : now + interval;
        }

        jobs_.push_back(
            PeriodicJob{
                std::move(name),
                interval,
                std::move(task),
                runImmediately,
                nextRun
            }
        );
    }

    wakeUp_.notify_one();

    return ScheduleResult::scheduled;
}

bool Scheduler::start()
{
    std::lock_guard lock(mutex_);

    if (
        running_ ||
        stopped_
    ) {
        return false;
    }

    const auto now =
        Clock::now();

    for (auto &job : jobs_) {
        job.nextRun =
            job.runImmediately
                ? now
                : now + job.interval;
    }

    stopRequested_ = false;
    running_ = true;

    try {
        schedulerThread_ =
            std::thread(
                [this]() {
                    runLoop();
                }
            );
    } catch (...) {
        running_ = false;

        throw;
    }

    return true;
}

void Scheduler::stop()
{
    {
        std::lock_guard lock(mutex_);

        if (stopped_) {
            return;
        }

        stopRequested_ = true;
        stopped_ = true;
    }

    wakeUp_.notify_all();

    if (schedulerThread_.joinable()) {
        schedulerThread_.join();
    }

    {
        std::lock_guard lock(mutex_);

        running_ = false;
    }
}

std::size_t
Scheduler::jobCount() const
{
    std::lock_guard lock(mutex_);

    return jobs_.size();
}

std::size_t
Scheduler::droppedSubmissionCount() const noexcept
{
    return droppedSubmissions_.load();
}

bool Scheduler::isRunning() const
{
    std::lock_guard lock(mutex_);

    return running_ &&
        !stopRequested_;
}

void Scheduler::runLoop()
{
    std::unique_lock lock(mutex_);

    while (!stopRequested_) {
        if (jobs_.empty()) {
            wakeUp_.wait(
                lock,
                [this]() {
                    return stopRequested_ ||
                        !jobs_.empty();
                }
            );

            continue;
        }

        const auto nextJob =
            std::min_element(
                jobs_.begin(),
                jobs_.end(),
                [](
                    const PeriodicJob &left,
                    const PeriodicJob &right
                ) {
                    return left.nextRun <
                        right.nextRun;
                }
            );

        const auto now =
            Clock::now();

        if (
            nextJob->nextRun >
            now
        ) {
            wakeUp_.wait_until(
                lock,
                nextJob->nextRun
            );

            continue;
        }

        std::vector<Task> dueTasks;

        const auto dispatchTime =
            Clock::now();

        for (auto &job : jobs_) {
            if (
                job.nextRun >
                dispatchTime
            ) {
                continue;
            }

            dueTasks.push_back(
                job.task
            );

            do {
                job.nextRun +=
                    job.interval;
            } while (
                job.nextRun <=
                dispatchTime
            );
        }

        lock.unlock();

        for (auto &task : dueTasks) {
            const auto result =
                threadPool_.submit(
                    std::move(task)
                );

            if (
                result !=
                BoundedThreadPool::
                    SubmitResult::accepted
            ) {
                ++droppedSubmissions_;
            }
        }

        lock.lock();
    }
}

}