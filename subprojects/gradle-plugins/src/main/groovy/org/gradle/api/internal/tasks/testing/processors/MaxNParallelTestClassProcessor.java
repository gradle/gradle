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
import org.gradle.api.internal.tasks.testing.TestClassRunInfo;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.messaging.actor.Actor;
import org.gradle.messaging.actor.ActorFactory;
import org.gradle.messaging.dispatch.CompositeStoppable;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages a set of parallel TestClassProcessors. Uses a simple round-robin algorithm to assign test classes to
 * processors.
 */
public class MaxNParallelTestClassProcessor implements TestClassProcessor {
    private final int maxProcessors;
    private final TestClassProcessorFactory factory;
    private final ActorFactory actorFactory;
    private TestResultProcessor resultProcessor;
    private int pos;
    private List<TestClassProcessor> processors = new ArrayList<TestClassProcessor>();
    private Actor resultProcessorActor;

    public MaxNParallelTestClassProcessor(int maxProcessors, TestClassProcessorFactory factory, ActorFactory actorFactory) {
        this.maxProcessors = maxProcessors;
        this.factory = factory;
        this.actorFactory = actorFactory;
    }

    public void startProcessing(TestResultProcessor resultProcessor) {
        resultProcessorActor = actorFactory.createActor(resultProcessor);
        this.resultProcessor = resultProcessorActor.getProxy(TestResultProcessor.class);
    }

    public void processTestClass(TestClassRunInfo testClass) {
        TestClassProcessor processor;
        if (processors.size() < maxProcessors) {
            processor = factory.create();
            processor = actorFactory.createActor(processor).getProxy(TestClassProcessor.class);
            processors.add(processor);
            processor.startProcessing(resultProcessor);
        } else {
            processor = processors.get(pos);
            pos = (pos + 1) % processors.size();
        }
        processor.processTestClass(testClass);
    }

    public void stop() {
        new CompositeStoppable(processors).add(resultProcessorActor).stop();
    }
}
