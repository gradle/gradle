/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.internal.provider

import org.gradle.api.Task
import org.gradle.api.internal.tasks.TaskDependencyContainer
import org.gradle.api.internal.tasks.TaskDependencyResolveContext

import java.util.function.Consumer

trait ProviderAssertions {
    void assertHasNoProducer(ProviderInternal<?> provider) {
        def producer = provider.producer
        assert !producer.known
        producer.getDependencies() == TaskDependencyContainer.EMPTY
        producer.getContentDependencies() == TaskDependencyContainer.EMPTY
    }

    void assertHasKnownProducer(ProviderInternal<?> provider) {
        def producer = provider.producer
        assert producer.known
        producer.getDependencies() == TaskDependencyContainer.EMPTY
        producer.getContentDependencies() == TaskDependencyContainer.EMPTY
    }

    void assertHasProducer(ProviderInternal<?> provider, Task task, Task... additional) {
        def expected = [task] + (additional as List)

        def producer = provider.producer
        assert producer.known
        def tasks = []
        producer.getDependencies().visitDependencies(new TestContext(tasks::add))
        assert tasks == expected
        tasks.clear()
        producer.getContentDependencies().visitDependencies(new TestContext(tasks::add))
        assert tasks == expected
    }

    static class TestContext implements TaskDependencyResolveContext {

        private final Consumer<Object> action

        TestContext(Consumer<Object> action) {
            this.action = action
        }

        @Override
        void add(Object dependency) {
            action.accept(dependency)
        }

        @Override
        void visitFailure(Throwable failure) {
            assert false
        }

        @Override
        Task getTask() {
            assert false
        }

    }
}
