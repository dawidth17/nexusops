#include "nexusops/sysprobe.h"
#include "sysprobe_internal.h"

#include <stdint.h>
#include <stdio.h>
#include <string.h>

static sysprobe_status parse_kib_value(
    const char *value_text,
    uint64_t *bytes
)
{
    unsigned long long kib = 0;
    char unit[3] = {0};

    int parsed = sscanf(
        value_text,
        " %llu %2s",
        &kib,
        unit
    );

    if (
        parsed != 2 ||
        strcmp(unit, "kB") != 0
    ) {
        return SYSPROBE_ERROR_PARSE;
    }

    if (
        (uint64_t) kib >
        UINT64_MAX / 1024U
    ) {
        return SYSPROBE_ERROR_PARSE;
    }

    *bytes =
        (uint64_t) kib * 1024U;

    return SYSPROBE_OK;
}

sysprobe_status sysprobe_read_memory_info_from_path(
    const char *path,
    sysprobe_memory_info *memory
)
{
    if (path == NULL || memory == NULL) {
        return SYSPROBE_ERROR_INVALID_ARGUMENT;
    }

    FILE *file = fopen(path, "r");

    if (file == NULL) {
        return SYSPROBE_ERROR_IO;
    }

    int found_total = 0;
    int found_available = 0;

    uint64_t total_bytes = 0;
    uint64_t available_bytes = 0;

    char line[512];

    while (fgets(line, sizeof(line), file) != NULL) {
        if (
            strncmp(
                line,
                "MemTotal:",
                strlen("MemTotal:")
            ) == 0
        ) {
            sysprobe_status status =
                parse_kib_value(
                    line + strlen("MemTotal:"),
                    &total_bytes
                );

            if (status != SYSPROBE_OK) {
                fclose(file);

                return status;
            }

            found_total = 1;
        }

        if (
            strncmp(
                line,
                "MemAvailable:",
                strlen("MemAvailable:")
            ) == 0
        ) {
            sysprobe_status status =
                parse_kib_value(
                    line + strlen("MemAvailable:"),
                    &available_bytes
                );

            if (status != SYSPROBE_OK) {
                fclose(file);

                return status;
            }

            found_available = 1;
        }
    }

    if (ferror(file)) {
        fclose(file);

        return SYSPROBE_ERROR_IO;
    }

    fclose(file);

    if (!found_total || !found_available) {
        return SYSPROBE_ERROR_PARSE;
    }

    if (available_bytes > total_bytes) {
        return SYSPROBE_ERROR_PARSE;
    }

    memory->total_bytes =
        total_bytes;

    memory->available_bytes =
        available_bytes;

    memory->used_bytes =
        total_bytes - available_bytes;

    return SYSPROBE_OK;
}

sysprobe_status sysprobe_read_memory_info(
    sysprobe_memory_info *memory
)
{
    return sysprobe_read_memory_info_from_path(
        "/proc/meminfo",
        memory
    );
}