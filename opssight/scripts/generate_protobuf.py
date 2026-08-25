from __future__ import annotations

import argparse
import importlib.util
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()

    parser.add_argument(
        "--contract-root",
        type=Path,
        required=True,
    )

    parser.add_argument(
        "--output-root",
        type=Path,
        required=True,
    )

    return parser.parse_args()


def find_grpc_include() -> Path:
    grpc_tools_spec = importlib.util.find_spec(
        "grpc_tools"
    )

    if (
        grpc_tools_spec is None
        or grpc_tools_spec.submodule_search_locations
        is None
    ):
        raise RuntimeError(
            "grpc_tools package not found"
        )

    package_directory = Path(
        next(
            iter(
                grpc_tools_spec.submodule_search_locations
            )
        )
    )

    grpc_include = (
        package_directory
        / "_proto"
    )

    if not grpc_include.is_dir():
        raise RuntimeError(
            "grpc_tools protobuf include directory not found"
        )

    return grpc_include


def main() -> int:
    args = parse_args()

    contract_root = args.contract_root.resolve()
    output_root = args.output_root.resolve()

    source_proto = (
        contract_root
        / "nexusops"
        / "telemetry"
        / "v1"
        / "telemetry.proto"
    )

    if not source_proto.is_file():
        raise FileNotFoundError(
            f"telemetry contract not found: {source_proto}"
        )

    grpc_include = find_grpc_include()

    generated_package = (
        Path("opssight")
        / "generated"
        / "nexusops"
        / "telemetry"
        / "v1"
    )

    with tempfile.TemporaryDirectory(
        prefix="nexusops-protobuf-"
    ) as temporary_directory:
        temporary_root = Path(
            temporary_directory
        )

        staged_proto = (
            temporary_root
            / generated_package
            / "telemetry.proto"
        )

        staged_proto.parent.mkdir(
            parents=True,
            exist_ok=True,
        )

        shutil.copyfile(
            source_proto,
            staged_proto,
        )

        output_root.mkdir(
            parents=True,
            exist_ok=True,
        )

        command = [
            sys.executable,
            "-m",
            "grpc_tools.protoc",
            f"-I{temporary_root}",
            f"-I{grpc_include}",
            f"--python_out={output_root}",
            f"--pyi_out={output_root}",
            f"--grpc_python_out={output_root}",
            str(staged_proto),
        ]

        subprocess.run(
            command,
            check=True,
        )

    package_directories = [
        output_root / "opssight",
        output_root
        / "opssight"
        / "generated",
        output_root
        / "opssight"
        / "generated"
        / "nexusops",
        output_root
        / "opssight"
        / "generated"
        / "nexusops"
        / "telemetry",
        output_root
        / "opssight"
        / "generated"
        / "nexusops"
        / "telemetry"
        / "v1",
    ]

    for directory in package_directories:
        directory.mkdir(
            parents=True,
            exist_ok=True,
        )

        (
            directory
            / "__init__.py"
        ).touch()

    print(
        "generated telemetry protobuf Python modules"
    )

    return 0


if __name__ == "__main__":
    raise SystemExit(
        main()
    )