/* Copyright 2026 the original author or authors. SPDX-License-Identifier: Apache-2.0 */

import assert from 'node:assert/strict';
import test from 'node:test';
import { orderForFork, report, validateConfig, validateHeap } from './measure-production.mjs';

test('three forks balance each mode across each block position', () => {
    assert.deepEqual([0, 1, 2].map(orderForFork), [
        ['baseline', 'disabled', 'origins'], ['disabled', 'origins', 'baseline'], ['origins', 'baseline', 'disabled']
    ]);
});

test('configuration requires pinned and separate source worktrees', () => {
    const config = { forks: 3, warmups: 8, runs: 8, heapRuns: 2, sourceRevision: 'a'.repeat(40), args: ['help'], jvmArgs: '-Xmx3g',
        variants: Object.fromEntries(['baseline', 'disabled', 'origins'].map(mode => [mode, { project: `/work/${mode.padEnd(8, '_')}`, gradle: '/dist/gradle' }])) };
    validateConfig(config);
    assert.throws(() => validateConfig({ ...config, sourceRevision: 'HEAD' }), /sourceRevision/);
    assert.throws(() => validateConfig({ ...config, warmups: 0 }), /positive integer/);
    config.variants.origins.project = config.variants.disabled.project;
    assert.throws(() => validateConfig(config), /separate project worktrees/);
});

test('heap negative controls distinguish absent, disabled, and enabled provenance', () => {
    const initialized = { name: 'org.gradle.api.internal.provider.provenance.PropertyProvenanceOrigin', instances: 1 };
    const state = { name: 'org.gradle.api.internal.provider.provenance.PropertyProvenanceState', instances: 100 };
    validateHeap('baseline', { provenance: [] });
    validateHeap('disabled', { provenance: [initialized] });
    validateHeap('origins', { provenance: [state] });
    assert.throws(() => validateHeap('baseline', { provenance: [initialized] }), /unexpectedly/);
    assert.throws(() => validateHeap('disabled', { provenance: [state] }), /retained/);
    assert.throws(() => validateHeap('origins', { provenance: [] }), /no provenance states/);
});

test('heap controls recognize inline provenance without standalone state objects', () => {
    for (const name of ['NonFinalizedValueWithProvenance', 'FinalizedValueWithProvenance']) {
        const heap = { provenance: [], valueStates: [{ name: `org.gradle.api.internal.provider.ValueState$${name}`, instances: 10 }] };
        validateHeap('origins', heap);
        assert.throws(() => validateHeap('disabled', heap), /retained/);
        assert.throws(() => validateHeap('baseline', heap), /unexpectedly/);
    }
    assert.throws(() => validateHeap('disabled', { provenance: [
        { name: 'org.gradle.api.internal.provider.provenance.PropertyProvenanceState$Detached', instances: 1 }
    ] }), /retained/);
});

test('state layout summaries expose the disabled state as well as inline enabled storage', () => {
    const name = 'org.gradle.api.internal.provider.ValueState$NonFinalizedValueWithProvenance';
    const origins = report([{ mode: 'origins', phase: 'heap', liveBytes: 1000,
        valueStates: [{ name, instances: 10, shallowBytes: 320 }] }])[2];
    assert.equal(origins.valueStateLayouts[0].bytesPerInstance.median, 32);
    assert.equal(origins.propertyLayouts.length, 0);
});

test('summaries keep independent daemon repetitions separate and exclude warmup and heap timing', () => {
    const samples = [
        { mode: 'baseline', fork: 0, phase: 'warmup', configurationMs: 999, wallMs: 999 },
        { mode: 'baseline', fork: 0, phase: 'timing', configurationMs: 10, wallMs: 20 },
        { mode: 'baseline', fork: 1, phase: 'timing', configurationMs: 30, wallMs: 40 },
        { mode: 'baseline', fork: 1, phase: 'heap', configurationMs: 999, wallMs: 999, liveBytes: 1000 }
    ];
    const baseline = report(samples)[0];
    assert.equal(baseline.configurationMs.median, 20);
    assert.equal(baseline.configurationMs.n, 2);
    assert.equal(baseline.forks[0].configurationMs.median, 10);
    assert.equal(baseline.forks[1].configurationMs.median, 30);
    assert.equal(baseline.liveBytes.median, 1000);
});

test('property layout summary separates instance count from per-instance size', () => {
    const name = 'org.gradle.api.internal.provider.DefaultProperty';
    const baseline = report([
        { mode: 'baseline', fork: 0, phase: 'heap', liveBytes: 1000,
            properties: [{ name, instances: 10, shallowBytes: 400 }] },
        { mode: 'baseline', fork: 1, phase: 'heap', liveBytes: 1100,
            properties: [{ name, instances: 12, shallowBytes: 480 }] }
    ])[0];
    assert.equal(baseline.propertyLayouts[0].instances.median, 11);
    assert.equal(baseline.propertyLayouts[0].bytesPerInstance.median, 40);
});
