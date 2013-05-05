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
package org.gradle.api.internal.project

import org.gradle.StartParameter
import org.gradle.api.internal.tasks.TaskExecuter
import org.gradle.api.internal.tasks.execution.ExecuteAtMostOnceTaskExecuter
import org.gradle.api.invocation.Gradle
import org.gradle.cache.CacheRepository
import org.gradle.cache.DirectoryCacheBuilder
import org.gradle.cache.PersistentCache
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.ServiceRegistry
import org.gradle.listener.ListenerManager
import spock.lang.Specification

class TaskExecutionServicesTest extends Specification {
    final ServiceRegistry parent = Mock()
    final Gradle gradle = Mock()
    final TaskExecutionServices services = new TaskExecutionServices(parent, gradle)

    def "makes a TaskExecutor available"() {
        given:
        ListenerManager listenerManager = Mock()
        StartParameter startParameter = Mock()
        CacheRepository cacheRepository = Mock()
        Instantiator instantiator = Mock();
        DirectoryCacheBuilder cacheBuilder = Mock()
        PersistentCache cache = Mock()
        _ * parent.get(ListenerManager) >> listenerManager
        _ * parent.get(StartParameter) >> startParameter
        _ * parent.get(CacheRepository) >> cacheRepository
        _ * parent.get(Instantiator) >> instantiator
        _ * cacheRepository.cache(!null) >> cacheBuilder
        _ * cacheBuilder.forObject(gradle) >> cacheBuilder
        _ * cacheBuilder.withDisplayName(!null) >> cacheBuilder
        _ * cacheBuilder.withLockMode(!null) >> cacheBuilder
        _ * cacheBuilder.open() >> cache

        expect:
        services.get(TaskExecuter) instanceof ExecuteAtMostOnceTaskExecuter
        services.get(TaskExecuter).is(services.get(TaskExecuter))
    }
}
