/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junitplatform;

import org.gradle.api.internal.tasks.testing.RemoteTestClassStealer;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.WorkerTestClassProcessorFactory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.actor.ActorFactory;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.time.Clock;

import java.io.Serializable;
import java.lang.reflect.Constructor;

/**
 * Implementation of {@link WorkerTestClassProcessorFactory} which instantiates a {@code JUnitPlatformTestClassProcessor}.
 * This class is loaded on test workers themselves and acts as the entry-point to running JUnit Platform tests on a test worker.
 */
class JUnitPlatformTestClassProcessorFactory implements WorkerTestClassProcessorFactory, Serializable {
    private final JUnitPlatformSpec spec;

    JUnitPlatformTestClassProcessorFactory(JUnitPlatformSpec spec) {
        this.spec = spec;
    }

    @Override
    public TestClassProcessor create(ServiceRegistry serviceRegistry) {
        try {
            IdGenerator<?> idGenerator = serviceRegistry.get(IdGenerator.class);
            Clock clock = serviceRegistry.get(Clock.class);
            ActorFactory actorFactory = serviceRegistry.get(ActorFactory.class);
            Class<?> clazz = getClass().getClassLoader().loadClass("org.gradle.api.internal.tasks.testing.junitplatform.JUnitPlatformTestClassProcessor");
            Constructor<?> constructor = clazz.getConstructor(JUnitPlatformSpec.class, IdGenerator.class, ActorFactory.class, Clock.class);
            return (TestClassProcessor) constructor.newInstance(spec, idGenerator, actorFactory, clock);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public RemoteTestClassStealer buildWorkerStealer(RemoteTestClassStealer stealer) {
        return null; // currently not supported, planned to delegate
    }
}
