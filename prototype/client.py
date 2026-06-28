#!/usr/bin/env python3
"""Prototype native (non-JVM) client for the in-daemon gRPC tooling API (Target beta).

Flow:
  1. Invoke `gradle --grpc-endpoint` (the bootstrap helper) to find-or-start a daemon
     and print its gRPC endpoint + token.
  2. Dial the daemon directly over gRPC and stream a build (RunBuild), printing output
     and exiting with the build's success/failure.

No JVM and no Tooling API involved on the client side - only gRPC + protobuf.
"""
import argparse
import os
import re
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
PROTO = os.path.join(REPO, "platforms", "core-runtime", "tooling-api-grpc", "src", "main", "proto", "tooling.proto")

ENDPOINT_RE = re.compile(r"(127\.0\.0\.1:\d+) (\S+)\s*$")


def ensure_stubs():
    """Generate the Python protobuf/gRPC stubs from the shared .proto and import them."""
    gen = os.path.join(tempfile.gettempdir(), "gradle_grpc_proto_gen")
    os.makedirs(gen, exist_ok=True)
    from grpc_tools import protoc
    proto_dir = os.path.dirname(PROTO)
    rc = protoc.main([
        "protoc",
        "-I" + proto_dir,
        "--python_out=" + gen,
        "--grpc_python_out=" + gen,
        PROTO,
    ])
    if rc != 0:
        raise SystemExit("protoc codegen failed")
    sys.path.insert(0, gen)


def get_endpoint(gradle_bin, project_dir):
    """Run the bootstrap helper and parse its '127.0.0.1:<port> <token>' line."""
    proc = subprocess.run(
        [gradle_bin, "--grpc-endpoint"],
        cwd=project_dir,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )
    for line in proc.stdout.splitlines():
        m = ENDPOINT_RE.search(line.strip())
        if m:
            return m.group(1), m.group(2)
    sys.stderr.write(proc.stdout)
    raise SystemExit("Could not find a gRPC endpoint in `gradle --grpc-endpoint` output")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--gradle", default=os.environ.get("GRADLE_BIN", "gradle"),
                        help="Path to the gradle launcher (or set GRADLE_BIN)")
    parser.add_argument("--project-dir", default=os.path.join(HERE, "sample"),
                        help="Build to run (default: bundled sample project)")
    parser.add_argument("tasks", nargs="*", help="Tasks to run (default: help)")
    args = parser.parse_args()

    ensure_stubs()
    import grpc
    import tooling_pb2
    import tooling_pb2_grpc

    project_dir = os.path.abspath(args.project_dir)
    tasks = args.tasks if args.tasks else ["help"]

    print("[helper] gradle --grpc-endpoint ...", file=sys.stderr)
    endpoint, token = get_endpoint(args.gradle, project_dir)
    print("[helper] endpoint=%s token=%s..." % (endpoint, token[:8]), file=sys.stderr)

    channel = grpc.insecure_channel(endpoint)
    stub = tooling_pb2_grpc.ToolingStub(channel)
    request = tooling_pb2.BuildRequest(args=tasks, project_dir=project_dir)
    metadata = [("x-gradle-daemon-token", token)]

    print("[gRPC] RunBuild(args=%s)" % tasks, file=sys.stderr)
    success = False
    message = ""
    for event in stub.RunBuild(request, metadata=metadata):
        kind = event.WhichOneof("kind")
        if kind == "output":
            text = event.output.text
            sys.stdout.write(text if text.endswith("\n") else text + "\n")
            sys.stdout.flush()
        elif kind == "result":
            success = event.result.success
            message = event.result.message

    print("[result] %s (success=%s)" % (message, success), file=sys.stderr)
    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()
