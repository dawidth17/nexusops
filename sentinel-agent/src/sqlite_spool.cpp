#include "nexusops/agent/sqlite_spool.h"

#include <sqlite3.h>

#include <limits>
#include <stdexcept>
#include <utility>

namespace {

class Statement {
public:
    explicit Statement(
        sqlite3_stmt *statement
    )
        : statement_(statement)
    {
    }

    ~Statement()
    {
        if (statement_ != nullptr) {
            sqlite3_finalize(statement_);
        }
    }

    Statement(
        const Statement &
    ) = delete;

    Statement &operator=(
        const Statement &
    ) = delete;

    [[nodiscard]]
    sqlite3_stmt *get() const noexcept
    {
        return statement_;
    }

private:
    sqlite3_stmt *statement_;
};

}

namespace nexusops::agent {

SQLiteSpool::SQLiteSpool(
    std::string databasePath,
    std::size_t maxRecords
)
    : databasePath_(
          std::move(databasePath)
      ),
      maxRecords_(maxRecords)
{
    if (databasePath_.empty()) {
        throw std::invalid_argument(
            "database path must not be empty"
        );
    }

    if (maxRecords_ == 0) {
        throw std::invalid_argument(
            "max records must be greater than zero"
        );
    }

    const int openResult =
        sqlite3_open_v2(
            databasePath_.c_str(),
            &database_,
            SQLITE_OPEN_READWRITE |
                SQLITE_OPEN_CREATE |
                SQLITE_OPEN_FULLMUTEX,
            nullptr
        );

    if (openResult != SQLITE_OK) {
        std::string message =
            "failed to open sqlite spool";

        if (database_ != nullptr) {
            message += ": ";
            message +=
                sqlite3_errmsg(
                    database_
                );

            sqlite3_close(
                database_
            );

            database_ = nullptr;
        }

        throw std::runtime_error(
            message
        );
    }

    sqlite3_extended_result_codes(
        database_,
        1
    );

    sqlite3_busy_timeout(
        database_,
        2000
    );

    if (
        !executeLocked(
            "PRAGMA journal_mode=WAL;"
        ) ||
        !executeLocked(
            "PRAGMA synchronous=FULL;"
        ) ||
        !migrateLocked()
    ) {
        const std::string message =
            lastError_.empty()
                ? "failed to initialize sqlite spool"
                : lastError_;

        sqlite3_close(
            database_
        );

        database_ = nullptr;

        throw std::runtime_error(
            message
        );
    }
}

SQLiteSpool::~SQLiteSpool()
{
    if (database_ != nullptr) {
        sqlite3_close(
            database_
        );

        database_ = nullptr;
    }
}

SpoolStatus SQLiteSpool::enqueue(
    std::string capturedAtUtc,
    std::string kind,
    std::string payload,
    std::int64_t *sequence
)
{
    if (
        sequence == nullptr ||
        capturedAtUtc.empty() ||
        capturedAtUtc.size() >
            maxTimestampBytes ||
        kind.empty() ||
        kind.size() >
            maxKindBytes ||
        payload.empty() ||
        payload.size() >
            maxPayloadBytes
    ) {
        return SpoolStatus::
            invalid_argument;
    }

    std::lock_guard lock(
        mutex_
    );

    clearErrorLocked();

    if (
        !executeLocked(
            "BEGIN IMMEDIATE;"
        )
    ) {
        return SpoolStatus::
            storage_error;
    }

    std::size_t existingCount = 0;

    if (
        !countLocked(
            existingCount
        )
    ) {
        executeLocked(
            "ROLLBACK;"
        );

        return SpoolStatus::
            storage_error;
    }

    if (
        existingCount >=
        maxRecords_
    ) {
        executeLocked(
            "ROLLBACK;"
        );

        clearErrorLocked();

        return SpoolStatus::full;
    }

    sqlite3_stmt *rawStatement =
        nullptr;

    const char *sql =
        "INSERT INTO telemetry_spool("
        "captured_at_utc, kind, payload"
        ") VALUES (?, ?, ?);";

    if (
        sqlite3_prepare_v2(
            database_,
            sql,
            -1,
            &rawStatement,
            nullptr
        ) != SQLITE_OK
    ) {
        setStorageErrorLocked(
            "failed to prepare spool insert"
        );

        executeLocked(
            "ROLLBACK;"
        );

        return SpoolStatus::
            storage_error;
    }

    Statement statement(
        rawStatement
    );

    if (
        sqlite3_bind_text(
            statement.get(),
            1,
            capturedAtUtc.c_str(),
            -1,
            SQLITE_TRANSIENT
        ) != SQLITE_OK ||
        sqlite3_bind_text(
            statement.get(),
            2,
            kind.c_str(),
            -1,
            SQLITE_TRANSIENT
        ) != SQLITE_OK ||
        sqlite3_bind_text(
            statement.get(),
            3,
            payload.c_str(),
            -1,
            SQLITE_TRANSIENT
        ) != SQLITE_OK
    ) {
        setStorageErrorLocked(
            "failed to bind spool insert"
        );

        executeLocked(
            "ROLLBACK;"
        );

        return SpoolStatus::
            storage_error;
    }

    if (
        sqlite3_step(
            statement.get()
        ) != SQLITE_DONE
    ) {
        setStorageErrorLocked(
            "failed to execute spool insert"
        );

        executeLocked(
            "ROLLBACK;"
        );

        return SpoolStatus::
            storage_error;
    }

    const sqlite3_int64
        insertedSequence =
            sqlite3_last_insert_rowid(
                database_
            );

    if (
        !executeLocked(
            "COMMIT;"
        )
    ) {
        executeLocked(
            "ROLLBACK;"
        );

        return SpoolStatus::
            storage_error;
    }

    *sequence =
        static_cast<std::int64_t>(
            insertedSequence
        );

    clearErrorLocked();

    return SpoolStatus::ok;
}

SpoolStatus SQLiteSpool::peekOldest(
    std::size_t limit,
    std::vector<TelemetryRecord> &records
)
{
    if (
        limit == 0 ||
        limit >
            static_cast<std::size_t>(
                std::numeric_limits<
                    sqlite3_int64
                >::max()
            )
    ) {
        return SpoolStatus::
            invalid_argument;
    }

    std::lock_guard lock(
        mutex_
    );

    clearErrorLocked();

    records.clear();

    sqlite3_stmt *rawStatement =
        nullptr;

    const char *sql =
        "SELECT "
        "sequence, captured_at_utc, "
        "kind, payload "
        "FROM telemetry_spool "
        "ORDER BY sequence ASC "
        "LIMIT ?;";

    if (
        sqlite3_prepare_v2(
            database_,
            sql,
            -1,
            &rawStatement,
            nullptr
        ) != SQLITE_OK
    ) {
        setStorageErrorLocked(
            "failed to prepare spool read"
        );

        return SpoolStatus::
            storage_error;
    }

    Statement statement(
        rawStatement
    );

    if (
        sqlite3_bind_int64(
            statement.get(),
            1,
            static_cast<
                sqlite3_int64
            >(limit)
        ) != SQLITE_OK
    ) {
        setStorageErrorLocked(
            "failed to bind spool read"
        );

        return SpoolStatus::
            storage_error;
    }

    for (;;) {
        const int stepResult =
            sqlite3_step(
                statement.get()
            );

        if (
            stepResult ==
            SQLITE_DONE
        ) {
            break;
        }

        if (
            stepResult !=
            SQLITE_ROW
        ) {
            setStorageErrorLocked(
                "failed to execute spool read"
            );

            return SpoolStatus::
                storage_error;
        }

        const unsigned char
            *capturedAt =
                sqlite3_column_text(
                    statement.get(),
                    1
                );

        const unsigned char *kind =
            sqlite3_column_text(
                statement.get(),
                2
            );

        const unsigned char
            *payload =
                sqlite3_column_text(
                    statement.get(),
                    3
                );

        if (
            capturedAt == nullptr ||
            kind == nullptr ||
            payload == nullptr
        ) {
            lastError_ =
                "spool row contains null text";

            return SpoolStatus::
                storage_error;
        }

        records.push_back(
            TelemetryRecord{
                static_cast<
                    std::int64_t
                >(
                    sqlite3_column_int64(
                        statement.get(),
                        0
                    )
                ),
                reinterpret_cast<
                    const char *
                >(capturedAt),
                reinterpret_cast<
                    const char *
                >(kind),
                reinterpret_cast<
                    const char *
                >(payload)
            }
        );
    }

    return SpoolStatus::ok;
}

SpoolStatus
SQLiteSpool::acknowledgeThrough(
    std::int64_t sequence,
    std::size_t &removedCount
)
{
    if (sequence <= 0) {
        return SpoolStatus::
            invalid_argument;
    }

    std::lock_guard lock(
        mutex_
    );

    clearErrorLocked();

    removedCount = 0;

    if (
        !executeLocked(
            "BEGIN IMMEDIATE;"
        )
    ) {
        return SpoolStatus::
            storage_error;
    }

    sqlite3_stmt *rawStatement =
        nullptr;

    if (
        sqlite3_prepare_v2(
            database_,
            "DELETE FROM telemetry_spool "
            "WHERE sequence <= ?;",
            -1,
            &rawStatement,
            nullptr
        ) != SQLITE_OK
    ) {
        setStorageErrorLocked(
            "failed to prepare spool acknowledgement"
        );

        executeLocked(
            "ROLLBACK;"
        );

        return SpoolStatus::
            storage_error;
    }

    Statement statement(
        rawStatement
    );

    if (
        sqlite3_bind_int64(
            statement.get(),
            1,
            static_cast<
                sqlite3_int64
            >(sequence)
        ) != SQLITE_OK
    ) {
        setStorageErrorLocked(
            "failed to bind spool acknowledgement"
        );

        executeLocked(
            "ROLLBACK;"
        );

        return SpoolStatus::
            storage_error;
    }

    if (
        sqlite3_step(
            statement.get()
        ) != SQLITE_DONE
    ) {
        setStorageErrorLocked(
            "failed to execute spool acknowledgement"
        );

        executeLocked(
            "ROLLBACK;"
        );

        return SpoolStatus::
            storage_error;
    }

    const int changes =
        sqlite3_changes(
            database_
        );

    if (
        !executeLocked(
            "COMMIT;"
        )
    ) {
        executeLocked(
            "ROLLBACK;"
        );

        return SpoolStatus::
            storage_error;
    }

    removedCount =
        changes > 0
            ? static_cast<
                  std::size_t
              >(changes)
            : 0U;

    clearErrorLocked();

    return SpoolStatus::ok;
}

SpoolStatus SQLiteSpool::count(
    std::size_t &recordCount
)
{
    std::lock_guard lock(
        mutex_
    );

    clearErrorLocked();

    if (
        !countLocked(
            recordCount
        )
    ) {
        return SpoolStatus::
            storage_error;
    }

    return SpoolStatus::ok;
}

int SQLiteSpool::
currentSchemaVersion() const noexcept
{
    return currentSchemaVersion_;
}

std::size_t
SQLiteSpool::maxRecords() const noexcept
{
    return maxRecords_;
}

std::string SQLiteSpool::lastError() const
{
    std::lock_guard lock(
        mutex_
    );

    return lastError_;
}

bool SQLiteSpool::executeLocked(
    const char *sql
)
{
    char *errorMessage =
        nullptr;

    const int result =
        sqlite3_exec(
            database_,
            sql,
            nullptr,
            nullptr,
            &errorMessage
        );

    if (result == SQLITE_OK) {
        if (
            errorMessage !=
            nullptr
        ) {
            sqlite3_free(
                errorMessage
            );
        }

        return true;
    }

    if (
        errorMessage !=
        nullptr
    ) {
        lastError_ =
            errorMessage;

        sqlite3_free(
            errorMessage
        );
    } else {
        setStorageErrorLocked(
            "sqlite execution failed"
        );
    }

    return false;
}

bool SQLiteSpool::
readSchemaVersionLocked(
    int &version
)
{
    sqlite3_stmt *rawStatement =
        nullptr;

    if (
        sqlite3_prepare_v2(
            database_,
            "PRAGMA user_version;",
            -1,
            &rawStatement,
            nullptr
        ) != SQLITE_OK
    ) {
        setStorageErrorLocked(
            "failed to read spool schema version"
        );

        return false;
    }

    Statement statement(
        rawStatement
    );

    if (
        sqlite3_step(
            statement.get()
        ) != SQLITE_ROW
    ) {
        setStorageErrorLocked(
            "failed to read spool schema version"
        );

        return false;
    }

    version =
        sqlite3_column_int(
            statement.get(),
            0
        );

    return true;
}

bool SQLiteSpool::migrateLocked()
{
    int version = 0;

    if (
        !readSchemaVersionLocked(
            version
        )
    ) {
        return false;
    }

    if (
        version >
        schemaVersion
    ) {
        lastError_ =
            "spool schema is newer than this agent supports";

        return false;
    }

    if (version == 0) {
        if (
            !executeLocked(
                "BEGIN IMMEDIATE;"
            )
        ) {
            return false;
        }

        if (
            !executeLocked(
                "CREATE TABLE telemetry_spool("
                "sequence INTEGER PRIMARY KEY AUTOINCREMENT,"
                "captured_at_utc TEXT NOT NULL,"
                "kind TEXT NOT NULL "
                "CHECK(length(kind) BETWEEN 1 AND 64),"
                "payload TEXT NOT NULL"
                ");"
            ) ||
            !executeLocked(
                "PRAGMA user_version=1;"
            ) ||
            !executeLocked(
                "COMMIT;"
            )
        ) {
            executeLocked(
                "ROLLBACK;"
            );

            return false;
        }

        version = 1;
    }

    if (
        version !=
        schemaVersion
    ) {
        lastError_ =
            "unsupported spool schema version";

        return false;
    }

    currentSchemaVersion_ =
        version;

    clearErrorLocked();

    return true;
}

bool SQLiteSpool::countLocked(
    std::size_t &recordCount
)
{
    sqlite3_stmt *rawStatement =
        nullptr;

    if (
        sqlite3_prepare_v2(
            database_,
            "SELECT COUNT(*) "
            "FROM telemetry_spool;",
            -1,
            &rawStatement,
            nullptr
        ) != SQLITE_OK
    ) {
        setStorageErrorLocked(
            "failed to prepare spool count"
        );

        return false;
    }

    Statement statement(
        rawStatement
    );

    if (
        sqlite3_step(
            statement.get()
        ) != SQLITE_ROW
    ) {
        setStorageErrorLocked(
            "failed to execute spool count"
        );

        return false;
    }

    const sqlite3_int64
        countValue =
            sqlite3_column_int64(
                statement.get(),
                0
            );

    if (countValue < 0) {
        lastError_ =
            "sqlite returned a negative spool count";

        return false;
    }

    recordCount =
        static_cast<
            std::size_t
        >(countValue);

    return true;
}

void SQLiteSpool::
setStorageErrorLocked(
    const std::string &context
)
{
    lastError_ =
        context;

    if (
        database_ !=
        nullptr
    ) {
        lastError_ += ": ";
        lastError_ +=
            sqlite3_errmsg(
                database_
            );
    }
}

void SQLiteSpool::
clearErrorLocked()
{
    lastError_.clear();
}

}