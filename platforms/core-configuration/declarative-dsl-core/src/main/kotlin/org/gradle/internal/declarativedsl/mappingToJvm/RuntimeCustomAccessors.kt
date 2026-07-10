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

package org.gradle.internal.declarativedsl.mappingToJvm

import org.gradle.declarative.dsl.schema.ConfigureAccessor
import org.gradle.internal.declarativedsl.InstanceAndPublicType

/**
 * Allows resolution of custom accessors at runtime. This is used for accessors that cannot be resolved statically, for example
 * project features or container elements.
 *
 */
interface RuntimeCustomAccessors {
    fun getObjectFromCustomAccessor(receiverObject: Any, accessor: ConfigureAccessor.Custom): InstanceAndPublicType

    companion object {
        val none: RuntimeCustomAccessors = object : RuntimeCustomAccessors {
            override fun getObjectFromCustomAccessor(receiverObject: Any, accessor: ConfigureAccessor.Custom): InstanceAndPublicType = InstanceAndPublicType.NULL
        }
    }
}

/**
 * Allows [RuntimeCustomAccessors] that have post-processing requirements that must be met once all conversion is complete.
 */
interface RuntimeCustomAccessorsWithPostProcessing : RuntimeCustomAccessors {
    fun postProcess()
}


/**
 * Allows grouping of multiple [RuntimeCustomAccessors] into a single implementation.
 */
class CompositeCustomAccessors(private val implementations: List<RuntimeCustomAccessors>) : RuntimeCustomAccessors {
    override fun getObjectFromCustomAccessor(receiverObject: Any, accessor: ConfigureAccessor.Custom): InstanceAndPublicType {
        implementations.forEach {
            val result = it.getObjectFromCustomAccessor(receiverObject, accessor)
            if (result.instance != null)
                return result
        }
        return InstanceAndPublicType.NULL
    }
}
