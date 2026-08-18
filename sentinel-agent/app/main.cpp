#include "nexusops/sysprobe.h"

#include <iostream>

int main()
{
    std::cout
        << "SentinelAgent using libsysprobe "
        << sysprobe_version_major()
        << '.'
        << sysprobe_version_minor()
        << '.'
        << sysprobe_version_patch()
        << '\n';

    return 0;
}