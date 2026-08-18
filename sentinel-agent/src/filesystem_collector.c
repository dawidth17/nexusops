#include "nexusops/sysprobe.h"

#include <stddef.h>
#include <stdint.h>
#include <sys/statvfs.h>

static int blocks_to_bytes(
    uintmax_t blocks,
    uint64_t block_size,
    uint64_t *bytes
)
{
    if (block_size == 0) {
        return 0;
    }

    if (
        blocks >
        UINT64_MAX / block_size
    ) {
        return 0;
    }

    *bytes =
        (uint64_t) blocks * block_size;

    return 1;
}

sysprobe_status sysprobe_read_filesystem_info(
    const char *path,
    sysprobe_filesystem_info *filesystem
)
{
    if (path == NULL || filesystem == NULL) {
        return SYSPROBE_ERROR_INVALID_ARGUMENT;
    }

    struct statvfs statistics;

    if (statvfs(path, &statistics) != 0) {
        return SYSPROBE_ERROR_IO;
    }

    uint64_t block_size =
        statistics.f_frsize != 0
            ? (uint64_t) statistics.f_frsize
            : (uint64_t) statistics.f_bsize;

    uint64_t total_bytes = 0;
    uint64_t free_bytes = 0;
    uint64_t available_bytes = 0;

    if (
        !blocks_to_bytes(
            (uintmax_t) statistics.f_blocks,
            block_size,
            &total_bytes
        ) ||
        !blocks_to_bytes(
            (uintmax_t) statistics.f_bfree,
            block_size,
            &free_bytes
        ) ||
        !blocks_to_bytes(
            (uintmax_t) statistics.f_bavail,
            block_size,
            &available_bytes
        )
    ) {
        return SYSPROBE_ERROR_PARSE;
    }

    if (
        free_bytes > total_bytes ||
        available_bytes > total_bytes
    ) {
        return SYSPROBE_ERROR_PARSE;
    }

    filesystem->total_bytes =
        total_bytes;

    filesystem->free_bytes =
        free_bytes;

    filesystem->available_bytes =
        available_bytes;

    filesystem->used_bytes =
        total_bytes - free_bytes;

    return SYSPROBE_OK;
}