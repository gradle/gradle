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

package org.gradle.features.internal


import org.gradle.features.binding.BuildModel
import org.gradle.features.internal.builders.definitions.AnotherProjectTypeDefinitionClassBuilder
import org.gradle.features.internal.builders.definitions.ProjectFeatureDefinitionAbstractClassBuilder
import org.gradle.features.internal.builders.definitions.ProjectFeatureDefinitionClassBuilder
import org.gradle.features.internal.builders.definitions.ProjectFeatureDefinitionThatUsesClassInjectedMethods
import org.gradle.features.internal.builders.definitions.ProjectFeatureDefinitionWithImplementationAndPublicBuildModelTypesClassBuilder
import org.gradle.features.internal.builders.definitions.ProjectFeatureDefinitionWithInjectableParentClassBuilder
import org.gradle.features.internal.builders.definitions.ProjectFeatureDefinitionWithNoBuildModelClassBuilder
import org.gradle.features.internal.builders.definitions.ProjectFeatureDefinitionWithPublicAndImplementationTypesClassBuilder
import org.gradle.features.internal.builders.definitions.ProjectFeatureNestedDefinitionClassBuilder
import org.gradle.features.internal.builders.definitions.ProjectFeatureThatBindsToDefinitionWithNoBuildModeClassBuilder
import org.gradle.features.internal.builders.definitions.ProjectTypeDefinitionClassBuilder
import org.gradle.features.internal.builders.definitions.ProjectTypeDefinitionThatRegistersANestedBindingLocationClassBuilder
import org.gradle.features.internal.builders.features.KotlinProjectFeaturePluginClassBuilder
import org.gradle.features.internal.builders.features.KotlinProjectFeaturePluginClassThatBindsWithClassBuilder
import org.gradle.features.internal.builders.features.KotlinReifiedProjectFeaturePluginClassBuilder
import org.gradle.features.internal.builders.features.NotAProjectFeaturePluginClassBuilder
import org.gradle.features.internal.builders.features.ProjectFeaturePluginClassBuilder
import org.gradle.features.internal.builders.features.ProjectFeaturePluginThatInjectsUnknownServiceClassBuilder
import org.gradle.features.internal.builders.features.ProjectFeaturePluginThatUsesUnsafeServicesClassBuilder
import org.gradle.features.internal.builders.features.ProjectFeatureThatBindsWithClassBuilder
import org.gradle.features.internal.builders.features.ProjectFeatureWithNoBuildModelPluginClassBuilder
import org.gradle.features.internal.builders.settings.KotlinSettingsPluginClassBuilder
import org.gradle.features.internal.builders.settings.SettingsPluginClassBuilder
import org.gradle.features.internal.builders.types.ProjectTypePluginClassBuilder
import org.gradle.features.internal.builders.types.ProjectTypeThatBindsWithClassBuilder
import org.gradle.test.fixtures.plugin.PluginBuilder

trait ProjectFeatureFixture extends ProjectTypeFixture {
    PluginBuilder withProjectFeature(ProjectTypeDefinitionClassBuilder projectTypeDefinition, ProjectTypePluginClassBuilder projectType, ProjectFeatureDefinitionClassBuilder projectFeatureDefinition, ProjectFeaturePluginClassBuilder projectFeature, SettingsPluginClassBuilder settingsBuilder) {
        PluginBuilder pluginBuilder = withProjectType(
            projectTypeDefinition,
            projectType,
            settingsBuilder
        )

        pluginBuilder.addPluginId("com.example.test-software-feature-impl", projectFeature.projectFeaturePluginClassName)
        projectFeature.build(pluginBuilder)
        projectFeatureDefinition.build(pluginBuilder)

        return pluginBuilder
    }

