/* Copyright 2026 the original author or authors. SPDX-License-Identifier: Apache-2.0 */

import assert from 'node:assert/strict';
import test from 'node:test';
import { parseHistogram, summarize } from './measure.mjs';

test('live totals and provenance shallow sizes are kept distinct', () => {
    const result = parseHistogram(` num     #instances         #bytes  class name (module)
   1:            10            240  org.gradle.api.internal.provider.provenance.PropertyProvenanceState
   2:            20            320  java.lang.Object (java.base@25)
   3:             1             32  [Lorg.gradle.api.internal.provider.provenance.PropertyProvenanceRecord;
Total            31            592
`);
    assert.equal(result.liveBytes, 592);
    assert.equal(result.liveInstances, 31);
    assert.equal(result.provenance[0].shallowBytes, 240);
    assert.equal(result.provenance[0].instances, 10);
    assert.equal(result.provenance[1].shallowBytes, 32);
    assert.throws(() => parseHistogram('Attach failed'), /Missing live histogram total/);
});

test('summary reports sample count and spread without implying significance', () => {
    assert.deepEqual(summarize([8, 2, 4]), { n: 3, median: 4, min: 2, max: 8 });
    assert.deepEqual(summarize([8, 2, 4, 6]), { n: 4, median: 5, min: 2, max: 8 });
    assert.equal(summarize([]), null);
});

test('property layout classes remain separate from provenance metadata', () => {
    const result = parseHistogram(`
 1: 10 560 org.gradle.api.internal.provider.DefaultProperty
 2: 5 320 org.gradle.api.internal.file.DefaultFilePropertyFactory$DefaultDirectoryVar
 3: 3 96 org.gradle.api.internal.provider.DefaultProvider
Total 18 976
`);
    assert.equal(result.properties.length, 2);
    assert.equal(result.properties[0].shallowBytes / result.properties[0].instances, 56);
    assert.equal(result.provenance.length, 0);
});
