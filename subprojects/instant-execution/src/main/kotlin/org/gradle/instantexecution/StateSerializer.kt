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

package org.gradle.instantexecution


interface StateSerializer {
    fun WriteContext.serializerFor(value: Any?): ValueSerializer?
}


interface ValueSerializer {
    fun WriteContext.invoke(context: SerializationContext)
}


interface StateDeserializer {
    fun ReadContext.read(context: DeserializationContext): Any?
}


fun writer(f: WriteContext.(SerializationContext) -> Unit): ValueSerializer = object : ValueSerializer {
    override fun WriteContext.invoke(context: SerializationContext) = f(context)
}


