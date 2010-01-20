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
import org.gradle.listener.AsyncProxy;

import java.util.LinkedHashMap;
import java.util.Map;

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
        Map<Spec<TestClassRunInfo>, Pipeline> pipelineMatchers = new LinkedHashMap<Spec<TestClassRunInfo>, Pipeline>();

        for (Pipeline pipeline : pipelinesManager.getPipelines()) {
            PipelineConfig pipelineConfig = pipeline.getConfig();
            SplitPolicy splitPolicy = pipelineConfig.getSplitPolicyConfig();
            Spec<TestClassRunInfo> matcher = splitPolicy.createSplitPolicyMatcher(pipelineConfig);

            pipelineMatchers.put(matcher, pipeline);
        }

        TestClassProcessor splitter = new SplittingTestClassProcessor(pipelineMatchers);
        asyncProxy.start(splitter);
    }

    public void waitForPipelineSplittingEnded() {
        asyncProxy.stop();
    }
}
