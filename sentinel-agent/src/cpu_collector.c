#include "nexusops/sysprobe.h"
#include "sysprobe_internal.h"

#include <stdint.h>
#include <stdio.h>
#include <string.h>

static sysprobe_status parse_cpu_line(
    const char *line,
    sysprobe_cpu_times *times
)
{
    unsigned long long user = 0;
    unsigned long long nice = 0;
    unsigned long long system = 0;
    unsigned long long idle = 0;
    unsigned long long iowait = 0;
    unsigned long long irq = 0;
    unsigned long long softirq = 0;
    unsigned long long steal = 0;

    int parsed = sscanf(
        line,
        "cpu %llu %llu %llu %llu %llu %llu %llu %llu",
        &user,
        &nice,
        &system,
        &idle,
        &iowait,
        &irq,
        &softirq,
        &steal
    );

    if (parsed != 8) {
        return SYSPROBE_ERROR_PARSE;
    }

    times->user = (uint64_t) user;
    times->nice = (uint64_t) nice;
    times->system = (uint64_t) system;
    times->idle = (uint64_t) idle;
    times->iowait = (uint64_t) iowait;
    times->irq = (uint64_t) irq;
    times->softirq = (uint64_t) softirq;
    times->steal = (uint64_t) steal;

    return SYSPROBE_OK;
}

static int add_without_overflow(
    uint64_t left,
    uint64_t right,
    uint64_t *result
)
{
    if (UINT64_MAX - left < right) {
        return 0;
    }

    *result = left + right;

    return 1;
}

static int calculate_total(
    const sysprobe_cpu_times *times,
    uint64_t *total
)
{
    const uint64_t values[] = {
        times->user,
        times->nice,
        times->system,
        times->idle,
        times->iowait,
        times->irq,
        times->softirq,
        times->steal
    };

    uint64_t sum = 0;

    for (
        unsigned int index = 0;
        index < sizeof(values) / sizeof(values[0]);
        ++index
    ) {
        if (!add_without_overflow(
                sum,
                values[index],
                &sum
            )) {
            return 0;
        }
    }

    *total = sum;

    return 1;
}

sysprobe_status sysprobe_read_cpu_times_from_path(
    const char *path,
    sysprobe_cpu_times *times
)
{
    if (path == NULL || times == NULL) {
        return SYSPROBE_ERROR_INVALID_ARGUMENT;
    }

    FILE *file = fopen(path, "r");

    if (file == NULL) {
        return SYSPROBE_ERROR_IO;
    }

    char line[512];

    while (fgets(line, sizeof(line), file) != NULL) {
        if (strncmp(line, "cpu ", 4) == 0) {
            sysprobe_status status =
                parse_cpu_line(
                    line,
                    times
                );

            fclose(file);

            return status;
        }
    }

    if (ferror(file)) {
        fclose(file);

        return SYSPROBE_ERROR_IO;
    }

    fclose(file);

    return SYSPROBE_ERROR_PARSE;
}

sysprobe_status sysprobe_read_cpu_times(
    sysprobe_cpu_times *times
)
{
    return sysprobe_read_cpu_times_from_path(
        "/proc/stat",
        times
    );
}

sysprobe_status sysprobe_calculate_cpu_usage_percent(
    const sysprobe_cpu_times *previous,
    const sysprobe_cpu_times *current,
    double *usage_percent
)
{
    if (
        previous == NULL ||
        current == NULL ||
        usage_percent == NULL
    ) {
        return SYSPROBE_ERROR_INVALID_ARGUMENT;
    }

    uint64_t previous_total = 0;
    uint64_t current_total = 0;
    uint64_t previous_idle = 0;
    uint64_t current_idle = 0;

    if (
        !calculate_total(previous, &previous_total) ||
        !calculate_total(current, &current_total)
    ) {
        return SYSPROBE_ERROR_INVALID_ARGUMENT;
    }

    if (
        !add_without_overflow(
            previous->idle,
            previous->iowait,
            &previous_idle
        ) ||
        !add_without_overflow(
            current->idle,
            current->iowait,
            &current_idle
        )
    ) {
        return SYSPROBE_ERROR_INVALID_ARGUMENT;
    }

    if (current_total <= previous_total) {
        return SYSPROBE_ERROR_INVALID_ARGUMENT;
    }

    uint64_t total_delta =
        current_total - previous_total;

    uint64_t idle_delta = 0;

    if (current_idle >= previous_idle) {
        idle_delta =
            current_idle - previous_idle;
    }

    if (idle_delta > total_delta) {
        idle_delta = total_delta;
    }

    *usage_percent =
        ((double) (total_delta - idle_delta) /
         (double) total_delta) *
        100.0;

    return SYSPROBE_OK;
}