    PluginBuilder withProjectFeature() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionClassBuilder()
        def projectFeature = new ProjectFeaturePluginClassBuilder(projectFeatureDefinition)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withUnsafeProjectFeatureDefinitionDeclaredSafe() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionAbstractClassBuilder()
        def projectFeature = new ProjectFeaturePluginClassBuilder(projectFeatureDefinition)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withUnsafeProjectFeatureDefinitionDeclaredUnsafe() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionAbstractClassBuilder()
        def projectFeature = new ProjectFeaturePluginClassBuilder(projectFeatureDefinition).withUnsafeDefinition()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withProjectFeatureAndInjectableDefinitionDeclaredSafe() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionClassBuilder().withInjectedServices()
        def projectFeature = new ProjectFeaturePluginClassBuilder(projectFeatureDefinition)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withProjectFeatureAndNestedInjectableDefinitionDeclaredSafe() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionClassBuilder().withNestedInjectedServices()
        def projectFeature = new ProjectFeaturePluginClassBuilder(projectFeatureDefinition)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withProjectFeatureAndMultipleInjectableDefinition() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionClassBuilder().withInjectedServices().withNestedInjectedServices()
        def projectFeature = new ProjectFeaturePluginClassBuilder(projectFeatureDefinition)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withProjectFeatureAndInjectableParentDefinitionDeclaredSafe() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionWithInjectableParentClassBuilder()
        def projectFeature = new ProjectFeaturePluginClassBuilder(projectFeatureDefinition)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withPolyUnsafeProjectFeatureDefinitionDeclaredSafe() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionAbstractClassBuilder().withInjectedServices()
        def projectFeature = new ProjectFeaturePluginClassBuilder(projectFeatureDefinition)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withMultipleProjectFeaturePlugins() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionClassBuilder()
        def projectFeature = new ProjectFeaturePluginClassBuilder(projectFeatureDefinition)
        def anotherFeatureDefinition = new ProjectFeatureDefinitionClassBuilder()
            .withPublicClassName("AnotherFeatureDefinition")
        def anotherProjectFeature = new ProjectFeaturePluginClassBuilder(anotherFeatureDefinition)
            .projectFeaturePluginClassName("AnotherProjectFeatureImplPlugin")
            .name("anotherFeature")
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
            .registersProjectFeature(anotherProjectFeature.projectFeaturePluginClassName)

