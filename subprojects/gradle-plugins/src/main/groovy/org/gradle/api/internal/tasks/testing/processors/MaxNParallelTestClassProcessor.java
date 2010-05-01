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

package org.gradle.api.internal.tasks.testing.processors;

import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestClassProcessorFactory;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.TestClassRunInfo;
import org.gradle.listener.ThreadSafeProxy;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages a set of parallel TestClassProcessors. Uses a simple round-robin algorithm to assign test classes to
 * processors.
 */
public class MaxNParallelTestClassProcessor implements TestClassProcessor {
    private final int maxProcessors;
    private final TestClassProcessorFactory factory;
    private TestResultProcessor resultProcessor;
    private int pos;
    private List<TestClassProcessor> processors = new ArrayList<TestClassProcessor>();

    public MaxNParallelTestClassProcessor(int maxProcessors, TestClassProcessorFactory factory) {
        this.maxProcessors = maxProcessors;
        this.factory = factory;
    }

    public void startProcessing(TestResultProcessor resultProcessor) {
        ThreadSafeProxy<TestResultProcessor> proxy
                = new ThreadSafeProxy<TestResultProcessor>(TestResultProcessor.class, resultProcessor);
        this.resultProcessor = proxy.getSource();
    }

    public void processTestClass(TestClassRunInfo testClass) {
        TestClassProcessor processor;
        if (processors.size() < maxProcessors) {
            processor = factory.create();
            processors.add(processor);
            processor.startProcessing(resultProcessor);
        } else {
            processor = processors.get(pos);
            pos = (pos + 1) % processors.size();
        }
        processor.processTestClass(testClass);
    }

    public void endProcessing() {
        for (TestClassProcessor processor : processors) {
            processor.endProcessing();
        }
    }
}
