#include "nexusops/agent/sqlite_spool.h"

#include <gtest/gtest.h>

#include <atomic>
#include <cstdint>
#include <filesystem>
#include <stdexcept>
#include <string>
#include <thread>
#include <vector>

#include <unistd.h>

namespace {

using nexusops::agent::SQLiteSpool;
using nexusops::agent::SpoolStatus;
using nexusops::agent::TelemetryRecord;

std::atomic<std::uint64_t>
    nextDatabaseId{0};

class TemporarySpoolFile {
public:
    TemporarySpoolFile()
    {
        const auto id =
            nextDatabaseId.fetch_add(
                1
            );

        path_ =
            (
                std::filesystem::
                    temp_directory_path() /
                (
                    "nexusops-spool-test-" +
                    std::to_string(
                        getpid()
                    ) +
                    "-" +
                    std::to_string(id) +
                    ".db"
                )
            ).string();
    }

    ~TemporarySpoolFile()
    {
        removeFiles();
    }

    [[nodiscard]]
    const std::string &path() const
    {
        return path_;
    }

private:
    void removeFiles()
    {
        std::error_code error;

        std::filesystem::remove(
            path_,
            error
        );

        error.clear();

        std::filesystem::remove(
            path_ + "-wal",
            error
        );

        error.clear();

        std::filesystem::remove(
            path_ + "-shm",
            error
        );
    }

