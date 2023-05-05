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

import org.gradle.api.Task
import org.gradle.api.internal.tasks.TaskContainerInternal
import org.gradle.configurationcache.problems.DocumentationSection.RequirementsTaskAccess
import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.IsolateOwner
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.configurationcache.serialization.logUnsupported
import org.gradle.configurationcache.serialization.readEnum
import org.gradle.configurationcache.serialization.writeEnum


private
enum class ReferenceType {
    SELF_REF,
    TASK_REF,
    PROHIBITED
}


internal
object TaskReferenceCodec : Codec<Task> {

    override suspend fun WriteContext.encode(value: Task) = when {
        value === isolate.owner.delegate ->
            writeEnum(ReferenceType.SELF_REF)

        isTaskReferencesAllowed(value) -> {
            writeEnum(ReferenceType.TASK_REF)
            writeString(value.name)
        }

        else -> {
            writeEnum(ReferenceType.PROHIBITED)
            logUnsupported(
                "serialize",
                Task::class,
                value.javaClass,
                documentationSection = RequirementsTaskAccess
            )
        }
    }

    private
    fun WriteContext.isTaskReferencesAllowed(value: Task): Boolean {
        val owner = isolate.owner
        val delegate = owner.delegate

        val isTaskReferencesAllowed = owner is IsolateOwner.OwnerTask && owner.allowTaskReferences
        val isTaskFromSameProject = delegate is Task && delegate.project == value.project

        return isTaskReferencesAllowed && isTaskFromSameProject
    }

    override suspend fun ReadContext.decode(): Task? = when (readEnum<ReferenceType>()) {
        ReferenceType.SELF_REF ->
            isolate.owner.delegate as Task

        ReferenceType.TASK_REF -> {
            val taskName = readString()
            isolate.owner.service(TaskContainerInternal::class.java).resolveTask(taskName)
        }

        ReferenceType.PROHIBITED -> {
            logUnsupported(
                "deserialize",
                Task::class,
                documentationSection = RequirementsTaskAccess
            )
            null
        }
    }
}
