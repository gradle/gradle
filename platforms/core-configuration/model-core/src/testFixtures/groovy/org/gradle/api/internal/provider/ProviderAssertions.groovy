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

import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.internal.tasks.CachingTaskDependencyResolveContext
import org.gradle.api.internal.tasks.TaskDependencyContainer
import org.gradle.api.internal.tasks.WorkDependencyResolver

trait ProviderAssertions {

    void assertHasNoProducer(ProviderInternal<?> provider) {
        def producer = provider.producer
        assert !producer.known
        assert getDependencies(producer::visitDependencies) == []
        assert getDependencies(producer::visitContentDependencies) == []
    }

    void assertHasKnownProducer(ProviderInternal<?> provider) {
        def producer = provider.producer
        assert producer.known
        assert getDependencies(producer::visitDependencies) == []
        assert getDependencies(producer::visitContentDependencies) == []
    }

    void assertHasProducer(ProviderInternal<?> provider, Object task, Object... additional) {
        def expected = [task] + (additional as List)

        def producer = provider.producer
        assert producer.known
        assert getDependencies(producer::visitDependencies) == expected
        assert getDependencies(producer::visitContentDependencies) == expected
    }

    List<Object> getDependencies(TaskDependencyContainer container) {
        return new ArrayList<>(new CachingTaskDependencyResolveContext<>([
            new CollectingWorkDependencyResolver()
        ]).getDependencies(null, container))
    }

    private static class CollectingWorkDependencyResolver implements WorkDependencyResolver<Object> {

        @Override
        boolean resolve(Task task, Object node, Action<? super Object> resolveAction) {
            resolveAction.execute(node)
            return true
        }

    }

}
