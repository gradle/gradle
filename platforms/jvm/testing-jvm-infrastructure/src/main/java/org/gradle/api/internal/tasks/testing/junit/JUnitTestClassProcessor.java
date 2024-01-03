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

package org.gradle.api.internal.tasks.testing.junit;

import org.gradle.api.Action;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.results.AttachParentTestResultProcessor;
import org.gradle.internal.actor.Actor;
import org.gradle.internal.actor.ActorFactory;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.time.Clock;

public class JUnitTestClassProcessor extends AbstractJUnitTestClassProcessor {

    private final JUnitSpec spec;

    public JUnitTestClassProcessor(JUnitSpec spec, IdGenerator<?> idGenerator, ActorFactory actorFactory, Clock clock) {
        super(idGenerator, actorFactory, clock);
        this.spec = spec;
    }

    @Override
    protected TestResultProcessor createResultProcessorChain(TestResultProcessor resultProcessor) {
        TestResultProcessor resultProcessorChain = new AttachParentTestResultProcessor(resultProcessor);
        return new TestClassExecutionEventGenerator(resultProcessorChain, idGenerator, clock);
    }

    @Override
    protected Action<String> createTestExecutor(Actor resultProcessorActor) {
        TestResultProcessor threadSafeResultProcessor = resultProcessorActor.getProxy(TestResultProcessor.class);
        TestClassExecutionListener threadSafeTestClassListener = resultProcessorActor.getProxy(TestClassExecutionListener.class);

        JUnitTestEventAdapter junitEventAdapter = new JUnitTestEventAdapter(threadSafeResultProcessor, clock, idGenerator);
        return new JUnitTestClassExecutor(Thread.currentThread().getContextClassLoader(), spec, junitEventAdapter, threadSafeTestClassListener);
    }

}
