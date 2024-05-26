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

package org.gradle.internal.declarativedsl.objectGraph

import org.gradle.declarative.dsl.model.annotations.Restricted
import org.gradle.internal.declarativedsl.demo.resolve
import org.gradle.internal.declarativedsl.language.Literal
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs


object AssignmentResolverTest {

    @Test
    fun `reports and does not record reassignment`() {
        val impl = AssignmentResolver()

        val resolution = schema.resolve(
            """
            x = 1
            y = 1
            x = 2
            """.trimIndent()
        )

        val additionResults = resolution.assignments.map { impl.addAssignment(it.lhs, it.rhs, it.assignmentMethod, it.operationId.generationId) }
        assertIs<AssignmentResolver.AssignmentAdditionResult.Reassignment>(additionResults[2])

        val resultMap = impl.getAssignmentResults()
        val xAssigned = assertIs<AssignmentResolver.AssignmentResolutionResult.Assigned>(resultMap.entries.single { it.key.property.name == "x" }.value)
        assertEquals(1, (xAssigned.objectOrigin.originElement as Literal.IntLiteral).value)
    }

    val schema = schemaFromTypes(Receiver::class, listOf(Receiver::class))

    private
    interface Receiver {
        @get:Restricted
        var x: Int

        @get:Restricted
        var y: Int
    }
}
