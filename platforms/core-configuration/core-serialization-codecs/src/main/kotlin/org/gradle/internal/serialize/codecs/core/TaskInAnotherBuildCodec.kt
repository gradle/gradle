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

package org.gradle.internal.serialize.codecs.core

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.PathSerializer
import org.gradle.execution.plan.TaskInAnotherBuild
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext


class TaskInAnotherBuildCodec(
    val pathSerializer: PathSerializer
) : Codec<TaskInAnotherBuild> {

    override suspend fun WriteContext.encode(value: TaskInAnotherBuild) {
        pathSerializer.write(this, value.taskIdentityPath)
    }

    override suspend fun ReadContext.decode(): TaskInAnotherBuild {
        val taskIdentityPath = pathSerializer.read(this)
        return TaskInAnotherBuild.restored(taskIdentityPath)
    }

}
