#ifndef NEXUSOPS_SYSPROBE_H
#define NEXUSOPS_SYSPROBE_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define SYSPROBE_VERSION_MAJOR 0
#define SYSPROBE_VERSION_MINOR 2
#define SYSPROBE_VERSION_PATCH 0

typedef enum sysprobe_status {
    SYSPROBE_OK = 0,
    SYSPROBE_ERROR_INVALID_ARGUMENT = 1,
    SYSPROBE_ERROR_IO = 2,
    SYSPROBE_ERROR_PARSE = 3
} sysprobe_status;

typedef struct sysprobe_cpu_times {
    uint64_t user;
    uint64_t nice;
    uint64_t system;
    uint64_t idle;
    uint64_t iowait;
    uint64_t irq;
    uint64_t softirq;
    uint64_t steal;
} sysprobe_cpu_times;

typedef struct sysprobe_memory_info {
    uint64_t total_bytes;
    uint64_t available_bytes;
    uint64_t used_bytes;
} sysprobe_memory_info;

typedef struct sysprobe_filesystem_info {
    uint64_t total_bytes;
    uint64_t free_bytes;
    uint64_t available_bytes;
    uint64_t used_bytes;
} sysprobe_filesystem_info;

typedef struct sysprobe_uptime_info {
    double uptime_seconds;
} sysprobe_uptime_info;

int sysprobe_version_major(void);
int sysprobe_version_minor(void);
int sysprobe_version_patch(void);

sysprobe_status sysprobe_read_cpu_times(
    sysprobe_cpu_times *times
);

sysprobe_status sysprobe_calculate_cpu_usage_percent(
    const sysprobe_cpu_times *previous,
    const sysprobe_cpu_times *current,
    double *usage_percent
);

sysprobe_status sysprobe_read_memory_info(
    sysprobe_memory_info *memory
);

sysprobe_status sysprobe_read_filesystem_info(
    const char *path,
    sysprobe_filesystem_info *filesystem
);

sysprobe_status sysprobe_read_uptime(
    sysprobe_uptime_info *uptime
);

#ifdef __cplusplus
}
#endif

#endif