#!/usr/bin/env python3
# Copyright 2026 Gradle and contributors.
# Licensed under the Apache License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0).
"""Bounded matched D1 smoke: isolated user homes/daemons, rotated variants, warm compilation separately."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import tempfile
import time
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--baseline-zip', required=True, type=Path)
parser.add_argument('--candidate-zip', required=True, type=Path)
parser.add_argument('--output', required=True, type=Path)
args = parser.parse_args()
source = Path(__file__).resolve().parent
repo = source.parents[2]
options = '-Xms256m -Xmx256m -XX:+UseSerialGC'
variants = ['baseline', 'disabled', 'origins']
results = {
    'gradleHead': subprocess.check_output(['git', '-C', repo, 'rev-parse', 'HEAD'], text=True).strip(),
    'baselineZipSha256': hashlib.sha256(args.baseline_zip.read_bytes()).hexdigest(),
    'candidateZipSha256': hashlib.sha256(args.candidate_zip.read_bytes()).hexdigest(),
    'workloadHashes': {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((source / 'smoke').glob('*.gradle'))},
    'runnerSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
    'java': subprocess.run(['java', '-version'], text=True, capture_output=True, check=True).stderr,
    'platform': platform.platform(),
    'daemonJvmArgs': options,
    'forks': 2,
    'warmupsPerDaemon': 3,
    'measurementsPerDaemon': 3,
    'tasks': ['help', 'verifyProvenanceSmoke'],
    'note': 'Client wall time, including startup/communication. Per-daemon summaries only; not a production regression threshold test.',
    'runs': [],
}
with tempfile.TemporaryDirectory(prefix='provenance-d1-smoke-') as temporary:
    root = Path(temporary)
    for fork in range(2):
        for variant in variants[fork:] + variants[:fork]:
            # Slot names and layouts have equal lengths; every daemon gets its own home and script caches.
            slot = root / f'fork{fork}' / f'slot{variants.index(variant)}'
            slot.mkdir(parents=True)
            home = slot / 'home'
            work = slot / 'work'
            shutil.copytree(source / 'smoke', work)
            archive_path = args.baseline_zip if variant == 'baseline' else args.candidate_zip
            with zipfile.ZipFile(archive_path) as archive:
                archive.extractall(slot / 'distribution')
            launcher = next((slot / 'distribution').glob('*/bin/gradle'))
            env = dict(os.environ, GRADLE_USER_HOME=str(home))
            base_command = ['bash', str(launcher), '-p', str(work), '--console=plain', '--max-workers=2', '-Dorg.gradle.jvmargs=' + options]
            command = base_command + ['--no-configuration-cache', '--no-build-cache',
                                      '-Dorg.gradle.internal.property-provenance=' + ('true' if variant == 'origins' else 'false'),
                                      'help', 'verifyProvenanceSmoke']
            run = {'variant': variant, 'fork': fork, 'warmupSeconds': [], 'measurementSeconds': [], 'outputSha256': []}
            try:
                for iteration in range(6):
                    started = time.monotonic()
                    process = subprocess.run(command, env=env, text=True, capture_output=True, timeout=120)
                    elapsed = time.monotonic() - started
                    output = process.stdout + process.stderr
                    if process.returncode != 0 or 'provenance-smoke=13000' not in output:
                        raise RuntimeError(f'{variant} smoke failed:\n{output}')
                    if 'Failure trace to source' in output or 'Configuration trace to source' in output:
                        raise AssertionError('Successful smoke build emitted an unrequested diagnostic report')
                    run['warmupSeconds' if iteration < 3 else 'measurementSeconds'].append(elapsed)
                    run['outputSha256'].append(hashlib.sha256(output.encode()).hexdigest())
                run['daemonLogs'] = [p.name for p in home.glob('daemon/*/daemon-*.out.log')]
                results['runs'].append(run)
                print(f'{variant} fork {fork}: measurements {run["measurementSeconds"]}', flush=True)
            finally:
                subprocess.run(base_command + ['--stop'], env=env, text=True, capture_output=True, timeout=30)
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(json.dumps(results, indent=2) + '\n')
print(args.output)
