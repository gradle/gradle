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

@file:Suppress("UNUSED_PARAMETER")

package org.gradle.internal.declarativedsl.checks

import org.gradle.declarative.dsl.model.annotations.AccessFromCurrentReceiverOnly
import org.gradle.declarative.dsl.model.annotations.Adding
import org.gradle.declarative.dsl.model.annotations.Configuring
import org.gradle.declarative.dsl.model.annotations.Restricted
import org.gradle.internal.declarative.dsl.checks.runChecks
import org.gradle.internal.declarativedsl.analysis.analyzeEverything
import org.gradle.internal.declarativedsl.common.gradleDslGeneralSchema
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.UnsupportedSyntax
import org.gradle.internal.declarativedsl.dom.UnsupportedSyntaxCause.AssignmentWithExplicitReceiver
import org.gradle.internal.declarativedsl.dom.UnsupportedSyntaxCause.ElementWithExplicitReceiver
import org.gradle.internal.declarativedsl.dom.fromLanguageTree.toDocument
import org.gradle.internal.declarativedsl.evaluationSchema.buildEvaluationSchema
import org.gradle.internal.declarativedsl.evaluator.checks.AccessOnCurrentReceiverCheck
import org.gradle.internal.declarativedsl.evaluator.checks.DocumentCheckFailureReason
import org.gradle.internal.declarativedsl.parsing.ParseTestUtil
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue


private
class TopLevelForTest {
    @Configuring
    fun inner(f: HasAnnotatedMembers.() -> Unit) = Unit

    @get:Restricted
    val inner: HasAnnotatedMembers
        get() = TODO()
}


private
class HasAnnotatedMembers {
    @get:Restricted
    @get:AccessFromCurrentReceiverOnly
    var x: Int = 0

    @get:Restricted
    var y: Int = 0

    @Adding
    @AccessFromCurrentReceiverOnly
    @Suppress("FunctionOnlyReturningConstant")
    fun f(): Int = 0

    @Configuring
    fun nested(fn: Nested.() -> Unit) = Unit

    @Restricted
    @AccessFromCurrentReceiverOnly
    fun factory(): Int = "0".toInt()
}


private
class Nested {
    @get:Restricted
    var n = 1
}


class AccessInCurrentReceiverOnlyTest {
    val schema = buildEvaluationSchema(TopLevelForTest::class, analyzeEverything) { gradleDslGeneralSchema() }
    val checks = listOf(AccessOnCurrentReceiverCheck)

    @Test
    fun `access on current receiver is allowed`() {
        val result = schema.runChecks(
            """
            inner {
                x = 1
                y = 1
                f()
            }
            """.trimIndent(),
            checks
        )

        assertTrue { result.isEmpty() }
    }

    @Test
    fun `access on a property is not allowed by dom conversion`() {
        val languageTree = ParseTestUtil.parse(
            """
            inner.x = 1
            inner.y = 1
            inner.f()
            """.trimIndent(),
        )
        val dom = languageTree.toDocument()

        assertEquals(
            listOf(AssignmentWithExplicitReceiver, AssignmentWithExplicitReceiver, ElementWithExplicitReceiver).map { listOf(UnsupportedSyntax(it)) },
            dom.content.map { (it as DeclarativeDocument.DocumentNode.ErrorNode).errors }
        )
    }

    @Test
    fun `access on an outer receiver is not allowed`() {
        val result = schema.runChecks(
            """
            inner {
                nested {
                    x = 1
                    y = 1
                    f()
                    n = factory()
                }
            }
            """.trimIndent(),
            checks
        )

        assertEquals(3, result.size)
        assertTrue { result.all { it.reason is DocumentCheckFailureReason.AccessOnCurrentReceiverViolation } }
        assertEquals(setOf("x = 1", "f()", "factory()") ,result.map { it.location.node.sourceData.text() }.toSet())
    }
}
