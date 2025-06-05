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
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.engine.support.hierarchical.Node;

public class SampleTestDescriptor extends AbstractTestDescriptor implements Node<SamplesEngineExecutionContext> {
    private final Sample sample;
    private final IntegrationTestSamplesRunner samplesRunner;

    protected SampleTestDescriptor(TestDescriptor parent, Sample sample, IntegrationTestSamplesRunner samplesRunner) {
        super(parent.getUniqueId().append("sample", sample.getId()), sample.getId(),
            MethodSource.from(((BucketClassTestDescriptor) parent).getClassName(), sample.getId()));
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
