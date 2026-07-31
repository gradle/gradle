#!/usr/bin/env python3
"""Prototype native (non-JVM) client for the in-daemon gRPC tooling API (Target beta).

Flow:
  1. Invoke `gradle --grpc-endpoint` (the bootstrap helper) to find-or-start a daemon
     and print its gRPC endpoint + token.
  2. Dial the daemon directly over gRPC and either:
       - run a build (RunBuild), streaming styled output, exiting with success/failure; or
       - query a model (QueryModel) when --query is given.

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
# The "IDE" vendor's own model schema. The client is built from this file, exactly like the
# daemon-side plugin is; that shared knowledge is what lets the client decode the Any.
IDE_PROTO = os.path.join(HERE, "ide_model.proto")

ENDPOINT_RE = re.compile(r"(127\.0\.0\.1:\d+) (\S+)\s*$")


def ensure_stubs():
    gen = os.path.join(tempfile.gettempdir(), "gradle_grpc_proto_gen")
    os.makedirs(gen, exist_ok=True)
    import grpc_tools
    from grpc_tools import protoc
    proto_dir = os.path.dirname(PROTO)
    # grpc_tools ships the well-known types (google/protobuf/*.proto) but does not add them to the
    # include path automatically when explicit -I dirs are passed; add its bundled _proto dir so
    # tooling.proto's `import "google/protobuf/any.proto"` resolves.
    wkt = os.path.join(os.path.dirname(grpc_tools.__file__), "_proto")
    rc = protoc.main([
        "protoc",
        "-I" + proto_dir,
        "-I" + HERE,
        "-I" + wkt,
        "--python_out=" + gen,
        "--grpc_python_out=" + gen,
        PROTO,
        IDE_PROTO,
    ])
    if rc != 0:
        raise SystemExit("protoc codegen failed")
    sys.path.insert(0, gen)


def get_endpoint(gradle_bin, project_dir):
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


def ansi_for(pb, style):
    table = {
        pb.STYLE_HEADER: "\033[1m",
        pb.STYLE_SUCCESS: "\033[32m",
        pb.STYLE_SUCCESS_HEADER: "\033[1;32m",
        pb.STYLE_FAILURE: "\033[31m",
        pb.STYLE_FAILURE_HEADER: "\033[1;31m",
        pb.STYLE_IDENTIFIER: "\033[36m",
        pb.STYLE_PROGRESS_STATUS: "\033[2m",
        pb.STYLE_INFO: "\033[34m",
        pb.STYLE_ERROR: "\033[31m",
    }
    return table.get(style, "")


def run_build(stub, pb, tasks, project_dir, token):
    request = pb.BuildRequest(args=tasks, project_dir=project_dir)
    metadata = [("x-gradle-daemon-token", token)]
    use_color = sys.stdout.isatty()
    print("[gRPC] RunBuild(args=%s)" % tasks, file=sys.stderr)

    success = False
    message = ""
    for event in stub.RunBuild(request, metadata=metadata):
        kind = event.WhichOneof("kind")
        if kind == "output":
            text = event.output.text
            sys.stdout.write(text if text.endswith("\n") else text + "\n")
        elif kind == "styled":
            for span in event.styled.spans:
                code = ansi_for(pb, span.style) if use_color else ""
                reset = "\033[0m" if code else ""
                sys.stdout.write(code + span.text + reset)
        elif kind == "progress":
            p = event.progress
            # render progress to stderr (dim) so it doesn't pollute build stdout
            if p.type == pb.PROGRESS_START and p.description:
                sys.stderr.write("\033[2m> %s\033[0m\n" % p.description)
        elif kind == "result":
            success = event.result.success
            message = event.result.message
        sys.stdout.flush()

    print("[result] %s (success=%s)" % (message, success), file=sys.stderr)
    return 0 if success else 1


def query_model(stub, pb, model, project_dir, token, project_path=""):
    metadata = [("x-gradle-daemon-token", token)]
    if model == "env":
        request = pb.ModelRequest(project_dir=project_dir, type=pb.MODEL_BUILD_ENVIRONMENT)
        response = stub.QueryModel(request, metadata=metadata)
        if not response.success:
            print("[query] failed: %s" % response.error, file=sys.stderr)
            return 1
        env = response.build_environment
        print("Build environment:")
        print("  gradle version: %s" % env.gradle_version)
        print("  java home:      %s" % env.java_home)
        print("  java version:   %s" % env.java_version)
        return 0
    if model == "project":
        # Query a plugin-contributed model by name. The response carries a google.protobuf.Any
        # whose schema Gradle does not know; we decode it with the vendor proto the client shares
        # with the daemon-side plugin.
        import ide_model_pb2 as ide
        # The client stays connected to the build root and names the target project by its logical
        # path (e.g. ":app"); the daemon resolves it via BuildTreeModelTarget.ofProject. An empty
        # project_path targets the default (root) project.
        request = pb.ModelRequest(project_dir=project_dir, model_name="com.example.ide.IdeProjectModel", project_path=project_path)
        response = stub.QueryModel(request, metadata=metadata)
        if not response.success:
            print("[query] failed: %s" % response.error, file=sys.stderr)
            return 1
        ide_model = ide.IdeProjectModel()
        if not response.model_any.Unpack(ide_model):
            print("[query] Any did not match IdeProjectModel (type_url=%s)" % response.model_any.type_url, file=sys.stderr)
            return 1
        print("IdeProjectModel (type_url=%s):" % response.model_any.type_url)
        print("  project path: %s" % ide_model.project_path)
        print("  project name: %s" % ide_model.project_name)
        print("  build dir:    %s" % ide_model.build_dir)
        print("  plugins:      %s" % ", ".join(ide_model.plugin_ids))
        return 0
    raise SystemExit("Unknown model: %s" % model)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--gradle", default=os.environ.get("GRADLE_BIN", "gradle"),
                        help="Path to the gradle launcher (or set GRADLE_BIN)")
    parser.add_argument("--project-dir", default=os.path.join(HERE, "sample"),
                        help="Build to run/query (default: bundled sample project)")
    parser.add_argument("--query", choices=["env", "project"], default=None,
                        help="Query a model instead of running a build")
    parser.add_argument("--target", choices=["root", "app", "lib"], default="root",
                        help="For --query project: which sample project to target (per-project model)")
    parser.add_argument("tasks", nargs="*", help="Tasks/flags to run (default: help)")
    # parse_known_args so build flags like -q, -x, -P, --info pass through to Gradle
    # instead of being claimed by the client's own argument parser.
    args, extra = parser.parse_known_args()

    ensure_stubs()
    import grpc
    import tooling_pb2 as pb
    import tooling_pb2_grpc as pb_grpc

    project_dir = os.path.abspath(args.project_dir)
    # Per-project targeting by logical path: stay connected to the build root and pass the target
    # project's path (":app"/":lib"). "root" targets the default project (empty path).
    project_path = "" if args.target == "root" else ":" + args.target

    print("[helper] gradle --grpc-endpoint ...", file=sys.stderr)
    endpoint, token = get_endpoint(args.gradle, project_dir)
    print("[helper] endpoint=%s token=%s..." % (endpoint, token[:8]), file=sys.stderr)

    channel = grpc.insecure_channel(endpoint)
    stub = pb_grpc.ToolingStub(channel)

    if args.query:
        sys.exit(query_model(stub, pb, args.query, project_dir, token, project_path))
    build_args = (args.tasks or []) + extra
    if not build_args:
        build_args = ["help"]
    sys.exit(run_build(stub, pb, build_args, project_dir, token))


if __name__ == "__main__":
    main()
