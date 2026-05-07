/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junit;

import org.gradle.api.internal.tasks.testing.RequiresTestFrameworkTestDefinitionProcessor;
import org.gradle.api.internal.tasks.testing.TestDefinitionConsumer;
import org.gradle.api.internal.tasks.testing.TestDefinition;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.internal.actor.Actor;
import org.gradle.internal.actor.ActorFactory;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

@NullMarked
public abstract class AbstractJUnitTestDefinitionProcessor<D extends TestDefinition> implements RequiresTestFrameworkTestDefinitionProcessor<D> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractJUnitTestDefinitionProcessor.class);

    private final ActorFactory actorFactory;
    @Nullable
    private Actor resultProcessorActor;
    @Nullable
    private TestDefinitionConsumer<D> executor;

    protected boolean startedProcessing;

    public AbstractJUnitTestDefinitionProcessor(ActorFactory actorFactory) {
        this.actorFactory = actorFactory;
    }

    @Override
    public void startProcessing(TestResultProcessor resultProcessor) {
        assertTestFrameworkAvailable();

        // Wrap the result processor chain up in a blocking actor, to make the whole thing thread-safe
        resultProcessorActor = actorFactory.createBlockingActor(resultProcessor);
        executor = createTestExecutor(resultProcessorActor);

        startedProcessing = true;
    }

    protected abstract TestDefinitionConsumer<D> createTestExecutor(Actor resultProcessorActor);

    @Override
    public final void processTestDefinition(D testDefinition) {
        if (startedProcessing) {
            LOGGER.debug("Executing {}", testDefinition.getDisplayName());
            Objects.requireNonNull(executor).accept(testDefinition);
        }
    }

    @Override
    public void stop() {
        if (startedProcessing) {
            resultProcessorActor.stop();
        }
    }

    @Override
    public void stopNow() {
        throw new UnsupportedOperationException("stopNow() should not be invoked on remote worker TestDefinitionProcessor");
    }
}
