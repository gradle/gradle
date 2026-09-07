#!/usr/bin/env python3
# Copyright 2026 Gradle and contributors.
# Licensed under the Apache License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0).
"""Run the bounded S2 factory, attribution and mixed-read probes against the S0 distribution."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import subprocess
import tempfile
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--baseline-zip', required=True, type=Path)
parser.add_argument('--output', required=True, type=Path)
args = parser.parse_args()
repo = Path(__file__).resolve().parents[3]
source = Path(__file__).resolve().parent
main = repo / 'platforms/core-configuration/model-core/build/classes/java/main'
core = repo / 'subprojects/core/build/classes/java/main'
if not (main / 'org/gradle/api/internal/provenance/UpdateSequence.class').exists():
    parser.error('Run ./gradlew :model-core:compileJava first.')


def run(command):
    result = subprocess.run([str(part) for part in command], text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError(f'{command[0]} failed:\n{result.stdout}\n{result.stderr}')
    return result.stdout


jvm_options = ['-Xms256m', '-Xmx256m', '-XX:+UseSerialGC']
results = {
    'gradleHead': run(['git', '-C', repo, 'rev-parse', 'HEAD']).strip(),
    'baselineZipSha256': hashlib.sha256(args.baseline_zip.read_bytes()).hexdigest(),
    'platform': platform.platform(),
    'machine': platform.machine(),
    'java': subprocess.run(['java', '-version'], text=True, capture_output=True, check=True).stderr,
    'jvmOptions': jvm_options,
    'warmupBatches': 5,
    'measurementBatches': 5,
    'forks': 3,
    'registrySourceSha256': hashlib.sha256((repo / 'subprojects/core/src/main/java/org/gradle/configuration/PropertyProvenanceRegistry.java').read_bytes()).hexdigest(),
    'sourceHashes': {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(source.glob('*.java'))},
    'metadataSourceHashes': {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((repo / 'platforms/core-configuration/model-core/src/main/java/org/gradle/api/internal/provenance').glob('*.java'))},
    'runs': [],
}
with tempfile.TemporaryDirectory(prefix='shared-provenance-probe-') as temporary:
    work = Path(temporary)
    with zipfile.ZipFile(args.baseline_zip) as archive:
        for member in archive.infolist():
            if member.filename.endswith('.jar'):
                archive.extract(member, work / 'distribution')
    # API compatibility stubs deliberately throw Error; they must not shadow runtime implementations.
    jars = sorted(p for p in (work / 'distribution').rglob('*.jar') if p.parent.name != 'api')
    baseline_classpath = os.pathsep.join(str(p) for p in jars)
    model_core_jar = next(p for p in jars if p.name.startswith('gradle-model-core-'))
    results['runtimeClassHashes'] = {}
    with zipfile.ZipFile(model_core_jar) as runtime:
        for name in ['DefaultProperty', 'AbstractProperty', 'ValueState', 'ValueState$NonFinalizedValue', 'ValueState$FinalizedValue']:
            path = 'org/gradle/api/internal/provider/' + name + '.class'
            results['runtimeClassHashes'][name] = {
                'baseline': hashlib.sha256(runtime.read(path)).hexdigest(),
                'compiledIn': hashlib.sha256((main / path).read_bytes()).hexdigest(),
            }
    baseline_classes = work / 'baseline-probe'
    baseline_classes.mkdir()
    run(['javac', '-cp', baseline_classpath, '-d', baseline_classes, source / 'ProvenanceAllocationProbe.java', source / 'AttributionBaselineProbe.java'])
    manifest = work / 'manifest.mf'
    manifest.write_text('Premain-Class: ProvenanceAllocationProbe\n\n')
    agent = work / 'probe-agent.jar'
    run(['jar', 'cfm', agent, manifest, '-C', baseline_classes, '.'])
    metadata_classes = work / 'metadata-probe'
    metadata_classes.mkdir()
    new_classpath = os.pathsep.join([str(main), str(core), str(baseline_classes), baseline_classpath])
    run(['javac', '-cp', new_classpath, '-d', metadata_classes, source / 'AttributionEnabledProbe.java'])
    # Rotation avoids giving every variant the same machine-load position; JVMs never share JIT state.
    variants = ['baseline', 'compiled-in', 'enabled']
    for fork in range(3):
        for variant in variants[fork:] + variants[:fork]:
            classpath = os.pathsep.join([str(baseline_classes), baseline_classpath]) if variant == 'baseline' else new_classpath
            entry = 'AttributionBaselineProbe'
            if variant == 'enabled':
                classpath = os.pathsep.join([str(metadata_classes), classpath])
                entry = 'AttributionEnabledProbe'
            output = run(['java', *jvm_options, '-javaagent:' + str(agent), '-cp', classpath, entry])
            results['runs'].append({'variant': variant, 'fork': fork, 'samples': [json.loads(line) for line in output.splitlines()]})
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(json.dumps(results, indent=2) + '\n')
print(args.output)
