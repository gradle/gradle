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

import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.composite.internal.BuildTreeWorkGraphController
import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.configurationcache.serialization.readNonNull
import org.gradle.execution.plan.TaskInAnotherBuild


class TaskInAnotherBuildCodec(
    private val includedTaskGraph: BuildTreeWorkGraphController
) : Codec<TaskInAnotherBuild> {

    override suspend fun WriteContext.encode(value: TaskInAnotherBuild) {
        value.run {
            writeString(taskPath)
            write(targetBuild)
        }
    }

    override suspend fun ReadContext.decode(): TaskInAnotherBuild {
        val taskPath = readString()
        val targetBuild = readNonNull<BuildIdentifier>()
        return TaskInAnotherBuild.lazy(
            taskPath,
            targetBuild,
            includedTaskGraph
        )
    }
}
