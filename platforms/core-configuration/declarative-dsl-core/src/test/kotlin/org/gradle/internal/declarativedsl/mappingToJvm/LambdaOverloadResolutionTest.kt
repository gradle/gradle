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

import org.gradle.declarative.dsl.model.annotations.Adding
import org.gradle.internal.declarativedsl.demo.resolve
import org.gradle.internal.declarativedsl.schemaBuilder.kotlinFunctionAsConfigureLambda
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.Test


class LambdaOverloadResolutionTest {
    @Test
    fun `functions with and without lambda get disambiguated at runtime`() {
        val schema = schemaFromTypes(MyTopLevelReceiver::class, listOf(MyTopLevelReceiver::class, AddedObject::class))

        val code = """
            addSomething(1) { }
            addSomething(2)
        """.trimIndent()

        val receiver = runtimeInstanceFromResult(schema, schema.resolve(code), kotlinFunctionAsConfigureLambda, RuntimeCustomAccessors.none, ::MyTopLevelReceiver)
        assertEquals(listOf("addSomething(1) { ... }", "addSomething(2)"), receiver.addedObjects.map { it.data })
    }

    internal
    class MyTopLevelReceiver {
        val addedObjects = mutableListOf<AddedObject>()

        @Adding
        @Suppress("unused")
        fun addSomething(x: Int, configure: AddedObject.() -> Unit): AddedObject =
            AddedObject("addSomething($x) { ... }")
                .also(configure)
                .also(addedObjects::add)

        @Adding
        fun addSomething(x: Int): AddedObject =
            AddedObject("addSomething($x)")
                .also(addedObjects::add)
    }

    internal
    class AddedObject(val data: String)
}
