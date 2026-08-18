#ifndef NEXUSOPS_SYSPROBE_INTERNAL_H
#define NEXUSOPS_SYSPROBE_INTERNAL_H

#include "nexusops/sysprobe.h"

#ifdef __cplusplus
extern "C" {
#endif

sysprobe_status sysprobe_read_cpu_times_from_path(
    const char *path,
    sysprobe_cpu_times *times
);

sysprobe_status sysprobe_read_memory_info_from_path(
    const char *path,
    sysprobe_memory_info *memory
);

sysprobe_status sysprobe_read_uptime_from_path(
    const char *path,
    sysprobe_uptime_info *uptime
);

sysprobe_status sysprobe_read_network_counters_from_path(
    const char *path,
    sysprobe_network_interface *interfaces,
    size_t capacity,
    size_t *count
);

sysprobe_status sysprobe_read_process_status_from_path(
    const char *path,
    sysprobe_process_info *process
);

sysprobe_status sysprobe_read_processes_from_root(
    const char *proc_root,
    sysprobe_process_info *processes,
    size_t capacity,
    size_t *count
);

#ifdef __cplusplus
}
#endif

#endif