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

package org.gradle.internal.declarativedsl.analysis

import org.gradle.declarative.dsl.model.annotations.Adding
import org.gradle.declarative.dsl.schema.FunctionSemantics
import org.gradle.internal.declarativedsl.analysis.ObjectOrigin.FunctionInvocationOrigin
import org.gradle.internal.declarativedsl.demo.resolve
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import kotlin.test.Test
import kotlin.test.assertEquals


object OverloadResolutionTest {
    @Test
    fun `function overloads with and without configure lambda are disambiguated`() {
        val schema = schemaFromTypes(MyTopLevelReceiver::class, listOf(MyTopLevelReceiver::class))

        val code = """
            addSomething(1)
            addSomething(1) { }
        """.trimIndent()

        val resolver = tracingCodeResolver()
        val result = schema.resolve(code, resolver)

        val addedObjects = result.additions.map { it.dataObject }
        assertEquals(FunctionSemanticsInternal.DefaultConfigureBlockRequirement.DefaultNotAllowed,
            ((addedObjects[0] as FunctionInvocationOrigin).function.semantics as FunctionSemantics.ConfigureSemantics).configureBlockRequirement)
        assertEquals(FunctionSemanticsInternal.DefaultConfigureBlockRequirement.DefaultRequired,
            ((addedObjects[1] as FunctionInvocationOrigin).function.semantics as FunctionSemantics.ConfigureSemantics).configureBlockRequirement)
    }

    private
    abstract class MyTopLevelReceiver {
        @Adding
        abstract fun addSomething(x: Int, configure: MyTopLevelReceiver.() -> Unit): MyTopLevelReceiver

        @Adding
        abstract fun addSomething(x: Int): MyTopLevelReceiver
    }
}
