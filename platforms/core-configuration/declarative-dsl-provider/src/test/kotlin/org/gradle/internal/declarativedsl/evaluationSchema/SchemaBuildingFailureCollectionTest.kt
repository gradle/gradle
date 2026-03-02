/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.declarativedsl.evaluationSchema

import org.gradle.declarative.dsl.evaluation.SchemaIssue.UnsupportedNullableReadOnlyProperty
import org.gradle.internal.declarativedsl.analysis.analyzeEverything
import org.gradle.internal.declarativedsl.common.gradleDslGeneralSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.KClass


class SchemaBuildingFailureCollectionTest {
    @Test
    fun `schema building failures are collected`() {
        val schema = schemaFrom(ReceiverWithFailure::class)
        assertEquals(1, schema.analysisSchemaBuildingFailures.size)
        assertTrue(schema.analysisSchemaBuildingFailures.single().issue is UnsupportedNullableReadOnlyProperty)
    }

    private
    fun schemaFrom(topLevelReceiverClass: KClass<*>) =
        buildEvaluationSchema(
            topLevelReceiverClass,
            analyzeEverything,
            schemaComponents = EvaluationSchemaBuilder::gradleDslGeneralSchema,
        )

    private
    abstract class ReceiverWithFailure {
        @Suppress("unused")
        abstract val x: Int?
    }
}
