#include "nexusops/sysprobe.h"
#include "sysprobe_internal.h"

#include <ctype.h>
#include <dirent.h>
#include <errno.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static int is_numeric_name(
    const char *name
)
{
    if (
        name == NULL ||
        *name == '\0'
    ) {
        return 0;
    }

    for (
        const unsigned char *current =
            (const unsigned char *) name;
        *current != '\0';
        ++current
    ) {
        if (!isdigit(*current)) {
            return 0;
        }
    }

    return 1;
}

static int parse_uint32_value(
    const char *text,
    uint32_t *value
)
{
    if (
        text == NULL ||
        value == NULL
    ) {
        return 0;
    }

    while (
        isspace(
            (unsigned char) *text
        )
    ) {
        ++text;
    }

    errno = 0;

    char *end = NULL;

    unsigned long parsed =
        strtoul(
            text,
            &end,
            10
        );

    if (
        end == text ||
        errno == ERANGE ||
        parsed > UINT32_MAX
    ) {
        return 0;
    }

    while (
        isspace(
            (unsigned char) *end
        )
    ) {
        ++end;
    }

    if (*end != '\0') {
        return 0;
    }

    *value =
        (uint32_t) parsed;

    return 1;
}

static int parse_kib_bytes(
    const char *text,
    uint64_t *bytes
)
{
    if (
        text == NULL ||
        bytes == NULL
    ) {
        return 0;
    }

    while (
        isspace(
            (unsigned char) *text
        )
    ) {
        ++text;
    }

    errno = 0;

    char *end = NULL;

    unsigned long long kib =
        strtoull(
            text,
            &end,
            10
        );

    if (
        end == text ||
        errno == ERANGE
    ) {
        return 0;
    }

    while (
        isspace(
            (unsigned char) *end
        )
    ) {
        ++end;
    }

    if (
        end[0] != 'k' ||
        end[1] != 'B'
    ) {
        return 0;
    }

    end += 2;

    while (
        isspace(
            (unsigned char) *end
        )
    ) {
        ++end;
    }

    if (*end != '\0') {
        return 0;
    }

    if (
        kib >
        UINT64_MAX / 1024U
    ) {
        return 0;
    }

    *bytes =
        (uint64_t) kib * 1024U;

    return 1;
}

static int copy_trimmed_value(
    const char *text,
    char *destination,
    size_t destination_size
)
{
    if (
        text == NULL ||
        destination == NULL ||
        destination_size == 0
    ) {
        return 0;
    }

    while (
        isspace(
            (unsigned char) *text
        )
    ) {
        ++text;
    }

    const char *end =
        text + strlen(text);

    while (
        end > text &&
        isspace(
            (unsigned char) end[-1]
        )
    ) {
        --end;
    }

    size_t length =
        (size_t) (end - text);

    if (
        length == 0 ||
        length >= destination_size
    ) {
        return 0;
    }

    memcpy(
        destination,
        text,
        length
    );

    destination[length] =
        '\0';

    return 1;
}

