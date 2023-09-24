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
import org.gradle.api.internal.tasks.testing.TestClassRunInfo;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.actor.Actor;
import org.gradle.internal.actor.ActorFactory;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.dispatch.DispatchException;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages a set of parallel TestClassProcessors. Uses a simple round-robin algorithm to assign test classes to
 * processors.
 */
public class MaxNParallelTestClassProcessor implements TestClassProcessor {
    private final int maxProcessors;
    private final Factory<TestClassProcessor> factory;
    private final ActorFactory actorFactory;
    private TestResultProcessor resultProcessor;
    private int pos;
    private List<TestClassProcessor> processors = new ArrayList<TestClassProcessor>();
    private List<TestClassProcessor> rawProcessors = new ArrayList<TestClassProcessor>();
    private List<Actor> actors = new ArrayList<Actor>();
    private Actor resultProcessorActor;
    private volatile boolean stoppedNow;

    public MaxNParallelTestClassProcessor(int maxProcessors, Factory<TestClassProcessor> factory, ActorFactory actorFactory) {
        this.maxProcessors = maxProcessors;
        this.factory = factory;
        this.actorFactory = actorFactory;
    }

    @Override
    public void startProcessing(TestResultProcessor resultProcessor) {
        // Create a processor that processes events in its own thread
        resultProcessorActor = actorFactory.createActor(resultProcessor);
        this.resultProcessor = resultProcessorActor.getProxy(TestResultProcessor.class);
    }

    @Override
    public void processTestClass(TestClassRunInfo testClass) {
        if (stoppedNow) {
            return;
        }

        TestClassProcessor processor;
        if (processors.size() < maxProcessors) {
            processor = factory.create();
            rawProcessors.add(processor);
            Actor actor = actorFactory.createActor(processor);
            processor = actor.getProxy(TestClassProcessor.class);
            actors.add(actor);
            processors.add(processor);
            processor.startProcessing(resultProcessor);
        } else {
            processor = processors.get(pos);
            pos = (pos + 1) % processors.size();
        }
        processor.processTestClass(testClass);
    }

    @Override
    public void stop() {
        try {
            CompositeStoppable.stoppable(processors).add(actors).add(resultProcessorActor).stop();
        } catch (DispatchException e) {
            throw UncheckedException.throwAsUncheckedException(e.getCause());
        }
    }

    @Override
    public void stopNow() {
        stoppedNow = true;
        for (TestClassProcessor processor : rawProcessors) {
            processor.stopNow();
        }
    }
}
