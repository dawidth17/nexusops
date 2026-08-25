#ifndef NEXUSOPS_SQLITE_SPOOL_H
#define NEXUSOPS_SQLITE_SPOOL_H

#include <cstddef>
#include <cstdint>
#include <mutex>
#include <string>
#include <vector>

struct sqlite3;

namespace nexusops::agent {

enum class SpoolStatus {
    ok,
    full,
    invalid_argument,
    storage_error
};

enum class TelemetryPayloadFormat : int {
    legacy_text = 1,
    protobuf = 2
};

struct TelemetryRecord {
    std::int64_t sequence{0};
    std::string capturedAtUtc;
    std::string kind;
    TelemetryPayloadFormat payloadFormat{
        TelemetryPayloadFormat::legacy_text
    };
    std::string payload;
};

class SQLiteSpool {
public:
    static constexpr int schemaVersion = 2;

    static constexpr std::size_t
        maxTimestampBytes = 64;

    static constexpr std::size_t
        maxKindBytes = 64;

    static constexpr std::size_t
        maxPayloadBytes = 64U * 1024U;

    SQLiteSpool(
        std::string databasePath,
        std::size_t maxRecords
    );

    ~SQLiteSpool();

    SQLiteSpool(
        const SQLiteSpool &
    ) = delete;

    SQLiteSpool &operator=(
        const SQLiteSpool &
    ) = delete;

    SQLiteSpool(
        SQLiteSpool &&
    ) = delete;

    SQLiteSpool &operator=(
        SQLiteSpool &&
    ) = delete;

    SpoolStatus enqueue(
        const std::string &capturedAtUtc,
        const std::string &kind,
        const std::string &payload,
        std::int64_t *sequence
    );

    SpoolStatus enqueueProtobuf(
        const std::string &capturedAtUtc,
        const std::string &kind,
        const std::string &payload,
        std::int64_t *sequence
    );

    SpoolStatus peekOldest(
        std::size_t limit,
        std::vector<TelemetryRecord> &records
    );

    SpoolStatus acknowledgeThrough(
        std::int64_t sequence,
        std::size_t &removedCount
    );

    SpoolStatus count(
        std::size_t &recordCount
    );

    [[nodiscard]]
    int currentSchemaVersion() const noexcept;

    [[nodiscard]]
    std::size_t maxRecords() const noexcept;

    [[nodiscard]]
    std::string lastError() const;

private:
    SpoolStatus enqueueWithFormat(
        const std::string &capturedAtUtc,
        const std::string &kind,
        TelemetryPayloadFormat payloadFormat,
        const std::string &payload,
        std::int64_t *sequence
    );

    bool executeLocked(
        const char *sql
    );

    bool readSchemaVersionLocked(
        int &version
    );

    bool migrateLocked();

    bool countLocked(
        std::size_t &recordCount
    );

    void setStorageErrorLocked(
        const std::string &context
    );

    void clearErrorLocked();

    std::string databasePath_;

    const std::size_t maxRecords_;

    sqlite3 *database_{nullptr};

    int currentSchemaVersion_{0};

    mutable std::mutex mutex_;

    std::string lastError_;
};

}

#endif