    std::string path_;
};

TEST(
    SQLiteSpoolTests,
    rejectsInvalidConfiguration
)
{
    TemporarySpoolFile file;

    EXPECT_THROW(
        SQLiteSpool(
            "",
            10
        ),
        std::invalid_argument
    );

    EXPECT_THROW(
        SQLiteSpool(
            file.path(),
            0
        ),
        std::invalid_argument
    );
}

TEST(
    SQLiteSpoolTests,
    createsSchemaAndStartsEmpty
)
{
    TemporarySpoolFile file;

    SQLiteSpool spool(
        file.path(),
        10
    );

    EXPECT_EQ(
        spool.currentSchemaVersion(),
        SQLiteSpool::schemaVersion
    );

    EXPECT_EQ(
        spool.maxRecords(),
        10U
    );

    std::size_t count = 99;

    ASSERT_EQ(
        spool.count(count),
        SpoolStatus::ok
    );

    EXPECT_EQ(
        count,
        0U
    );
}

TEST(
    SQLiteSpoolTests,
    rejectsInvalidRecords
)
{
    TemporarySpoolFile file;

    SQLiteSpool spool(
        file.path(),
        10
    );

    std::int64_t sequence = 0;

    EXPECT_EQ(
        spool.enqueue(
            "",
            "system",
            "payload",
            &sequence
        ),
        SpoolStatus::
            invalid_argument
    );

    EXPECT_EQ(
        spool.enqueue(
            "2026-08-18T12:00:00Z",
            "",
            "payload",
            &sequence
        ),
        SpoolStatus::
            invalid_argument
    );

    EXPECT_EQ(
        spool.enqueue(
            "2026-08-18T12:00:00Z",
            "system",
            "",
            &sequence
        ),
        SpoolStatus::
            invalid_argument
    );

    std::string oversizedKind(
        SQLiteSpool::maxKindBytes + 1,
        'x'
    );

    EXPECT_EQ(
        spool.enqueue(
            "2026-08-18T12:00:00Z",
            oversizedKind,
            "payload",
            &sequence
        ),
        SpoolStatus::
            invalid_argument
    );

    EXPECT_EQ(
        spool.enqueue(
            "2026-08-18T12:00:00Z",
            "system",
            "payload",
            nullptr
        ),
        SpoolStatus::
            invalid_argument
    );
}

TEST(
    SQLiteSpoolTests,
    storesAndReadsInSequenceOrder
)
{
    TemporarySpoolFile file;

    SQLiteSpool spool(
        file.path(),
        10
    );

    std::int64_t firstSequence = 0;
    std::int64_t secondSequence = 0;
    std::int64_t thirdSequence = 0;

    ASSERT_EQ(
        spool.enqueue(
            "2026-08-18T12:00:00Z",
            "system",
            "cpu=10",
            &firstSequence
        ),
        SpoolStatus::ok
    );

    ASSERT_EQ(
        spool.enqueue(
            "2026-08-18T12:00:01Z",
            "network",
            "rx=100",
            &secondSequence
        ),
        SpoolStatus::ok
    );

    ASSERT_EQ(
        spool.enqueue(
            "2026-08-18T12:00:02Z",
            "processes",
            "count=42",
            &thirdSequence
        ),
        SpoolStatus::ok
    );

    EXPECT_LT(
        firstSequence,
        secondSequence
    );

    EXPECT_LT(
        secondSequence,
        thirdSequence
    );

    std::vector<
        TelemetryRecord
    > records;

    ASSERT_EQ(
        spool.peekOldest(
            2,
            records
        ),
        SpoolStatus::ok
    );

    ASSERT_EQ(
        records.size(),
        2U
    );

    EXPECT_EQ(
        records[0].sequence,
        firstSequence
    );

    EXPECT_EQ(
        records[0].kind,
        "system"
    );

    EXPECT_EQ(
        records[0].payload,
        "cpu=10"
    );

    EXPECT_EQ(
        records[1].sequence,
        secondSequence
    );

    EXPECT_EQ(
        records[1].kind,
        "network"
    );
}

TEST(
    SQLiteSpoolTests,
    returnsFullAtCapacity
)
{
    TemporarySpoolFile file;

    SQLiteSpool spool(
        file.path(),
        2
    );

    std::int64_t firstSequence = 0;
    std::int64_t secondSequence = 0;
    std::int64_t thirdSequence = 0;

    ASSERT_EQ(
        spool.enqueue(
            "2026-08-18T12:00:00Z",
            "system",
            "first",
            &firstSequence
        ),
        SpoolStatus::ok
    );

    ASSERT_EQ(
        spool.enqueue(
            "2026-08-18T12:00:01Z",
            "system",
            "second",
            &secondSequence
        ),
        SpoolStatus::ok
    );

    EXPECT_EQ(
        spool.enqueue(
            "2026-08-18T12:00:02Z",
            "system",
            "third",
            &thirdSequence
        ),
        SpoolStatus::full
    );

    std::size_t count = 0;

    ASSERT_EQ(
        spool.count(count),
        SpoolStatus::ok
    );

    EXPECT_EQ(
        count,
        2U
    );

    std::size_t removed = 0;

    ASSERT_EQ(
        spool.acknowledgeThrough(
            firstSequence,
            removed
        ),
        SpoolStatus::ok
    );

    EXPECT_EQ(
        removed,
        1U
    );

    EXPECT_EQ(
        spool.enqueue(
            "2026-08-18T12:00:02Z",
            "system",
            "third",
            &thirdSequence
        ),
        SpoolStatus::ok
    );
}

TEST(
    SQLiteSpoolTests,
    acknowledgesOnlyRequestedPrefix
)
{
    TemporarySpoolFile file;

    SQLiteSpool spool(
        file.path(),
        10
    );

    std::int64_t firstSequence = 0;
    std::int64_t secondSequence = 0;
    std::int64_t thirdSequence = 0;

    ASSERT_EQ(
        spool.enqueue(
            "2026-08-18T12:00:00Z",
            "system",
            "first",
            &firstSequence
        ),
        SpoolStatus::ok
    );

    ASSERT_EQ(
        spool.enqueue(
            "2026-08-18T12:00:01Z",
            "network",
            "second",
            &secondSequence
        ),
        SpoolStatus::ok
    );

    ASSERT_EQ(
        spool.enqueue(
            "2026-08-18T12:00:02Z",
            "processes",
            "third",
            &thirdSequence
        ),
        SpoolStatus::ok
    );

    std::size_t removed = 0;

    ASSERT_EQ(
        spool.acknowledgeThrough(
            secondSequence,
            removed
        ),
        SpoolStatus::ok
    );

    EXPECT_EQ(
        removed,
        2U
    );

    std::vector<
        TelemetryRecord
    > records;

    ASSERT_EQ(
        spool.peekOldest(
            10,
            records
        ),
        SpoolStatus::ok
    );

    ASSERT_EQ(
        records.size(),
        1U
    );

    EXPECT_EQ(
        records[0].sequence,
        thirdSequence
    );
}

TEST(
    SQLiteSpoolTests,
    persistsRecordsAcrossReopen
)
{
    TemporarySpoolFile file;

    std::int64_t firstSequence = 0;
    std::int64_t secondSequence = 0;

    {
        SQLiteSpool spool(
            file.path(),
            10
        );

        ASSERT_EQ(
            spool.enqueue(
                "2026-08-18T12:00:00Z",
                "system",
                "cpu=15",
                &firstSequence
            ),
            SpoolStatus::ok
        );

        ASSERT_EQ(
            spool.enqueue(
                "2026-08-18T12:00:01Z",
                "network",
                "rx=500",
                &secondSequence
            ),
            SpoolStatus::ok
        );
    }

    EXPECT_TRUE(
        std::filesystem::exists(
            file.path()
        )
    );

    {
        SQLiteSpool spool(
            file.path(),
            10
        );

        std::size_t count = 0;

        ASSERT_EQ(
            spool.count(count),
            SpoolStatus::ok
        );

        EXPECT_EQ(
            count,
            2U
        );

        std::vector<
            TelemetryRecord
        > records;

        ASSERT_EQ(
            spool.peekOldest(
                10,
                records
            ),
            SpoolStatus::ok
        );

        ASSERT_EQ(
            records.size(),
            2U
        );

        EXPECT_EQ(
            records[0].sequence,
            firstSequence
        );

        EXPECT_EQ(
            records[1].sequence,
            secondSequence
        );

        EXPECT_EQ(
            records[0].payload,
            "cpu=15"
        );

        EXPECT_EQ(
            records[1].payload,
            "rx=500"
        );
    }
}

TEST(
    SQLiteSpoolTests,
    serializesConcurrentEnqueues
)
{
    TemporarySpoolFile file;

    SQLiteSpool spool(
        file.path(),
        200
    );

    constexpr int threadCount = 4;
    constexpr int recordsPerThread = 25;

    std::atomic<int> failures{0};

    std::vector<std::thread>
        threads;

    for (
        int threadIndex = 0;
        threadIndex < threadCount;
        ++threadIndex
    ) {
        threads.emplace_back(
            [
                &spool,
                &failures,
                threadIndex
            ]() {
                for (
                    int index = 0;
                    index <
                        recordsPerThread;
                    ++index
                ) {
                    std::int64_t
                        sequence = 0;

                    const auto status =
                        spool.enqueue(
                            "2026-08-18T12:00:00Z",
                            "system",
                            "thread=" +
                                std::to_string(
                                    threadIndex
                                ) +
                                ";record=" +
                                std::to_string(
                                    index
                                ),
                            &sequence
                        );

                    if (
                        status !=
                        SpoolStatus::ok
                    ) {
                        ++failures;
                    }
                }
            }
        );
    }

    for (
        auto &thread :
        threads
    ) {
        thread.join();
    }

    EXPECT_EQ(
        failures.load(),
        0
    );

    std::size_t count = 0;

    ASSERT_EQ(
        spool.count(count),
        SpoolStatus::ok
    );

    EXPECT_EQ(
        count,
        static_cast<std::size_t>(
            threadCount *
            recordsPerThread
        )
    );

    std::vector<
        TelemetryRecord
    > records;

    ASSERT_EQ(
        spool.peekOldest(
            200,
            records
        ),
        SpoolStatus::ok
    );

    ASSERT_EQ(
        records.size(),
        count
    );

    for (
        std::size_t index = 1;
        index < records.size();
        ++index
    ) {
        EXPECT_LT(
            records[index - 1].
                sequence,
            records[index].
                sequence
        );
    }
}

}