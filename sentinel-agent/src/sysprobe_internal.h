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

#ifdef __cplusplus
}
#endif

#endif