#ifndef NEXUSOPS_TEST_PATHS_H
#define NEXUSOPS_TEST_PATHS_H

#include <string>
#include <string_view>

namespace nexusops::test {

inline std::string fixturePath(
    std::string_view fileName
)
{
    return std::string(
        SYSPROBE_TEST_FIXTURE_DIR
    ) +
        "/" +
        std::string(fileName);
}

}

#endif