/* Copyright 2026 the original author or authors. SPDX-License-Identifier: Apache-2.0 */

import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { availableParallelism, platform, arch } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { command, parseHistogram, summarize } from './measure.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const modes = ['baseline', 'disabled', 'origins'];

export function orderForFork(fork) {
    return modes.map((_, offset) => modes[(fork + offset) % modes.length]);
}

export function validateConfig(config) {
    for (const key of ['forks', 'warmups', 'runs', 'heapRuns']) {
        if (!Number.isSafeInteger(config[key]) || config[key] < 1) throw new Error(`${key} must be a positive integer`);
    }
    if (!/^[a-f0-9]{40}$/.test(config.sourceRevision)) throw new Error('Pin the full sourceRevision');
    if (!Array.isArray(config.args) || !config.args.every(arg => typeof arg === 'string')) throw new Error('args must be a string array');
    if (!config.jvmArgs) throw new Error('Specify identical jvmArgs for all modes');
    const projects = new Set();
    for (const mode of modes) {
        const variant = config.variants?.[mode];
        if (!variant || !path.isAbsolute(variant.project) || !path.isAbsolute(variant.gradle)) throw new Error(`Absolute project/gradle paths required for ${mode}`);
        projects.add(variant.project);
    }
    if (projects.size !== modes.length) throw new Error('Use separate project worktrees to avoid build-logic recompilation on mode switches');
    if (new Set([...projects].map(project => project.length)).size !== 1) throw new Error('Use equal-length project paths to control retained path strings');
}

export function validateHeap(mode, heap) {
    const states = heap.provenance.find(entry => entry.name.endsWith('.PropertyProvenanceState'))?.instances ?? 0;
    if (mode === 'baseline' && heap.provenance.length) throw new Error('Baseline unexpectedly contains provenance metadata');
    if (mode === 'disabled' && states) throw new Error('Disabled run retained provenance states');
    if (mode === 'origins' && !states) throw new Error('Enabled run has no provenance states; verify distribution and workload');
}

export function report(samples) {
    return modes.map(mode => {
        const matching = samples.filter(sample => sample.mode === mode);
        const timing = matching.filter(sample => sample.phase === 'timing');
        const heap = matching.filter(sample => sample.phase === 'heap');
        const forks = [...new Set(timing.map(sample => sample.fork))];
        const propertyNames = [...new Set(heap.flatMap(sample => (sample.properties ?? []).map(entry => entry.name)))].sort();
        const propertyLayouts = propertyNames.map(name => {
            const entries = heap.map(sample => (sample.properties ?? []).filter(entry => entry.name === name))
                .filter(entries => entries.length).map(entries => ({
                    instances: entries.reduce((sum, entry) => sum + entry.instances, 0),
                    shallowBytes: entries.reduce((sum, entry) => sum + entry.shallowBytes, 0)
                }));
            return { name, instances: summarize(entries.map(entry => entry.instances)),
                bytesPerInstance: summarize(entries.map(entry => entry.shallowBytes / entry.instances)) };
        });
        return { mode, configurationMs: summarize(timing.map(s => s.configurationMs)), wallMs: summarize(timing.map(s => s.wallMs)),
            liveBytes: summarize(heap.map(s => s.liveBytes)), propertyLayouts, forks: forks.map(fork => {
                const timed = timing.filter(s => s.fork === fork), retained = heap.filter(s => s.fork === fork);
                return { fork, configurationMs: summarize(timed.map(s => s.configurationMs)), wallMs: summarize(timed.map(s => s.wallMs)),
                    liveBytes: summarize(retained.map(s => s.liveBytes)) };
            }) };
    });
}

