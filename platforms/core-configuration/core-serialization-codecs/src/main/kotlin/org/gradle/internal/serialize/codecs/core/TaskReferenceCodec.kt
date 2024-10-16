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

import org.gradle.api.Task
import org.gradle.api.internal.GeneratedSubclasses.unpackType
import org.gradle.api.internal.tasks.TaskContainerInternal
import org.gradle.internal.cc.base.serialize.IsolateOwners
import org.gradle.internal.serialize.graph.logUnsupportedBaseType
import org.gradle.internal.configuration.problems.DocumentationSection.RequirementsTaskAccess
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.logUnsupported
import org.gradle.internal.serialize.graph.readEnum
import org.gradle.internal.serialize.graph.writeEnum


private
enum class ReferenceType {
    SELF_REF,
    TASK_REF,
    PROHIBITED
}


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
            logUnsupportedBaseType(
                "serialize",
                Task::class,
                unpackType(value),
                documentationSection = RequirementsTaskAccess
            )
        }
    }

    private
    fun WriteContext.isTaskReferencesAllowed(value: Task): Boolean {
        val owner = isolate.owner
        val delegate = owner.delegate

        val isTaskReferencesAllowed = owner is IsolateOwners.OwnerTask && owner.allowTaskReferences
        val isTaskFromSameProject = delegate is Task && delegate.project == value.project

        return isTaskReferencesAllowed && isTaskFromSameProject
    }

    override suspend fun ReadContext.decode(): Task? = when (readEnum<ReferenceType>()) {
        ReferenceType.SELF_REF ->
            isolate.owner.delegate as Task

        ReferenceType.TASK_REF -> {
            val taskName = readString()
            isolate.owner.service(TaskContainerInternal::class.java).getByPath(taskName)
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
