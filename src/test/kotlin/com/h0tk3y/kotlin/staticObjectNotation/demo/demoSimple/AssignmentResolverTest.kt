package com.example.com.h0tk3y.kotlin.staticObjectNotation.demo.demoSimple

import com.h0tk3y.kotlin.staticObjectNotation.analysis.ObjectOrigin
import com.h0tk3y.kotlin.staticObjectNotation.demo.assignmentTrace
import com.h0tk3y.kotlin.staticObjectNotation.demo.resolve
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.AssignmentResolver
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.AssignmentTraceElement
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

object AssignmentResolverTest {
    @Test
    fun `resolves transitive property`() {
        assignmentTrace(
            schema.resolve(
                """
                val myD = newD("shared")
                val c1 = c(1)
                c1.d = myD
                str = c1.d.id
                """.trimIndent()
            )
        ).run { 
            val strAssignment = elements.find { it.lhs.property.name == "str" }
            assertIs<AssignmentTraceElement.RecordedAssignment>(strAssignment)
            val value = resolvedAssignments[strAssignment.lhs]
            assertIs<AssignmentResolver.AssignmentResolutionResult.Assigned>(value)
            val valueOrigin = value.objectOrigin
            assertIs<ObjectOrigin.ConstantOrigin>(valueOrigin)
            assertEquals("shared", valueOrigin.literal.value)
        }
    }
    
    @Test
    fun `reports assignment pointing to unassigned property`() {
        assignmentTrace(
            schema.resolve(
                """
                val c1 = c(1)
                val c2 = c(2)
                c2.d = c1.d
                """.trimIndent()
            )
        ).run {
            val shouldBeUnassigned = elements[2]
            assertIs<AssignmentTraceElement.UnassignedValueUsed>(shouldBeUnassigned)
            val assignmentAdditionResult = shouldBeUnassigned.assignmentAdditionResult
            assertIs<AssignmentResolver.AssignmentAdditionResult.UnresolvedValueUsedInRhs>(assignmentAdditionResult)
            val propertyReference = assignmentAdditionResult.value
            assertIs<ObjectOrigin.PropertyReference>(propertyReference)
            assertEquals("d", propertyReference.property.name)
        }
    }
    
    @Test
    fun `reports unassigned value used in lhs`() {
        assignmentTrace(
            schema.resolve(
                """
                val c1 = c(1)
                c1.d.id = "test"
                """.trimIndent()
            )
        ).run {
            val shouldBeUnassigned = elements[1]
            assertIs<AssignmentTraceElement.UnassignedValueUsed>(shouldBeUnassigned)
            val assignmentAdditionResult = shouldBeUnassigned.assignmentAdditionResult
            assertIs<AssignmentResolver.AssignmentAdditionResult.UnresolvedValueUsedInLhs>(assignmentAdditionResult)
            val propertyReference = assignmentAdditionResult.value
            assertIs<ObjectOrigin.PropertyReference>(propertyReference)
            assertEquals("d", propertyReference.property.name)
        }
    }
}