/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.declarative.dsl.model.annotations.HiddenInDeclarativeDsl
import org.gradle.declarative.dsl.model.annotations.Restricted
import org.gradle.internal.declarativedsl.demo.resolve
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


private
class HasHiddenProperty {
    @get:Restricted
    var x: Int = 0

    @get:Restricted
    @get:HiddenInDeclarativeDsl
    var y: Int = 0
}


class HiddenInDslTest {
    val schema = schemaFromTypes(HasHiddenProperty::class, listOf(HasHiddenProperty::class))

    @Test
    fun `handles the hidden properties correctly`() {
        val aType = schema.dataClassesByFqName.getValue(DefaultFqName.parse(HasHiddenProperty::class.qualifiedName!!))
        assertTrue { aType.properties.single { it.name == "y" }.isHiddenInDsl }

        val result = schema.resolve(
            """
            x = 1
            y = 2
            """.trimIndent()
        )

        assertEquals(2, result.errors.size)
        assertEquals("y = 2", result.errors.single { it.errorReason is ErrorReason.UnresolvedAssignmentLhs }.element.sourceData.text())
        assertEquals("y", result.errors.single { it.errorReason is ErrorReason.UnresolvedReference }.element.sourceData.text())
    }
}