sysprobe_status sysprobe_read_process_status_from_path(
    const char *path,
    sysprobe_process_info *process
)
{
    if (
        path == NULL ||
        process == NULL
    ) {
        return SYSPROBE_ERROR_INVALID_ARGUMENT;
    }

    FILE *file =
        fopen(path, "r");

    if (file == NULL) {
        return SYSPROBE_ERROR_IO;
    }

    sysprobe_process_info parsed = {0};

    int found_name = 0;
    int found_state = 0;
    int found_pid = 0;
    int found_parent_pid = 0;

    char line[1024];

    while (
        fgets(
            line,
            sizeof(line),
            file
        ) != NULL
    ) {
        if (
            strncmp(
                line,
                "Name:",
                5
            ) == 0
        ) {
            if (
                !copy_trimmed_value(
                    line + 5,
                    parsed.name,
                    sizeof(parsed.name)
                )
            ) {
                fclose(file);

                return SYSPROBE_ERROR_PARSE;
            }

            found_name = 1;

            continue;
        }

        if (
            strncmp(
                line,
                "State:",
                6
            ) == 0
        ) {
            const char *value =
                line + 6;

            while (
                isspace(
                    (unsigned char) *value
                )
            ) {
                ++value;
            }

            if (*value == '\0') {
                fclose(file);

                return SYSPROBE_ERROR_PARSE;
            }

            parsed.state =
                *value;

            found_state = 1;

            continue;
        }

        if (
            strncmp(
                line,
                "Pid:",
                4
            ) == 0
        ) {
            if (
                !parse_uint32_value(
                    line + 4,
                    &parsed.pid
                )
            ) {
                fclose(file);

                return SYSPROBE_ERROR_PARSE;
            }

            found_pid = 1;

            continue;
        }

        if (
            strncmp(
                line,
                "PPid:",
                5
            ) == 0
        ) {
            if (
                !parse_uint32_value(
                    line + 5,
                    &parsed.parent_pid
                )
            ) {
                fclose(file);

                return SYSPROBE_ERROR_PARSE;
            }

            found_parent_pid = 1;

            continue;
        }

        if (
            strncmp(
                line,
                "VmRSS:",
                6
            ) == 0
        ) {
            if (
                !parse_kib_bytes(
                    line + 6,
                    &parsed.resident_memory_bytes
                )
            ) {
                fclose(file);

                return SYSPROBE_ERROR_PARSE;
            }
        }
    }

    if (ferror(file)) {
        fclose(file);

        return SYSPROBE_ERROR_IO;
    }

    fclose(file);

    if (
        !found_name ||
        !found_state ||
        !found_pid ||
        !found_parent_pid
    ) {
        return SYSPROBE_ERROR_PARSE;
    }

    *process =
        parsed;

    return SYSPROBE_OK;
}

static int compare_processes_by_pid(
    const void *left,
    const void *right
)
{
    const sysprobe_process_info *left_process =
        (const sysprobe_process_info *) left;

    const sysprobe_process_info *right_process =
        (const sysprobe_process_info *) right;

    if (
        left_process->pid <
        right_process->pid
    ) {
        return -1;
    }

    if (
        left_process->pid >
        right_process->pid
    ) {
        return 1;
    }

    return 0;
}

sysprobe_status sysprobe_read_processes_from_root(
    const char *proc_root,
    sysprobe_process_info *processes,
    size_t capacity,
    size_t *count
)
{
    if (
        proc_root == NULL ||
        count == NULL ||
        (processes == NULL && capacity != 0)
    ) {
        return SYSPROBE_ERROR_INVALID_ARGUMENT;
    }

    DIR *directory =
        opendir(proc_root);

    if (directory == NULL) {
        return SYSPROBE_ERROR_IO;
    }

    size_t total = 0;
    int directory_error = 0;

    for (;;) {
        errno = 0;

        struct dirent *entry =
            readdir(directory);

        if (entry == NULL) {
            if (errno != 0) {
                directory_error = 1;
            }

            break;
        }

        if (
            !is_numeric_name(
                entry->d_name
            )
        ) {
            continue;
        }

        uint32_t directory_pid = 0;

        if (
            !parse_uint32_value(
                entry->d_name,
                &directory_pid
            )
        ) {
            continue;
        }

        char status_path[4096];

        int written =
            snprintf(
                status_path,
                sizeof(status_path),
                "%s/%s/status",
                proc_root,
                entry->d_name
            );

        if (
            written < 0 ||
            (size_t) written >=
                sizeof(status_path)
        ) {
            continue;
        }

        sysprobe_process_info process;

        sysprobe_status status =
            sysprobe_read_process_status_from_path(
                status_path,
                &process
            );

        if (status != SYSPROBE_OK) {
            continue;
        }

        if (
            process.pid !=
            directory_pid
        ) {
            continue;
        }

        if (
            processes != NULL &&
            total < capacity
        ) {
            processes[total] =
                process;
        }

        ++total;
    }

    closedir(directory);

    if (directory_error) {
        return SYSPROBE_ERROR_IO;
    }

    size_t stored =
        total < capacity
            ? total
            : capacity;

    if (
        processes != NULL &&
        stored > 1
    ) {
        qsort(
            processes,
            stored,
            sizeof(*processes),
            compare_processes_by_pid
        );
    }

    *count = total;

    if (
        processes != NULL &&
        total > capacity
    ) {
        return SYSPROBE_ERROR_BUFFER_TOO_SMALL;
    }

    return SYSPROBE_OK;
}

sysprobe_status sysprobe_read_processes(
    sysprobe_process_info *processes,
    size_t capacity,
    size_t *count
)
{
    return sysprobe_read_processes_from_root(
        "/proc",
        processes,
        capacity,
        count
    );
}