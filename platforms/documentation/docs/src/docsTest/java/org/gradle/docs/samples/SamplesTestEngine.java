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

import org.gradle.docs.samples.bucket.Buckets;
import org.gradle.exemplar.model.Sample;
import org.junit.platform.engine.EngineDiscoveryRequest;
import org.junit.platform.engine.ExecutionRequest;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.discovery.ClassSelector;
import org.junit.platform.engine.discovery.UniqueIdSelector;
import org.junit.platform.engine.support.descriptor.EngineDescriptor;
import org.junit.platform.engine.support.hierarchical.EngineExecutionContext;
import org.junit.platform.engine.support.hierarchical.HierarchicalTestEngine;
import org.junit.runners.model.InitializationError;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * We have thousands of {@link Sample}s to test. To enable parallel execution, we implement a custom {@link org.junit.platform.engine.TestEngine}
 * that distributes these samples into {@link Buckets#BUCKET_NUMBER} buckets. Since all infrastructures (like Gradle test execution and TC Parallel Tests)
 * are class-based, we create empty "carrier" classes to enable parallel test execution.
 *
 * The discovered test descriptors are as follows:
 *
 * <pre>
 *                              [Root (engine descriptor)]
 *                             /             |              \
 *                    [Bucket1 class]  [Bucket2 class] ... [BucketN class]
 *                    /     |     \    /    |    \         /     |      \
 *              [sample1] [sample2] ... [sample3] ... [sample4] ... [sampleN]
 * </pre>
 *
 * A sample is mapped into bucket N by simply {@code int bucketNumber = Math.abs(sampleId.hashCode()) % Buckets.BUCKET_NUMBER + 1;}
 */
public class SamplesTestEngine extends HierarchicalTestEngine<SamplesTestEngine.SamplesEngineExecutionContext> {
    public static class SamplesEngineExecutionContext implements EngineExecutionContext {
    }

    public final static String SAMPLES_TEST_ENGINE_ID = "samples-test-engine";
    public final static UniqueId SAMPLES_TEST_ENGINE_UID = UniqueId.forEngine(SAMPLES_TEST_ENGINE_ID);
    private final IntegrationTestSamplesRunner samplesRunner;

    public SamplesTestEngine() throws InitializationError {
        this.samplesRunner = new IntegrationTestSamplesRunner(Buckets.class);
    }

    public static String getBucketClassName(String sampleId) {
        int bucketNumber = Math.abs(sampleId.hashCode()) % Buckets.BUCKET_NUMBER + 1;
        return String.format("org.gradle.docs.samples.bucket.Bucket%s", bucketNumber);
    }

    @Override
    public String getId() {
        return SAMPLES_TEST_ENGINE_ID;
    }

    private boolean selectedByClass(Sample sample, Set<String> classNames) {
        return classNames.contains(getBucketClassName(sample.getId()));
    }

    private boolean selectedByUniqueId(Sample sample, Set<UniqueId> uids) {
        String className = getBucketClassName(sample.getId());
        UniqueId classUid = BucketClassTestDescriptor.getClassUid(className);
        UniqueId sampleUid = SampleTestDescriptor.getSampleUid(sample.getId());
        return uids.contains(classUid) || uids.contains(sampleUid);
    }

    private List<Sample> determineSamplesToBeRun(EngineDiscoveryRequest discoveryRequest) {
        Set<String> classNames = discoveryRequest.getSelectorsByType(ClassSelector.class).stream()
            .map(ClassSelector::getClassName).collect(Collectors.toSet());
        Set<UniqueId> uniqueIds = discoveryRequest.getSelectorsByType(UniqueIdSelector.class).stream()
            .map(UniqueIdSelector::getUniqueId)
            .collect(Collectors.toSet());
        List<Sample> allSamples = samplesRunner.getAllSamples();

        List<Sample> result = new ArrayList<>();
        for (Sample sample : allSamples) {
            if (selectedByClass(sample, classNames) || selectedByUniqueId(sample, uniqueIds)) {
                result.add(sample);
            }
        }
        return result;
    }

    @Override
    public TestDescriptor discover(EngineDiscoveryRequest discoveryRequest, UniqueId uniqueId) {
        try {
            List<Sample> samplesToBeRun = determineSamplesToBeRun(discoveryRequest);

            Set<String> sampleBucketClassesToBeRun = samplesToBeRun.stream()
                .map(sample -> getBucketClassName(sample.getId()))
                .collect(Collectors.toSet());

            TestDescriptor engineDescriptor = new EngineDescriptor(SAMPLES_TEST_ENGINE_UID, SAMPLES_TEST_ENGINE_ID);

            Map<String, TestDescriptor> classNameToEngineDescriptor = new HashMap<>();
            for (String className : sampleBucketClassesToBeRun) {
                TestDescriptor classDescriptor = new BucketClassTestDescriptor(Class.forName(className));
                classNameToEngineDescriptor.put(className, classDescriptor);
                engineDescriptor.addChild(classDescriptor);
                classDescriptor.setParent(engineDescriptor);
            }

            for (Sample sample : samplesToBeRun) {
                String className = getBucketClassName(sample.getId());
                TestDescriptor classDescriptor = classNameToEngineDescriptor.get(className);
                TestDescriptor sampleDescriptor = new SampleTestDescriptor(sample, samplesRunner);
                classDescriptor.addChild(sampleDescriptor);
                sampleDescriptor.setParent(classDescriptor);
            }
            return engineDescriptor;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected SamplesEngineExecutionContext createExecutionContext(ExecutionRequest request) {
        return new SamplesEngineExecutionContext();
    }
}
