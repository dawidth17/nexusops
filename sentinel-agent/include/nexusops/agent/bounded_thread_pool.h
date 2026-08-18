#ifndef NEXUSOPS_BOUNDED_THREAD_POOL_H
#define NEXUSOPS_BOUNDED_THREAD_POOL_H

#include <condition_variable>
#include <cstddef>
#include <functional>
#include <mutex>
#include <queue>
#include <thread>
#include <vector>

namespace nexusops::agent {

class BoundedThreadPool {
public:
    using Task = std::function<void()>;

    enum class SubmitResult {
        accepted,
        queue_full,
        stopped,
        invalid_task
    };

    BoundedThreadPool(
        std::size_t workerCount,
        std::size_t queueCapacity
    );

    ~BoundedThreadPool();

    BoundedThreadPool(
        const BoundedThreadPool &
    ) = delete;

    BoundedThreadPool &operator=(
        const BoundedThreadPool &
    ) = delete;

    BoundedThreadPool(
        BoundedThreadPool &&
    ) = delete;

    BoundedThreadPool &operator=(
        BoundedThreadPool &&
    ) = delete;

    SubmitResult submit(
        Task task
    );

    void stop();

    [[nodiscard]]
    std::size_t workerCount() const noexcept;

    [[nodiscard]]
    std::size_t queueCapacity() const noexcept;

    [[nodiscard]]
    std::size_t queuedTaskCount() const;

    [[nodiscard]]
    std::size_t activeTaskCount() const;

    [[nodiscard]]
    bool isStopped() const;

private:
    void workerLoop();

    const std::size_t queueCapacity_;

    mutable std::mutex mutex_;
    std::condition_variable taskAvailable_;

    std::queue<Task> tasks_;
    std::vector<std::thread> workers_;

    std::size_t activeTasks_{0};

    bool accepting_{true};
    bool stopping_{false};
};

}

#endif