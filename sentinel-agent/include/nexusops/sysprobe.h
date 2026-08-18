#ifndef NEXUSOPS_SYSPROBE_H
#define NEXUSOPS_SYSPROBE_H

#ifdef __cplusplus
extern "C" {
#endif

#define SYSPROBE_VERSION_MAJOR 0
#define SYSPROBE_VERSION_MINOR 1
#define SYSPROBE_VERSION_PATCH 0

typedef enum sysprobe_status {
    SYSPROBE_OK = 0,
    SYSPROBE_ERROR_INVALID_ARGUMENT = 1,
    SYSPROBE_ERROR_IO = 2,
    SYSPROBE_ERROR_PARSE = 3
} sysprobe_status;

int sysprobe_version_major(void);
int sysprobe_version_minor(void);
int sysprobe_version_patch(void);

#ifdef __cplusplus
}
#endif

#endif