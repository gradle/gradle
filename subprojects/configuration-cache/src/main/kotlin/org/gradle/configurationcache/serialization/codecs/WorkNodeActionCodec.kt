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

package org.gradle.configurationcache.serialization.codecs

import org.gradle.api.Project
import org.gradle.api.internal.tasks.NodeExecutionContext
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.internal.tasks.WorkNodeAction
import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.configurationcache.serialization.logNotImplemented


object WorkNodeActionCodec : Codec<WorkNodeAction> {
    override suspend fun WriteContext.encode(value: WorkNodeAction) {
        logNotImplemented(value.javaClass)
    }

    override suspend fun ReadContext.decode(): WorkNodeAction {
        // TODO - should discard from graph instead
        return object : WorkNodeAction {
            override fun usesMutableProjectState() = false

            override fun getOwningProject(): Project? = null

            override fun visitDependencies(context: TaskDependencyResolveContext) {
            }

            override fun run(context: NodeExecutionContext) {
                // Ignore
            }
        }
    }
}