        def pluginBuilder = withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)

        anotherProjectFeature.build(pluginBuilder)
        anotherFeatureDefinition.build(pluginBuilder)

        return pluginBuilder
    }

    PluginBuilder withTwoProjectFeaturesThatHaveTheSameName() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionClassBuilder()
        def projectFeature = new ProjectFeaturePluginClassBuilder(projectFeatureDefinition)
        def anotherFeatureDefinition = new ProjectFeatureDefinitionClassBuilder()
            .withPublicClassName("AnotherFeatureDefinition")
        def anotherProjectFeature = new ProjectFeaturePluginClassBuilder(anotherFeatureDefinition)
            .projectFeaturePluginClassName("AnotherProjectFeatureImplPlugin")
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
            .registersProjectFeature(anotherProjectFeature.projectFeaturePluginClassName)

        def pluginBuilder = withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)

        anotherProjectFeature.build(pluginBuilder)
        anotherFeatureDefinition.build(pluginBuilder)

        return pluginBuilder
    }

    PluginBuilder withTwoProjectFeaturesThatHaveTheSameNameButDifferentBindings() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionClassBuilder()
        def projectFeature = new ProjectFeaturePluginClassBuilder(projectFeatureDefinition)

        def anotherProjectTypeDefinition = new AnotherProjectTypeDefinitionClassBuilder()
        def anotherProjectType = new ProjectTypePluginClassBuilder(anotherProjectTypeDefinition)
            .name("anotherProjectType")
            .projectTypePluginClassName("AnotherProjectTypePlugin")
            .withoutConventions()
        def anotherFeatureDefinition = new ProjectFeatureDefinitionClassBuilder()
            .withPublicClassName("AnotherFeatureDefinition")
        def anotherProjectFeature = new ProjectFeaturePluginClassBuilder(anotherFeatureDefinition)
            .bindingTypeClassName(anotherProjectTypeDefinition.publicTypeClassName)
            .projectFeaturePluginClassName("AnotherProjectFeatureImplPlugin")
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectType(anotherProjectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
            .registersProjectFeature(anotherProjectFeature.projectFeaturePluginClassName)

        def pluginBuilder = withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)

        anotherProjectType.build(pluginBuilder)
        anotherProjectTypeDefinition.build(pluginBuilder)
        anotherProjectFeature.build(pluginBuilder)
        anotherFeatureDefinition.build(pluginBuilder)

        return pluginBuilder
    }

    PluginBuilder withProjectFeatureDefinitionThatHasPublicAndImplementationTypes() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionWithPublicAndImplementationTypesClassBuilder()
        def projectFeature = new ProjectFeaturePluginClassBuilder(projectFeatureDefinition)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withProjectFeaturePluginThatDoesNotExposeProjectFeatures() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionClassBuilder()
        def projectFeature = new NotAProjectFeaturePluginClassBuilder()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withProjectFeatureThatBindsToBuildModel() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionClassBuilder()
        def projectFeature = new ProjectFeaturePluginClassBuilder(projectFeatureDefinition)
            .bindToBuildModel()
            .bindingTypeClassName(projectTypeDefinition.fullyQualifiedBuildModelClassName)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withProjectFeatureBuildModelThatHasPublicAndImplementationTypes() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionWithImplementationAndPublicBuildModelTypesClassBuilder()
        def projectFeature = new ProjectFeaturePluginClassBuilder(projectFeatureDefinition)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withProjectTypeAndFeatureThatBindsToNestedDefinition() {
        def projectTypeDefinition = new ProjectTypeDefinitionThatRegistersANestedBindingLocationClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)

        def projectFeatureDefinition = new ProjectFeatureNestedDefinitionClassBuilder()
        def projectFeature = new ProjectFeaturePluginClassBuilder(projectFeatureDefinition)
            .bindingTypeClassName(projectTypeDefinition.fullyQualifiedPublicTypeClassName + ".Foo")

        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withProjectTypeAndFeatureThatBindsToNestedBuildModel() {
        def projectTypeDefinition = new ProjectTypeDefinitionThatRegistersANestedBindingLocationClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)

        def projectFeatureDefinition = new ProjectFeatureNestedDefinitionClassBuilder()
        def projectFeature = new ProjectFeaturePluginClassBuilder(projectFeatureDefinition)
            .bindingTypeClassName(projectTypeDefinition.fullyQualifiedPublicTypeClassName + ".FooBuildModel")
            .bindToBuildModel()

        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withProjectFeatureThatHasNoBuildModel() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionWithNoBuildModelClassBuilder()
        def projectFeature = new ProjectFeatureWithNoBuildModelPluginClassBuilder(projectFeatureDefinition)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withProjectFeatureThatHasNoBuildModelAndAnotherFeatureThatBindsToItsDefinition() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionWithNoBuildModelClassBuilder()
        def projectFeature = new ProjectFeatureWithNoBuildModelPluginClassBuilder(projectFeatureDefinition)
        def anotherFeatureDefinition = new ProjectFeatureThatBindsToDefinitionWithNoBuildModeClassBuilder()
            .withPublicClassName("AnotherFeatureDefinition")
        def anotherProjectFeature = new ProjectFeaturePluginClassBuilder(anotherFeatureDefinition)
            .projectFeaturePluginClassName("AnotherProjectFeatureImplPlugin")
            .bindingTypeClassName(projectFeatureDefinition.publicTypeClassName)
            .name("anotherFeature")
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
            .registersProjectFeature(anotherProjectFeature.projectFeaturePluginClassName)
        def pluginBuilder = withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
        anotherProjectFeature.build(pluginBuilder)
        anotherFeatureDefinition.build(pluginBuilder)
        return pluginBuilder
    }

    PluginBuilder withProjectFeatureThatBindsToNoneBuildModel() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionClassBuilder()
        def projectFeature = new ProjectFeaturePluginClassBuilder(projectFeatureDefinition)
            .bindingTypeClassName(BuildModel.name + ".None")
            .bindToBuildModel()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withProjectFeatureWithUnsafeApplyActionDeclaredSafe() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionClassBuilder()
        def projectFeature = new ProjectFeaturePluginThatUsesUnsafeServicesClassBuilder(projectFeatureDefinition)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withProjectFeatureWithUnsafeApplyActionDeclaredUnsafe() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionClassBuilder()
        def projectFeature = new ProjectFeaturePluginThatUsesUnsafeServicesClassBuilder(projectFeatureDefinition).withUnsafeApplyAction()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withProjectFeatureWithUnsafeApplyActionInjectingUnknownService() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionClassBuilder()
        def projectFeature = new ProjectFeaturePluginThatInjectsUnknownServiceClassBuilder(projectFeatureDefinition).withUnsafeApplyAction()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withProjectFeatureThatBindsWithClass() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypeThatBindsWithClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionThatUsesClassInjectedMethods()
        def projectFeature = new ProjectFeatureThatBindsWithClassBuilder(projectFeatureDefinition)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withKotlinProjectFeaturePlugin() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionClassBuilder()
        def projectFeature = new KotlinProjectFeaturePluginClassBuilder(projectFeatureDefinition)
        def settingsBuilder = new KotlinSettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withKotlinProjectFeaturePluginsThatHasNoBuildModel() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionWithNoBuildModelClassBuilder()
        def projectFeature = new KotlinReifiedProjectFeaturePluginClassBuilder(projectFeatureDefinition)
        def settingsBuilder = new KotlinSettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }

    PluginBuilder withKotlinProjectFeaturePluginThatBindsWithClass() {
        def projectTypeDefinition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(projectTypeDefinition)
        def projectFeatureDefinition = new ProjectFeatureDefinitionThatUsesClassInjectedMethods()
        def projectFeature = new KotlinProjectFeaturePluginClassThatBindsWithClassBuilder(projectFeatureDefinition)
        def settingsBuilder = new KotlinSettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)
            .registersProjectFeature(projectFeature.projectFeaturePluginClassName)
        return withProjectFeature(projectTypeDefinition, projectType, projectFeatureDefinition, projectFeature, settingsBuilder)
    }
}
