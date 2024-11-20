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


interface RuntimeCustomAccessors {
    fun getObjectFromCustomAccessor(receiverObject: Any, accessor: ConfigureAccessor.Custom): InstanceAndPublicType

    companion object {
        val none: RuntimeCustomAccessors = object : RuntimeCustomAccessors {
            override fun getObjectFromCustomAccessor(receiverObject: Any, accessor: ConfigureAccessor.Custom): InstanceAndPublicType = nullInstanceAndPublicType
        }
    }
}


class CompositeCustomAccessors(private val implementations: List<RuntimeCustomAccessors>) : RuntimeCustomAccessors {
    override fun getObjectFromCustomAccessor(receiverObject: Any, accessor: ConfigureAccessor.Custom): InstanceAndPublicType {
        implementations.forEach {
            val result = it.getObjectFromCustomAccessor(receiverObject, accessor)
            if (result.first != null)
                return result
        }
        return nullInstanceAndPublicType
    }
}
