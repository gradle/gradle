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

package org.gradle.internal.declarativedsl.software

import com.nhaarman.mockitokotlin2.mock
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.declarativedsl.analysis.analyzeEverything
import org.gradle.internal.declarativedsl.evaluationSchema.buildEvaluationAndConversionSchema
import org.gradle.internal.declarativedsl.evaluationSchema.buildEvaluationSchema
import org.gradle.plugin.software.internal.Convention
import org.gradle.plugin.software.internal.SoftwareTypeImplementation
import org.gradle.plugin.software.internal.SoftwareTypeRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test


class SoftwareTypesTest {
    @Test
    fun `software types are added to the schema along with their supertypes`() {
        val registryMock = mock<SoftwareTypeRegistry> { mock ->
            on(mock.softwareTypeImplementations).thenReturn(
                setOf(object : SoftwareTypeImplementation<Subtype> {
                    override fun getSoftwareType(): String = "subtype"
                    override fun getModelPublicType(): Class<out Subtype> = Subtype::class.java
                    override fun getPluginClass(): Class<out Plugin<*>> = SubtypePlugin::class.java
                    override fun addConvention(rule: Convention<*>) {}
                    override fun getConventions(): List<Convention<*>> = emptyList()
                })
            )
        }

        val schemaForSettings = buildEvaluationSchema(TopLevel::class, analyzeEverything) {
            softwareTypesConventions(TopLevel::class, registryMock)
        }

        val schemaForProject = buildEvaluationAndConversionSchema(TopLevel::class, analyzeEverything) {
            softwareTypesWithPluginApplication(TopLevel::class, registryMock)
        }

        listOf(schemaForSettings, schemaForProject).forEach { schema ->
            assertTrue(schema.analysisSchema.dataClassesByFqName.any { it.key.qualifiedName == Supertype::class.qualifiedName })

            assertFalse(schema.analysisSchema.dataClassesByFqName.any { it.key.qualifiedName == Any::class.qualifiedName })
            assertFalse(schema.analysisSchema.dataClassesByFqName.any { it.key.qualifiedName == "java.lang.Object" })
        }
    }

    internal
    interface TopLevel

    internal
    interface Supertype

    internal
    interface Subtype : Supertype

    internal
    interface SubtypePlugin : Plugin<Project>
}