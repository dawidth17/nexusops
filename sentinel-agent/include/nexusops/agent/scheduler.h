#ifndef NEXUSOPS_SCHEDULER_H
#define NEXUSOPS_SCHEDULER_H

#include "nexusops/agent/bounded_thread_pool.h"

#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstddef>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

namespace nexusops::agent {

class Scheduler {
public:
    using Duration =
        std::chrono::milliseconds;

    using Task =
        BoundedThreadPool::Task;

    enum class ScheduleResult {
        scheduled,
        invalid_argument,
        stopped
    };

    explicit Scheduler(
        BoundedThreadPool &threadPool
    );

    ~Scheduler();

    Scheduler(
        const Scheduler &
    ) = delete;

    Scheduler &operator=(
        const Scheduler &
    ) = delete;

    Scheduler(
        Scheduler &&
    ) = delete;

    Scheduler &operator=(
        Scheduler &&
    ) = delete;

    ScheduleResult schedulePeriodic(
        std::string name,
        Duration interval,
        Task task,
        bool runImmediately
    );

    bool start();

    void stop();

    [[nodiscard]]
    std::size_t jobCount() const;

    [[nodiscard]]
    std::size_t droppedSubmissionCount() const noexcept;

    [[nodiscard]]
    bool isRunning() const;

private:
    using Clock =
        std::chrono::steady_clock;

    struct PeriodicJob {
        std::string name;
        Duration interval;
        Task task;
        bool runImmediately;
        Clock::time_point nextRun;
    };

    void runLoop();

    BoundedThreadPool &threadPool_;

    mutable std::mutex mutex_;
    std::condition_variable wakeUp_;

    std::vector<PeriodicJob> jobs_;

    std::thread schedulerThread_;

    std::atomic<std::size_t>
        droppedSubmissions_{0};

    bool running_{false};
    bool stopRequested_{false};
    bool stopped_{false};
};

}

#endif