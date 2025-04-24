/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.declarativedsl.schemaBuidler

import org.gradle.declarative.dsl.model.annotations.Restricted
import org.gradle.internal.declarativedsl.analysis.DefaultFqName
import org.gradle.internal.declarativedsl.analysis.ObjectOrigin
import org.gradle.internal.declarativedsl.assertIs
import org.gradle.internal.declarativedsl.demo.resolve
import org.gradle.internal.declarativedsl.schemaBuilder.FixedTopLevelFunctionDiscovery
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.junit.Assert
import org.junit.Test
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.kotlinFunction

class ExternalFunctionsTest {
    @Test
    fun `can import the Kotlin listOf function`() {
        val rhs = schema.resolve("""myStrings = listOf("one", "two")""").assignments.single().rhs
        assertIs<ObjectOrigin.FunctionInvocationOrigin>(rhs)
        Assert.assertEquals("listOf", rhs.function.simpleName)
        assertIs<ObjectOrigin.GroupedVarargValue>(rhs.parameterBindings.bindingMap.values.single().objectOrigin)
    }

    val schema = schemaFromTypes(
        TopLevel::class,
        listOf(TopLevel::class, List::class),
        externalFunctionDiscovery = FixedTopLevelFunctionDiscovery(
            listOf<KFunction<*>>(
                Class.forName("kotlin.collections.CollectionsKt").methods.single { it.name == "listOf" && it.parameters.singleOrNull()?.isVarArgs == true }.kotlinFunction!!
            )
        ),
        defaultImports = listOf(DefaultFqName.parse("kotlin.collections.listOf"))
    )

    class TopLevel {
        @Suppress("unused")
        @get:Restricted
        var myStrings: List<String> = emptyList()
    }
}