async function main() {
    if (process.argv.length !== 6 || process.argv[2] !== '--config' || process.argv[4] !== '--output') {
        throw new Error('Usage: node measure-production.mjs --config /path/config.json --output /new/results/directory');
    }
    const config = JSON.parse(await readFile(process.argv[3], 'utf8'));
    validateConfig(config);
    const output = path.resolve(process.argv[5]);
    await mkdir(output); // Refuse to overwrite any existing result set.
    const logs = path.join(output, 'logs');
    await mkdir(logs);
    const env = { ...process.env };
    if (config.readOnlyCache) env.GRADLE_RO_DEP_CACHE = config.readOnlyCache;
    const manifest = { config, platform: platform(), arch: arch(), cpus: availableParallelism(), variants: {} };
    for (const mode of modes) {
        const variant = config.variants[mode];
        const revision = await command('git', ['rev-parse', 'HEAD'], variant.project, path.join(logs, `${mode}-revision.log`));
        const status = await command('git', ['status', '--porcelain', '--untracked-files=no'], variant.project, path.join(logs, `${mode}-status.log`));
        if (revision.stdout.trim() !== config.sourceRevision || status.stdout.trim()) throw new Error(`${mode} source differs from the pinned clean revision`);
        const version = await command(variant.gradle, ['--version'], variant.project, path.join(logs, `${mode}-version.log`), env);
        manifest.variants[mode] = { revision: revision.stdout.trim(), version: version.stdout };
    }
    await writeFile(path.join(output, 'manifest.json'), JSON.stringify(manifest, null, 2));
    const samples = [];
    let sequence = 0;
    const home = mode => path.join(output, `${mode.padEnd(8, '_')}-user-home`);
    const stop = mode => command(config.variants[mode].gradle, ['--gradle-user-home', home(mode), '--stop'], output,
        path.join(logs, `${sequence++}-${mode}-stop.log`), env);

    async function run(mode, fork, phase, iteration) {
        const variant = config.variants[mode];
        const id = `${sequence++}-${mode}-${fork}-${phase}-${iteration}`;
        const histogram = path.join(logs, `${id}.histogram`);
        const args = [...config.args, '--gradle-user-home', home(mode), '--console=plain', '--no-scan',
            '--no-configuration-cache', '--no-build-cache', '--no-watch-fs', '--max-workers=2',
            '-Dorg.gradle.isolated-projects=false', `-Dorg.gradle.jvmargs=${config.jvmArgs}`,
            `-Dorg.gradle.internal.property-provenance=${mode === 'origins'}`, '-Dorg.gradle.internal.property-provenance.locations=false',
            '-I', path.join(here, 'fixture/measure.init.gradle')];
        if (phase === 'heap') args.push(`-PsampleHistogram=${histogram}`);
        const result = await command(variant.gradle, args, variant.project, path.join(logs, `${id}.log`), env);
        const lines = result.stdout.split('\n').filter(line => line.startsWith('PROVENANCE_SAMPLE '));
        if (lines.length !== 1) throw new Error(`Expected exactly one main-build checkpoint; see ${id}.log`);
        const sample = { mode, fork, phase, iteration, wallMs: result.wallMs, ...JSON.parse(lines[0].slice(18)) };
        if (phase === 'heap') {
            Object.assign(sample, parseHistogram(await readFile(histogram, 'utf8')));
            validateHeap(mode, sample);
        }
        if (phase === 'timing' || phase === 'heap') {
            // Build-logic compilation must finish during preparation/warmup, not contaminate measurements.
            const compilations = result.stdout.split('\n').filter(line => /^> Task .*:(compile\w+|generatePrecompiledScriptPluginAccessors)(?:\s|$)/.test(line));
            if (compilations.some(line => !/ (UP-TO-DATE|NO-SOURCE|SKIPPED)$/.test(line))) throw new Error(`Build logic executed during measurement; see ${id}.log`);
        }
        samples.push(sample);
        await writeFile(path.join(output, 'samples.json'), JSON.stringify(samples, null, 2));
        await writeFile(path.join(output, 'summary.json'), JSON.stringify(report(samples), null, 2));
        console.log(`${mode} fork ${fork} ${phase} ${iteration}: configuration ${sample.configurationMs.toFixed(1)} ms, wall ${sample.wallMs.toFixed(1)} ms${sample.liveBytes ? `, live ${(sample.liveBytes / 1048576).toFixed(2)} MiB` : ''}`);
    }

    // Prime separate distribution caches and source worktrees before independent daemon repetitions.
    // Only one measurement daemon is live at a time; do not stop the user's normal Gradle daemons.
    for (const mode of modes) {
        try { await run(mode, -1, 'prepare', 0); } finally { await stop(mode); }
    }
    const pids = new Set();
    for (let fork = 0; fork < config.forks; fork++) {
        for (const mode of orderForFork(fork)) {
            try {
                for (const [phase, count] of [['warmup', config.warmups], ['timing', config.runs], ['heap', config.heapRuns]]) {
                    for (let iteration = 0; iteration < count; iteration++) await run(mode, fork, phase, iteration);
                }
                const block = samples.filter(sample => sample.mode === mode && sample.fork === fork);
                const blockPids = new Set(block.map(sample => sample.pid));
                if (blockPids.size !== 1 || pids.has(block[0].pid)) throw new Error('Expected one fresh daemon per fork/mode block');
                pids.add(block[0].pid);
            } finally { await stop(mode); }
        }
    }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    main().catch(error => { console.error(error); process.exitCode = 1; });
}
