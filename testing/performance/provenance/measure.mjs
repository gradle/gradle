/* Copyright 2026 the original author or authors. SPDX-License-Identifier: Apache-2.0 */

import { spawn } from 'node:child_process';
import { copyFile, mkdir, readFile, writeFile } from 'node:fs/promises';
import { availableParallelism, platform, arch } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { performance } from 'node:perf_hooks';

const here = path.dirname(fileURLToPath(import.meta.url));
const modes = ['disabled', 'origins', 'locations'];

export function parseHistogram(text) {
    const total = text.match(/^Total\s+(\d+)\s+(\d+)\s*$/m);
    if (!total) throw new Error('Missing live histogram total');
    const provenance = [];
    for (const line of text.split('\n')) {
        const match = line.match(/^\s*\d+:\s+(\d+)\s+(\d+)\s+((?:\[L)?org\.gradle\.api\.internal\.provider\.provenance\.\S+)/);
        if (match) provenance.push({ name: match[3], instances: Number(match[1]), shallowBytes: Number(match[2]) });
    }
    return { liveInstances: Number(total[1]), liveBytes: Number(total[2]), provenance };
}

export function summarize(values) {
    const sorted = [...values].sort((a, b) => a - b);
    if (!sorted.length) return null;
    const middle = Math.floor(sorted.length / 2);
    return { n: sorted.length, median: sorted.length % 2 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2,
        min: sorted[0], max: sorted.at(-1) };
}

async function command(executable, args, cwd, log) {
    const started = performance.now();
    return new Promise((resolve, reject) => {
        const child = spawn(executable, args, { cwd, stdio: ['ignore', 'pipe', 'pipe'] });
        let stdout = '', stderr = '';
        child.stdout.on('data', data => { stdout += data; });
        child.stderr.on('data', data => { stderr += data; });
        child.on('error', reject);
        child.on('close', async code => {
            try {
                const wallMs = performance.now() - started;
                await writeFile(log, stdout + '\n--- stderr ---\n' + stderr);
                if (code !== 0) throw new Error(`Exit ${code}; see ${log}`);
                resolve({ stdout, wallMs });
            } catch (error) { reject(error); }
        });
    });
}

async function fixture(directory, projects) {
    await mkdir(path.join(directory, 'buildSrc/src/main/java/example'), { recursive: true });
    await copyFile(path.join(here, 'fixture/ModelPlugin.java'), path.join(directory, 'buildSrc/src/main/java/example/ModelPlugin.java'));
    await writeFile(path.join(directory, 'settings.gradle'), `rootProject.name = 'provenance-sample'\ninclude ${(Array.from({ length: projects }, (_, i) => `'p${i}'`)).join(', ')}\n`);
    await writeFile(path.join(directory, 'buildSrc/build.gradle'), `plugins { id 'java-gradle-plugin' }
gradlePlugin { plugins { model { id = 'example.model'; implementationClass = 'example.ModelPlugin' } } }
`);
    await writeFile(path.join(directory, 'build.gradle'), `subprojects { apply plugin: 'example.model' }
tasks.register('checkpoint') {
    dependsOn subprojects.collect { [it.tasks.named('modelCheckpoint'), it.tasks.named('assemble')] }
}
`);
    for (let i = 0; i < projects; i++) {
        const projectDir = path.join(directory, `p${i}`);
        await mkdir(path.join(projectDir, 'src/main/java/example'), { recursive: true });
        await writeFile(path.join(projectDir, 'build.gradle'), i ? `dependencies { implementation project(':p${i - 1}') }\n` : '');
        await writeFile(path.join(projectDir, 'src/main/java/example/Library.java'), 'package example; public class Library {}\n');
    }
}

async function main() {
    const options = { projects: '10,50', tasks: '40', warmups: '3', runs: '9', heapRuns: '3', coldRuns: '1' };
    for (let i = 2; i < process.argv.length; i += 2) {
        const key = process.argv[i].replace(/^--/, '');
        if (!['gradle', 'output', ...Object.keys(options)].includes(key) || !process.argv[i + 1]) throw new Error(`Invalid option ${process.argv[i]}`);
        options[key] = process.argv[i + 1];
    }
    if (!options.gradle || !options.output) throw new Error('Required: --gradle /path/to/bin/gradle --output /new/results/directory');
    const projects = options.projects.split(',').map(Number);
    for (const value of [...projects, ...['tasks', 'warmups', 'runs', 'heapRuns', 'coldRuns'].map(key => Number(options[key]))]) {
        if (!Number.isSafeInteger(value) || value < 1) throw new Error('Counts must be positive integers');
    }
    const output = path.resolve(options.output), gradle = path.resolve(options.gradle);
    // Deliberately refuse an existing directory, so results/fixtures cannot be overwritten.
    await mkdir(output);
    const logs = path.join(output, 'logs');
    await mkdir(logs);
    const userHome = path.join(output, 'gradle-user-home');
    const base = ['--gradle-user-home', userHome, '--console=plain', '--no-configuration-cache', '--no-build-cache',
        '--no-watch-fs', '--max-workers=2', '-Dorg.gradle.jvmargs=-Xms512m -Xmx512m -XX:+UseG1GC'];
    const samples = [];
    let sequence = 0;
    const stop = () => command(gradle, ['--gradle-user-home', userHome, '--stop'], output, path.join(logs, `${sequence++}-stop.log`));
    const version = await command(gradle, ['--version'], output, path.join(logs, 'version.log'));
    await writeFile(path.join(output, 'environment.json'), JSON.stringify({ options, gradle, platform: platform(), arch: arch(),
        cpus: availableParallelism(), version: version.stdout, javaToolOptionsPresent: Boolean(process.env.JAVA_TOOL_OPTIONS) }, null, 2));
    try {
        for (const count of projects) {
            const directory = path.join(output, `${count}-projects`);
            await fixture(directory, count);
            async function run(mode, phase, iteration) {
                const id = `${sequence++}-${count}-${phase}-${mode}-${iteration}`;
                const histogram = path.join(logs, `${id}.histogram`);
                const args = [...base, '-I', path.join(here, 'fixture/measure.init.gradle'),
                    `-PsampleTasks=${options.tasks}`, `-Dorg.gradle.internal.property-provenance=${mode !== 'disabled'}`,
                    `-Dorg.gradle.internal.property-provenance.locations=${mode === 'locations'}`, 'checkpoint'];
                if (phase === 'heap') args.push(`-PsampleHistogram=${histogram}`);
                const result = await command(gradle, args, directory, path.join(logs, `${id}.log`));
                const lines = result.stdout.split('\n').filter(line => line.startsWith('PROVENANCE_SAMPLE '));
                if (lines.length !== 1) throw new Error(`Expected one main-build checkpoint; see ${id}.log`);
                const sample = { projects: count, mode, phase, iteration, wallMs: result.wallMs, ...JSON.parse(lines[0].slice(18)) };
                if (phase === 'heap') Object.assign(sample, parseHistogram(await readFile(histogram, 'utf8')));
                samples.push(sample);
                await writeFile(path.join(output, 'samples.json'), JSON.stringify(samples, null, 2));
                console.log(`${count} projects ${phase} ${mode} ${iteration}: configuration ${sample.configurationMs.toFixed(1)} ms, wall ${sample.wallMs.toFixed(1)} ms${sample.liveBytes ? `, live ${(sample.liveBytes / 1048576).toFixed(2)} MiB` : ''}`);
            }
            for (const [phase, rounds] of [['warmup', options.warmups], ['timing', options.runs], ['heap', options.heapRuns]]) {
                for (let round = 0; round < Number(rounds); round++) {
                    // Rotate order to reduce systematic position/JIT/GC bias without random irreproducibility.
                    for (let offset = 0; offset < modes.length; offset++) await run(modes[(round + offset) % modes.length], phase, round);
                }
            }
            // Cold daemon, warm disk/script caches: not a fresh checkout or cold filesystem.
            for (let round = 0; round < Number(options.coldRuns); round++) {
                for (const mode of modes) { await stop(); await run(mode, 'cold-daemon', round); }
            }
            await stop();
        }
    } finally { await stop(); }
    const summary = [];
    for (const count of projects) for (const phase of ['timing', 'heap', 'cold-daemon']) for (const mode of modes) {
        const matching = samples.filter(sample => sample.projects === count && sample.mode === mode && sample.phase === phase);
        summary.push({ projects: count, phase, mode, configurationMs: summarize(matching.map(s => s.configurationMs)),
            wallMs: summarize(matching.map(s => s.wallMs)), liveBytes: summarize(matching.filter(s => s.liveBytes).map(s => s.liveBytes)) });
    }
    await writeFile(path.join(output, 'summary.json'), JSON.stringify(summary, null, 2));
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    main().catch(error => { console.error(error); process.exitCode = 1; });
}
