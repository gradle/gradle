/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.instantexecution.serialization.beans

import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.internal.reflect.ClassInspector


class SerializableReadReplaceReader() : BeanStateReader {
    override suspend fun ReadContext.newBean(): Any {
        // Read the entire state of the placeholder. This means there cannot be a reference to this object in the state of the placeholder
        val placeholder = read()!!
        // TODO - this method lookup will need some kind of caching
        val details = ClassInspector.inspect(placeholder.javaClass)
        val method = details.allMethods.find { it.name == "readResolve" && it.parameters.isEmpty() }!!
        method.isAccessible = true
        return method.invoke(placeholder)
    }

    override suspend fun ReadContext.readStateOf(bean: Any) {
        // Nothing to do
    }
}
