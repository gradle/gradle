/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.configurationcache.serialization

import org.gradle.api.Task
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.invocation.Gradle
import org.gradle.configurationcache.DefaultConfigurationCache
import org.gradle.internal.serialize.graph.IsolateOwner


sealed class IsolateOwners : IsolateOwner {

    class OwnerTask(
        override val delegate: Task,
        // TODO:configuration-cache - consider immutability
        var allowTaskReferences: Boolean = false
    ) : IsolateOwners() {
        override fun <T> service(type: Class<T>): T = (delegate.project as ProjectInternal).services.get(type)
    }

    class OwnerGradle(override val delegate: Gradle) : IsolateOwners() {
        override fun <T> service(type: Class<T>): T = (delegate as GradleInternal).services.get(type)
    }

    class OwnerHost(override val delegate: DefaultConfigurationCache.Host) : IsolateOwners() {
        override fun <T> service(type: Class<T>): T = delegate.service(type)
    }

    class OwnerFlowScope(override val delegate: Gradle) : IsolateOwners() {
        override fun <T> service(type: Class<T>): T = (delegate as GradleInternal).services.get(type)
    }

    class OwnerFlowAction(override val delegate: OwnerFlowScope) : IsolateOwners() {
        override fun <T> service(type: Class<T>): T = delegate.service(type)
    }
}
