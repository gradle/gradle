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

import org.gradle.api.internal.tasks.testing.TestDefinitionProcessor;
import org.gradle.api.internal.tasks.testing.TestDefinition;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.actor.Actor;
import org.gradle.internal.actor.ActorFactory;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.dispatch.DispatchException;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages a set of parallel {@link TestDefinitionProcessor}s.
 * <p>
 * Uses a simple round-robin algorithm to assign test definitions to processors.
 */
public class MaxNParallelTestDefinitionProcessor<D extends TestDefinition> implements TestDefinitionProcessor<D> {
    private final int maxProcessors;
    // In theory this should be a Factory<? extends TestDefinitionProcessor<? extends D>>
    // for full compatibility, but in practice we don't need it.
    private final Factory<TestDefinitionProcessor<D>> factory;
    private final ActorFactory actorFactory;
    private TestResultProcessor resultProcessor;
    private int pos;
    private final List<TestDefinitionProcessor<D>> processors = new ArrayList<>();
    private final List<TestDefinitionProcessor<D>> rawProcessors = new ArrayList<>();
    private final List<Actor> actors = new ArrayList<Actor>();
    private Actor resultProcessorActor;
    private volatile boolean stoppedNow;

    public MaxNParallelTestDefinitionProcessor(int maxProcessors, Factory<TestDefinitionProcessor<D>> factory, ActorFactory actorFactory) {
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
    public void processTestDefinition(D testDefinition) {
        if (stoppedNow) {
            return;
        }

        TestDefinitionProcessor<D> processor;
        if (processors.size() < maxProcessors) {
            processor = factory.create();
            rawProcessors.add(processor);
            Actor actor = actorFactory.createActor(processor);
            processor = Cast.uncheckedNonnullCast(actor.getProxy(TestDefinitionProcessor.class));
            actors.add(actor);
            processors.add(processor);
            processor.startProcessing(resultProcessor);
        } else {
            processor = processors.get(pos);
            pos = (pos + 1) % processors.size();
        }
        processor.processTestDefinition(testDefinition);
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
        for (TestDefinitionProcessor<D> processor : rawProcessors) {
            processor.stopNow();
        }
    }
}
