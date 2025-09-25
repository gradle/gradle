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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.plugins.ProjectFeatureApplyAction
import org.gradle.api.internal.plugins.TargetTypeInformation
import org.gradle.declarative.dsl.schema.ProjectFeatureOrigin
import org.gradle.internal.declarativedsl.analysis.analyzeEverything
import org.gradle.internal.declarativedsl.common.gradleDslGeneralSchema
import org.gradle.internal.declarativedsl.evaluationSchema.buildEvaluationAndConversionSchema
import org.gradle.internal.declarativedsl.evaluationSchema.buildEvaluationSchema
import org.gradle.plugin.software.internal.ModelDefault
import org.gradle.plugin.software.internal.ProjectFeatureImplementation
import org.gradle.plugin.software.internal.ProjectFeatureRegistry
import org.junit.Assert
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock


class ProjectTypesTest {
    @Test
    fun `project types are added to the schema along with their supertypes`() {
        val registryMock = mock<ProjectFeatureRegistry> { mock ->
            on(mock.projectFeatureImplementations).thenReturn(
                setOf(object : ProjectFeatureImplementation<Subtype, Subtype> {
                    override fun getFeatureName(): String = "subtype"
                    override fun getDefinitionPublicType(): Class<Subtype> = Subtype::class.java
                    override fun getDefinitionImplementationType(): Class<out Subtype> = definitionPublicType
                    override fun getTargetDefinitionType(): TargetTypeInformation<*> = TargetTypeInformation.DefinitionTargetTypeInformation(Project::class.java)
                    override fun getBuildModelType(): Class<Subtype> = Subtype::class.java
                    override fun getBuildModelImplementationType(): Class<out Subtype> = buildModelType
                    override fun getPluginClass(): Class<out Plugin<Project>> = SubtypePlugin::class.java
                    override fun getRegisteringPluginClass(): Class<out Plugin<Settings>> = SubtypeEcosystemPlugin::class.java
                    override fun getRegisteringPluginId(): String = "com.example.test"
                    override fun getBindingTransform(): ProjectFeatureApplyAction<Subtype, Subtype, Project> =
                        ProjectFeatureApplyAction { _, _, _, _ -> }
                    override fun addModelDefault(rule: ModelDefault<*>) = Unit
                    override fun <V : ModelDefault.Visitor<*>> visitModelDefaults(type: Class<out ModelDefault<V>>, visitor: V) = Unit
                }).associateBy { it.getFeatureName() }
            )
        }

        val schemaForSettings = buildEvaluationSchema(TopLevel::class, analyzeEverything) {
            gradleDslGeneralSchema()
            projectFeaturesDefaultsComponent(TopLevel::class, registryMock)
        }

        val schemaForProject = buildEvaluationAndConversionSchema(TopLevel::class, analyzeEverything) {
            gradleDslGeneralSchema()
            projectFeaturesComponent(TopLevel::class, registryMock, withDefaultsApplication = false)
        }

        listOf(schemaForSettings, schemaForProject).forEach { schema ->
            assertTrue(schema.analysisSchema.dataClassTypesByFqName.any { it.key.qualifiedName == Supertype::class.qualifiedName })

            assertFalse(schema.analysisSchema.dataClassTypesByFqName.any { it.key.qualifiedName == Any::class.qualifiedName })
            assertFalse(schema.analysisSchema.dataClassTypesByFqName.any { it.key.qualifiedName == "java.lang.Object" })
        }

        schemaForProject.analysisSchema.topLevelReceiverType.memberFunctions.single { it.simpleName == "subtype" }.run {
            metadata.filterIsInstance<ProjectFeatureOrigin>().single().apply {
                Assert.assertEquals("subtype", featureName)
                Assert.assertEquals(SubtypePlugin::class.java.name, featurePluginClassName)
                Assert.assertEquals(SubtypeEcosystemPlugin::class.java.name, ecosystemPluginClassName)
                Assert.assertEquals("com.example.test", ecosystemPluginId)
                Assert.assertEquals(Project::class.java.name, targetDefinitionClassName)
                Assert.assertNull(targetBuildModelClassName)
            }
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

    internal
    interface SubtypeEcosystemPlugin : Plugin<Settings>
}
