/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.testing.pipelinesplit;

import org.gradle.api.specs.Spec;
import org.gradle.api.testing.detection.TestClassProcessor;
import org.gradle.api.testing.execution.Pipeline;
import org.gradle.api.testing.execution.PipelineConfig;
import org.gradle.api.testing.execution.PipelinesManager;
import org.gradle.api.testing.fabric.TestClassRunInfo;
import org.gradle.api.testing.pipelinesplit.policies.SplitPolicy;
import org.gradle.api.testing.pipelinesplit.policies.SplitPolicyConfig;
import org.gradle.api.testing.pipelinesplit.policies.SplitPolicyInstance;
import org.gradle.api.testing.pipelinesplit.policies.SplitPolicyRegister;
import org.gradle.listener.AsyncProxy;

import java.util.*;

/**
 * @author Tom Eyckmans
 */
public class TestPipelineSplitOrchestrator {
    private final AsyncProxy<TestClassProcessor> asyncProxy;

    public TestPipelineSplitOrchestrator() {
        asyncProxy = new AsyncProxy<TestClassProcessor>(TestClassProcessor.class);
    }

    public TestClassProcessor getProcessor() {
        return asyncProxy.getSource();
    }

    public void startPipelineSplitting(final PipelinesManager pipelinesManager) {
        final Map<Spec<TestClassRunInfo>, Pipeline> pipelineMatchers = new HashMap<Spec<TestClassRunInfo>, Pipeline>();
        final List<Spec<TestClassRunInfo>> splitPolicyMatchers = new ArrayList<Spec<TestClassRunInfo>>();

        for (final Pipeline pipeline : pipelinesManager.getPipelines()) {
            final PipelineConfig pipelineConfig = pipeline.getConfig();
            final SplitPolicyConfig splitPolicyConfig = pipelineConfig.getSplitPolicyConfig();

            final SplitPolicy splitPolicy = SplitPolicyRegister.getSplitPolicy(splitPolicyConfig.getPolicyName());
            final SplitPolicyInstance splitPolicyInstance = splitPolicy.getSplitPolicyInstance(pipelineConfig);

            splitPolicyInstance.prepare();

            final Spec<TestClassRunInfo> matcher = splitPolicyInstance.createSplitPolicyMatcher();

            pipelineMatchers.put(matcher, pipeline);
            splitPolicyMatchers.add(matcher);
        }

        TestClassProcessor splitter = new PipelineSplitWorker(Collections.unmodifiableList(splitPolicyMatchers),
                Collections.unmodifiableMap(pipelineMatchers));
        asyncProxy.start(splitter);
    }

    public void waitForPipelineSplittingEnded() {
        asyncProxy.stop();
    }
}
