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
import org.gradle.api.testing.fabric.TestClassRunInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author Tom Eyckmans
 */
public class PipelineSplitWorker implements TestClassProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineSplitWorker.class);

    private final List<? extends Spec<TestClassRunInfo>> splitPolicyMatchers;
    private final Map<? extends Spec<TestClassRunInfo>, ? extends TestClassProcessor> pipelineMatchers;

    public PipelineSplitWorker(List<? extends Spec<TestClassRunInfo>> splitPolicyMatchers,
                               Map<? extends Spec<TestClassRunInfo>, ? extends TestClassProcessor> pipelineMatchers) {
        this.splitPolicyMatchers = splitPolicyMatchers;
        this.pipelineMatchers = pipelineMatchers;
    }

    public void processTestClass(TestClassRunInfo testClass) {
        LOGGER.debug("[pipeline-splitting >> test-run] {}", testClass.getTestClassName());

        Spec<TestClassRunInfo> matcher = null;

        final Iterator<? extends Spec<TestClassRunInfo>> matcherIterator = splitPolicyMatchers.iterator();
        while (matcher == null && matcherIterator.hasNext()) {
            Spec<TestClassRunInfo> currentMatcher = matcherIterator.next();
            if (currentMatcher.isSatisfiedBy(testClass)) {
                matcher = currentMatcher;
            }
        }

        pipelineMatchers.get(matcher).processTestClass(testClass);
    }
}
