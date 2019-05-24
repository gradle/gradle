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

package org.gradle.instantexecution.serialization


interface StateSerializer {
    fun WriteContext.serializerFor(candidate: Any?): ValueSerializer?
}


interface ValueSerializer {
    fun WriteContext.invoke(value: Any?)
}


interface StateDeserializer {
    fun ReadContext.deserialize(): Any?
}


fun writer(f: WriteContext.(Any?) -> Unit): ValueSerializer = object : ValueSerializer {
    override fun WriteContext.invoke(value: Any?) = f(value)
}
