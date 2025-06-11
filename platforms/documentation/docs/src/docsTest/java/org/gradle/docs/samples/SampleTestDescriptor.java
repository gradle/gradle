/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.docs.samples;

import org.gradle.docs.samples.SamplesTestEngine.SamplesEngineExecutionContext;
import org.gradle.exemplar.model.Sample;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.engine.support.hierarchical.Node;

import static org.gradle.docs.samples.BucketClassTestDescriptor.BUCKET_CLASS_SEGMENT;
import static org.gradle.docs.samples.SamplesTestEngine.SAMPLES_TEST_ENGINE_UID;
import static org.gradle.docs.samples.SamplesTestEngine.getBucketClassName;

public class SampleTestDescriptor extends AbstractTestDescriptor implements Node<SamplesEngineExecutionContext> {
    public static final String SAMPLE_SEGMENT = "sample";
    private final Sample sample;
    private final IntegrationTestSamplesRunner samplesRunner;

    public static UniqueId getSampleUid(String sampleId) {
        String className = getBucketClassName(sampleId);
        return SAMPLES_TEST_ENGINE_UID.append(BUCKET_CLASS_SEGMENT, className).append(SAMPLE_SEGMENT, sampleId);
    }

    protected SampleTestDescriptor(Sample sample, IntegrationTestSamplesRunner samplesRunner) {
        super(getSampleUid(sample.getId()), sample.getId(), MethodSource.from(getBucketClassName(sample.getId()), sample.getId()));
        this.sample = sample;
        this.samplesRunner = samplesRunner;
    }

    @Override
    public Type getType() {
        return Type.TEST;
    }

    @Override
    public SamplesEngineExecutionContext execute(SamplesEngineExecutionContext context, DynamicTestExecutor dynamicTestExecutor) throws Exception {
        samplesRunner.runSample(sample);
        return context;
    }
}
