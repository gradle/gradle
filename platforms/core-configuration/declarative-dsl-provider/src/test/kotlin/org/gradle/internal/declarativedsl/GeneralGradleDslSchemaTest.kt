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

package org.gradle.internal.declarativedsl

import org.gradle.api.Action
import org.gradle.api.provider.Property
import org.gradle.declarative.dsl.model.annotations.Configuring
import org.gradle.declarative.dsl.model.annotations.Restricted
import org.gradle.internal.declarativedsl.analysis.analyzeEverything
import org.gradle.internal.declarativedsl.evaluationSchema.EvaluationSchemaBuilder
import org.gradle.internal.declarativedsl.evaluationSchema.buildEvaluationSchema
import org.gradle.internal.declarativedsl.common.gradleDslGeneralSchema
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.KClass


class GeneralGradleDslSchemaTest {
    @Test
    fun `general dsl schema has types discovered via action functions`() {
        val schema = schemaFrom(TopLevelReceiver::class)
        assertTrue(schema.analysisSchema.dataClassesByFqName.keys.any { it.simpleName == NestedReceiver::class.simpleName })
    }

    @Test
    fun `general dsl schema has types discovered via factory functions`() {
        val schema = schemaFrom(UtilsContainer::class)
        assertTrue(schema.analysisSchema.dataClassesByFqName.keys.any { it.simpleName == NestedReceiver::class.simpleName })
    }

    @Test
    fun `general dsl schema has properties imported from gradle property api`() {
        val schema = schemaFrom(NestedReceiver::class)
        assertTrue(schema.analysisSchema.dataClassesByFqName.values.single { it.name.simpleName == NestedReceiver::class.simpleName }.properties.any { it.name == "property" })
    }

    private
    fun schemaFrom(topLevelReceiverClass: KClass<*>) =
        buildEvaluationSchema(
            topLevelReceiverClass,
            analyzeEverything,
            schemaComponents = EvaluationSchemaBuilder::gradleDslGeneralSchema,
        )

    private
    abstract class TopLevelReceiver {
        @Configuring
        @Suppress("unused")
        abstract fun configureNestedReceiver(configure: Action<in NestedReceiver>)
    }

    private
    abstract class NestedReceiver {
        @get:Restricted
        @Suppress("unused")
        abstract val property: Property<Int>
    }

    private
    abstract class UtilsContainer {
        @Restricted
        @Suppress("unused")
        abstract fun nestedReceiver(): NestedReceiver
    }
}
