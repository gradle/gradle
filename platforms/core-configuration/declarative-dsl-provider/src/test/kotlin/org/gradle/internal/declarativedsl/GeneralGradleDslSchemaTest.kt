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
import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.internal.declarativedsl.analysis.DefaultFqName
import org.gradle.internal.declarativedsl.analysis.analyzeEverything
import org.gradle.internal.declarativedsl.common.gradleDslGeneralSchema
import org.gradle.internal.declarativedsl.evaluationSchema.EvaluationSchemaBuilder
import org.gradle.internal.declarativedsl.evaluationSchema.buildEvaluationSchema
import org.gradle.internal.declarativedsl.schemaUtils.findType
import org.gradle.internal.declarativedsl.schemaUtils.findTypeFor
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.KClass


class GeneralGradleDslSchemaTest {
    @Test
    fun `general dsl schema has types discovered via action functions`() {
        val schema = schemaFrom(TopLevelReceiver::class)
        assertHasNestedReceiverType(schema.analysisSchema)
    }

    @Test
    fun `general dsl schema has types discovered via factory functions`() {
        val schema = schemaFrom(UtilsContainer::class)
        assertHasNestedReceiverType(schema.analysisSchema)
    }

    @Test
    fun `general dsl schema has types discovered via properties`() {
        val schema = schemaFrom(TypeDiscoveryTestReceiver::class)
        assertNotNull(schema.analysisSchema.dataClassTypesByFqName[DefaultFqName.parse(Enum::class.qualifiedName!!)])
        assertNotNull(schema.analysisSchema.findTypeFor<UtilsContainer>())
    }

    @Test
    fun `general dsl schema has properties imported from gradle property api`() {
        val schema = schemaFrom(NestedReceiver::class)
        assertHasNestedReceiverType(schema.analysisSchema)
        val singleType = schema.analysisSchema.findType { it: DataClass -> it.name.simpleName == NestedReceiver::class.simpleName }!!
        assertTrue(singleType.properties.any { it.name == "intProperty" })
        assertTrue(singleType.properties.any { it.name == "enumProperty" })
    }

    private
    fun assertHasNestedReceiverType(analysisSchema: AnalysisSchema) {
        assertTrue(analysisSchema.dataClassTypesByFqName.keys.any { it.simpleName == NestedReceiver::class.simpleName })
        // It should also include the supertype of the specified type.
        // Having it in the schema is useful for locating and mutating definitions based on the supertype.
        assertTrue(analysisSchema.dataClassTypesByFqName.keys.any { it.simpleName == NestedReceiverSupertype::class.simpleName })
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
    interface NestedReceiverSupertype

    private
    abstract class NestedReceiver : NestedReceiverSupertype {
        @get:Restricted
        @Suppress("unused")
        abstract val intProperty: Property<Int>

        @get:Restricted
        @Suppress("unused")
        abstract val enumProperty: Property<Enum>
    }

    private
    abstract class UtilsContainer {
        @Restricted
        @Suppress("unused")
        abstract fun nestedReceiver(): NestedReceiver
    }

    @Suppress("unused")
    private
    enum class Enum {
        A, B, C
    }

    @Suppress("unused")
    private interface TypeDiscoveryTestReceiver {
        @get:Restricted
        var javaBeanProperty: Enum

        @get:Restricted
        val propertyApiProperty: Property<UtilsContainer>
    }
}
