#!/usr/bin/env python3
"""Cross-version endpoint discovery for the native gRPC tooling client.

Given only a project directory, work out which Gradle version it targets and hand back a gRPC endpoint
that can serve it, without the caller choosing a mode, a version, or an endpoint:

  - a released version (read from the project's gradle-wrapper.properties) has no in-daemon gRPC
    server, so it is served through the cross-version bridge - find-or-start one for that version;
  - no wrapper version means "use the direct in-daemon path" via the `gradle --grpc-endpoint` helper.

Running bridges are tracked in a small file-based registry so repeat calls reuse them and different
versions get their own bridge. The registry is the language-neutral "index sidecar" a non-JVM client
can read directly, rather than the JVM daemon registry.
"""
import json
import os
import re
import socket
import subprocess
import time

_WRAPPER_RE = re.compile(r'gradle-([0-9]+(?:\.[0-9]+)*)-(?:bin|all)\.zip')


def read_wrapper_version(project_dir):
    """The Gradle version a project targets, parsed from its gradle-wrapper.properties, or None."""
    props = os.path.join(project_dir, "gradle", "wrapper", "gradle-wrapper.properties")
    if not os.path.exists(props):
        return None
    match = _WRAPPER_RE.search(open(props).read())
    return match.group(1) if match else None


def registry_dir():
    path = os.environ.get("GRADLE_GRPC_BRIDGE_HOME") or os.path.expanduser("~/.gradle-grpc-tapi/bridges")
    os.makedirs(path, exist_ok=True)
    return path


def _alive(pid):
    try:
        os.kill(pid, 0)
        return True
    except OSError:
        return False


def _reachable(endpoint):
    host, _, port = endpoint.partition(":")
    try:
        with socket.create_connection((host, int(port)), timeout=1):
            return True
    except OSError:
        return False


def find_or_start_bridge(version, bridge_bin, log=lambda m: None):
    """Reuse a live bridge for `version` from the registry, or start one and register it."""
    entry = os.path.join(registry_dir(), version + ".json")
    if os.path.exists(entry):
        info = json.load(open(entry))
        if _alive(info["pid"]) and _reachable(info["endpoint"]):
            log("reusing bridge for Gradle %s at %s (pid %d)" % (version, info["endpoint"], info["pid"]))
            return info["endpoint"]
        os.remove(entry)  # stale registration

    if not bridge_bin or not os.path.exists(bridge_bin):
        raise SystemExit("no bridge launcher found (set BRIDGE_BIN or build prototype/bridge); "
                         "cannot serve Gradle %s" % version)

    log("starting a bridge for Gradle %s ..." % version)
    out_path = os.path.join(registry_dir(), version + ".out")
    proc = subprocess.Popen([bridge_bin, "--gradle-version", version, "--port", "0"],
                            stdout=open(out_path, "w"), stderr=subprocess.DEVNULL)
    endpoint = None
    for _ in range(240):
        if proc.poll() is not None:
            break
        for line in open(out_path):
            m = re.match(r'BRIDGE_ENDPOINT (\S+)', line.strip())
            if m:
                endpoint = m.group(1)
                break
        if endpoint:
            break
        time.sleep(0.5)
    if not endpoint:
        raise SystemExit("bridge for Gradle %s failed to report an endpoint" % version)

    json.dump({"version": version, "endpoint": endpoint, "pid": proc.pid}, open(entry, "w"))
    log("started bridge for Gradle %s at %s (pid %d)" % (version, endpoint, proc.pid))
    return endpoint


def default_bridge_bin(here):
    """The installed bridge launcher, from BRIDGE_BIN or the standard build output location."""
    return os.environ.get("BRIDGE_BIN") or os.path.join(
        here, "bridge", "build", "install", "grpc-tapi-bridge", "bin", "grpc-tapi-bridge")


def discover(project_dir, gradle_bin, get_endpoint, here, log=lambda m: None):
    """Return (endpoint, token, mode, version) for the given project."""
    version = read_wrapper_version(project_dir)
    if version:
        endpoint = find_or_start_bridge(version, default_bridge_bin(here), log)
        return endpoint, "", "bridged", version
    endpoint, token = get_endpoint(gradle_bin, project_dir)
    return endpoint, token, "direct", None
