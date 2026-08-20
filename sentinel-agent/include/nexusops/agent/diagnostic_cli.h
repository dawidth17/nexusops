#ifndef NEXUSOPS_DIAGNOSTIC_CLI_H
#define NEXUSOPS_DIAGNOSTIC_CLI_H

#include <optional>

namespace nexusops::agent {

[[nodiscard]]
std::optional<int>
tryRunDiagnosticCommand(
    int argc,
    char **argv
);

}

#endif