#include "nexusops/sysprobe.h"
#include "sysprobe_internal.h"

#include <ctype.h>
#include <errno.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>

sysprobe_status sysprobe_read_uptime_from_path(
    const char *path,
    sysprobe_uptime_info *uptime
)
{
    if (path == NULL || uptime == NULL) {
        return SYSPROBE_ERROR_INVALID_ARGUMENT;
    }

    FILE *file = fopen(path, "r");

    if (file == NULL) {
        return SYSPROBE_ERROR_IO;
    }

    char line[256];

    if (fgets(line, sizeof(line), file) == NULL) {
        sysprobe_status status =
            ferror(file)
                ? SYSPROBE_ERROR_IO
                : SYSPROBE_ERROR_PARSE;

        fclose(file);

        return status;
    }

    fclose(file);

    errno = 0;

    char *end = NULL;

    double seconds =
        strtod(
            line,
            &end
        );

    if (
        end == line ||
        errno == ERANGE ||
        !isfinite(seconds) ||
        seconds < 0.0
    ) {
        return SYSPROBE_ERROR_PARSE;
    }

    if (
        *end != '\0' &&
        !isspace((unsigned char) *end)
    ) {
        return SYSPROBE_ERROR_PARSE;
    }

    uptime->uptime_seconds =
        seconds;

    return SYSPROBE_OK;
}

sysprobe_status sysprobe_read_uptime(
    sysprobe_uptime_info *uptime
)
{
    return sysprobe_read_uptime_from_path(
        "/proc/uptime",
        uptime
    );
}