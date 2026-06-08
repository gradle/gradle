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
import org.gradle.internal.configuration.problems.DocumentationSection.RequirementsTaskAccess
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.logUnsupported
import org.gradle.internal.serialize.graph.logUnsupportedBaseType
import org.gradle.internal.serialize.graph.readEnum
import org.gradle.internal.serialize.graph.writeEnum


private
enum class ReferenceType {
    SELF_REF,
    PROHIBITED
}


object TaskReferenceCodec : Codec<Task> {

    override suspend fun WriteContext.encode(value: Task) = when {
        value === isolate.owner.delegate ->
            writeEnum(ReferenceType.SELF_REF)

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

    override suspend fun ReadContext.decode(): Task? = when (readEnum<ReferenceType>()) {
        ReferenceType.SELF_REF ->
            isolate.owner.delegate as Task

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
