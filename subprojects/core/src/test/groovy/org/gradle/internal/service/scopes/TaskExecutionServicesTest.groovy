/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.internal.service.scopes

import org.gradle.StartParameter
import org.gradle.api.internal.changedetection.state.InMemoryTaskArtifactCache
import org.gradle.api.internal.tasks.TaskExecuter
import org.gradle.api.internal.tasks.execution.ExecuteAtMostOnceTaskExecuter
import org.gradle.api.invocation.Gradle
import org.gradle.cache.CacheBuilder
import org.gradle.cache.CacheRepository
import org.gradle.cache.PersistentCache
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.environment.GradleBuildEnvironment
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.operations.BuildOperationProcessor
import org.gradle.internal.operations.DefaultBuildOperationProcessor
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.internal.service.ServiceRegistry
import spock.lang.Specification

class TaskExecutionServicesTest extends Specification {
    final ServiceRegistry parent = Mock()
    final Gradle gradle = Mock()
    final def services = new DefaultServiceRegistry(parent).addProvider(new TaskExecutionServices())

    def "makes a TaskExecutor available"() {
        given:
        CacheRepository cacheRepository = Mock()
        CacheBuilder cacheBuilder = Mock()
        _ * parent.get(Gradle) >> gradle
        _ * parent.get(ListenerManager) >> Mock(ListenerManager)
        _ * parent.get(StartParameter) >> Mock(StartParameter)
        _ * parent.get(GradleBuildEnvironment) >> Stub(GradleBuildEnvironment)
        _ * parent.get(CacheRepository) >> cacheRepository
        _ * parent.get(Instantiator) >> Mock(Instantiator)
        _ * parent.get(InMemoryTaskArtifactCache) >> Mock(InMemoryTaskArtifactCache)
        _ * parent.get(StartParameter) >> Mock(StartParameter)
        _ * cacheRepository.cache(gradle, 'taskArtifacts') >> cacheBuilder
        _ * cacheBuilder.withDisplayName(!null) >> cacheBuilder
        _ * cacheBuilder.withLockOptions(!null) >> cacheBuilder
        _ * cacheBuilder.open() >> Mock(PersistentCache)

        expect:
        services.get(TaskExecuter) instanceof ExecuteAtMostOnceTaskExecuter
        services.get(TaskExecuter).is(services.get(TaskExecuter))
    }

    def "makes a BuildOperationProcessor available"() {
        given:
        _ * parent.get(StartParameter) >> Mock(StartParameter)
        _ * parent.get(ExecutorFactory) >> Mock(ExecutorFactory)

        expect:
        services.get(BuildOperationProcessor) instanceof DefaultBuildOperationProcessor
        services.get(BuildOperationProcessor).is(services.get(BuildOperationProcessor))
    }
}
