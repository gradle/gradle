package org.gradle.internal.declarativedsl.demo.demoSimple

import org.gradle.internal.declarativedsl.analysis.ObjectOrigin
import org.gradle.internal.declarativedsl.demo.propertyLinkTrace
import org.gradle.internal.declarativedsl.demo.resolve
import org.gradle.internal.declarativedsl.objectGraph.PropertyLinksResolver
import org.gradle.internal.declarativedsl.objectGraph.PropertyLinkTraceElement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.Test
import org.gradle.internal.declarativedsl.assertIs


class AssignmentResolverTest {
    @Test
    fun `resolves transitive property`() {
        propertyLinkTrace(
            schema.resolve(
                """
                val myD = newD("shared")
                val c1 = c(1)
                c1.d = myD
                str = c1.d.id
                """.trimIndent()
            )
        ).run {
            val strAssignment = trace.find { it.originElement.sourceData.text() == "str = c1.d.id" }
            assertIs<PropertyLinkTraceElement.RecordedAssignment>(strAssignment)
            val value = finalAssignments[strAssignment.lhs]
            assertIs<PropertyLinksResolver.AssignmentResolutionResult.Assigned>(value)
            assertEquals(strAssignment.resolvedRhs, value.objectOrigin)
            val valueOrigin = value.objectOrigin
            assertIs<ObjectOrigin.ConstantOrigin>(valueOrigin)
            assertEquals("shared", valueOrigin.literal.value)
        }
    }

    @Test
    fun `reports assignment pointing to unassigned property`() {
        propertyLinkTrace(
            schema.resolve(
                """
                val c1 = c(1) { }
                val c2 = c(2) { }
                c2.d = c1.d
                """.trimIndent()
            )
        ).run {
            val shouldBeUnassigned = trace.find { it.originElement.sourceData.text() == "c2.d = c1.d" }
            assertIs<PropertyLinkTraceElement.UnassignedValueUsedInAssignment>(shouldBeUnassigned)
            val assignmentAdditionResult = shouldBeUnassigned.assignmentAdditionResult
            assertIs<PropertyLinksResolver.AssignmentAdditionResult.UnresolvedValueUsedInRhs>(assignmentAdditionResult)
            val propertyReference = assignmentAdditionResult.value
            assertIs<ObjectOrigin.PropertyReference>(propertyReference)
            assertEquals("d", propertyReference.property.name)
        }
    }

    @Test
    fun `reports unassigned value used in lhs`() {
        propertyLinkTrace(
            schema.resolve(
                """
                val c1 = c(1) { }
                c1.d.id = "test"
                """.trimIndent()
            )
        ).run {
            val shouldBeUnassigned = trace.find { it.originElement.sourceData.text() == "c1.d.id = \"test\"" }
            assertIs<PropertyLinkTraceElement.UnassignedValueUsedInAssignment>(shouldBeUnassigned)
            val assignmentAdditionResult = shouldBeUnassigned.assignmentAdditionResult
            assertIs<PropertyLinksResolver.AssignmentAdditionResult.UnresolvedValueUsedInLhs>(assignmentAdditionResult)
            val propertyReference = assignmentAdditionResult.value
            assertIs<ObjectOrigin.PropertyReference>(propertyReference)
            assertEquals("d", propertyReference.property.name)
        }
    }
}
