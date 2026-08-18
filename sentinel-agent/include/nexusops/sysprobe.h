#ifndef NEXUSOPS_SYSPROBE_H
#define NEXUSOPS_SYSPROBE_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define SYSPROBE_VERSION_MAJOR 0
#define SYSPROBE_VERSION_MINOR 3
#define SYSPROBE_VERSION_PATCH 0

#define SYSPROBE_NETWORK_NAME_MAX 64
#define SYSPROBE_IPV4_TEXT_MAX 16
#define SYSPROBE_IPV6_TEXT_MAX 46
#define SYSPROBE_PROCESS_NAME_MAX 256

typedef enum sysprobe_status {
    SYSPROBE_OK = 0,
    SYSPROBE_ERROR_INVALID_ARGUMENT = 1,
    SYSPROBE_ERROR_IO = 2,
    SYSPROBE_ERROR_PARSE = 3,
    SYSPROBE_ERROR_BUFFER_TOO_SMALL = 4
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

typedef struct sysprobe_network_interface {
    char name[SYSPROBE_NETWORK_NAME_MAX];

    char ipv4_address[SYSPROBE_IPV4_TEXT_MAX];
    char ipv6_address[SYSPROBE_IPV6_TEXT_MAX];

    uint64_t rx_bytes;
    uint64_t rx_packets;
    uint64_t rx_errors;
    uint64_t rx_dropped;

    uint64_t tx_bytes;
    uint64_t tx_packets;
    uint64_t tx_errors;
    uint64_t tx_dropped;
} sysprobe_network_interface;

typedef struct sysprobe_process_info {
    uint32_t pid;
    uint32_t parent_pid;

    char state;
    char name[SYSPROBE_PROCESS_NAME_MAX];

    uint64_t resident_memory_bytes;
} sysprobe_process_info;

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

sysprobe_status sysprobe_read_network_interfaces(
    sysprobe_network_interface *interfaces,
    size_t capacity,
    size_t *count
);

sysprobe_status sysprobe_read_processes(
    sysprobe_process_info *processes,
    size_t capacity,
    size_t *count
);

#ifdef __cplusplus
}
#endif

#endif