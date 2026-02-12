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

import groovy.transform.SelfType
import org.gradle.features.internal.builders.definitions.AnotherProjectTypeDefinitionClassBuilder
import org.gradle.features.internal.builders.definitions.ProjectTypeDefinitionAbstractClassBuilder
import org.gradle.features.internal.builders.definitions.ProjectTypeDefinitionClassBuilder
import org.gradle.features.internal.builders.definitions.ProjectTypeDefinitionWithDependenciesClassBuilder
import org.gradle.features.internal.builders.definitions.ProjectTypeDefinitionWithInjectableParentClassBuilder
import org.gradle.features.internal.builders.definitions.ProjectTypeDefinitionWithNdocClassBuilder
import org.gradle.features.internal.builders.definitions.ProjectTypeDefinitionWithPublicAndImplementationTypesClassBuilder
import org.gradle.features.internal.builders.settings.KotlinSettingsPluginClassBuilder
import org.gradle.features.internal.builders.settings.SettingsPluginThatConfiguresProjectTypeDefaultsBuilder
import org.gradle.features.internal.builders.types.KotlinProjectTypeThatBindsWithClassBuilder
import org.gradle.features.internal.builders.types.ProjectPluginThatDoesNotExposeProjectTypesBuilder
import org.gradle.features.internal.builders.types.ProjectPluginThatProvidesMultipleProjectTypesBuilder
import org.gradle.features.internal.builders.types.ProjectTypePluginClassBuilder
import org.gradle.features.internal.builders.types.ProjectTypeThatBindsWithClassBuilder
import org.gradle.features.internal.builders.types.ProjectTypeThatUsesUnknownServicesClassBuilder
import org.gradle.features.internal.builders.types.ProjectTypeThatUsesUnsafeServicesClassBuilder
import org.gradle.features.internal.builders.settings.SettingsPluginClassBuilder
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.plugin.PluginBuilder

@SelfType(AbstractIntegrationSpec)
trait ProjectTypeFixture {
    PluginBuilder withProjectType(ProjectTypeDefinitionClassBuilder definitionBuilder, ProjectTypePluginClassBuilder projectTypeBuilder, SettingsPluginClassBuilder settingsBuilder) {
        def pluginBuilder = new PluginBuilder(file("plugins"))
        pluginBuilder.addPluginId("com.example.test-project-type-impl", projectTypeBuilder.projectTypePluginClassName)
        pluginBuilder.addPluginId("com.example.test-software-ecosystem", settingsBuilder.pluginClassName)

        definitionBuilder.build(pluginBuilder)
        projectTypeBuilder.build(pluginBuilder)
        settingsBuilder.build(pluginBuilder)

        return pluginBuilder
    }

    PluginBuilder withProjectType() {
        def definition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(definition)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        return withProjectType(
            definition,
            projectType,
            settingsBuilder
        )
    }

    PluginBuilder withProjectTypeWithNdoc() {
        def definition = new ProjectTypeDefinitionWithNdocClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(definition)
            .withoutConventions()
            .withUnsafeDefinition()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        return withProjectType(
            definition,
            projectType,
            settingsBuilder
        )
    }

    PluginBuilder withProjectTypePluginThatDoesNotExposeProjectTypes() {
        def definition = new ProjectTypeDefinitionWithNdocClassBuilder()
        def projectType = new ProjectPluginThatDoesNotExposeProjectTypesBuilder(definition)
            .projectTypePluginClassName("NotAProjectTypePlugin")
            .withoutConventions()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        return withProjectType(
            definition,
            projectType,
            settingsBuilder
        )
    }

    PluginBuilder withSettingsPluginThatExposesMultipleProjectTypes() {
        def mainDefinition = new ProjectTypeDefinitionClassBuilder()
        def anotherDefinition = new AnotherProjectTypeDefinitionClassBuilder()
        def mainProjectType = new ProjectTypePluginClassBuilder(mainDefinition)
        def anotherProjectType = new ProjectTypePluginClassBuilder(anotherDefinition)
            .projectTypePluginClassName("AnotherProjectTypeImplPlugin")
            .withoutConventions()
            .name("anotherProjectType")
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(mainProjectType.projectTypePluginClassName)
            .registersProjectType(anotherProjectType.projectTypePluginClassName)

        PluginBuilder pluginBuilder = withProjectType(
            mainDefinition,
            mainProjectType,
            settingsBuilder
        )

        pluginBuilder.addPluginId("com.example.another-software-type-impl", anotherProjectType.projectTypePluginClassName)
        anotherProjectType.build(pluginBuilder)
        anotherDefinition.build(pluginBuilder)

        return pluginBuilder
    }

    PluginBuilder withTwoProjectTypesThatHaveTheSameName() {
        def mainDefinition = new ProjectTypeDefinitionClassBuilder()
        def anotherDefinition = new AnotherProjectTypeDefinitionClassBuilder()
        def mainProjectType = new ProjectTypePluginClassBuilder(mainDefinition)
        def anotherProjectType = new ProjectTypePluginClassBuilder(anotherDefinition)
            .projectTypePluginClassName("AnotherProjectTypeImplPlugin")
            .withoutConventions()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(mainProjectType.projectTypePluginClassName)
            .registersProjectType(anotherProjectType.projectTypePluginClassName)

        PluginBuilder pluginBuilder = withProjectType(
            mainDefinition,
            mainProjectType,
            settingsBuilder
        )

        pluginBuilder.addPluginId("com.example.another-software-type-impl", anotherProjectType.projectTypePluginClassName)
        anotherProjectType.build(pluginBuilder)
        anotherDefinition.build(pluginBuilder)

        return pluginBuilder
    }

    PluginBuilder withProjectTypeThatHasDifferentPublicAndImplementationTypes() {
        def definition = new ProjectTypeDefinitionWithPublicAndImplementationTypesClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(definition)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        PluginBuilder pluginBuilder = withProjectType(
            definition,
            projectType,
            settingsBuilder
        )

        return pluginBuilder
    }

    PluginBuilder withProjectTypePluginThatExposesMultipleProjectTypes() {
        def definition = new ProjectTypeDefinitionClassBuilder()
        def anotherProjectTypeDefinition = new AnotherProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectPluginThatProvidesMultipleProjectTypesBuilder(definition, anotherProjectTypeDefinition)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        PluginBuilder pluginBuilder = withProjectType(
            definition,
            projectType,
            settingsBuilder
        )

        anotherProjectTypeDefinition.build(pluginBuilder)

        return pluginBuilder
    }

    PluginBuilder withProjectTypeDefinitionWithDependencies() {
        def definitionWithClasses = new ProjectTypeDefinitionWithDependenciesClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(definitionWithClasses)
            .withUnsafeDefinition()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        PluginBuilder pluginBuilder = withProjectType(
            definitionWithClasses,
            projectType,
            settingsBuilder
        )

        return pluginBuilder
    }

    PluginBuilder withSettingsPluginThatConfiguresModelDefaults() {
        def definition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(definition)
        def settingsBuilder = new SettingsPluginThatConfiguresProjectTypeDefaultsBuilder()
            .definitionImplementationTypeClassName(definition.publicTypeClassName)
            .registersProjectType(projectType.projectTypePluginClassName)

        return withProjectType(
            definition,
            projectType,
            settingsBuilder
        )
    }

    PluginBuilder withUnsafeProjectTypeDefinitionDeclaredSafe() {
        def definition = new ProjectTypeDefinitionAbstractClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(definition)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        return withProjectType(
            definition,
            projectType,
            settingsBuilder
        )
    }

    PluginBuilder withUnsafeProjectTypeDefinitionDeclaredUnsafe() {
        def definition = new ProjectTypeDefinitionAbstractClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(definition).withUnsafeDefinition()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        return withProjectType(
            definition,
            projectType,
            settingsBuilder
        )
    }

    PluginBuilder withSafeProjectTypeAndInjectableDefinition() {
        def definition = new ProjectTypeDefinitionClassBuilder().withInjectedServices()
        def projectType = new ProjectTypePluginClassBuilder(definition)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        return withProjectType(
            definition,
            projectType,
            settingsBuilder
        )
    }

    PluginBuilder withSafeProjectTypeAndNestedInjectableDefinition() {
        def definition = new ProjectTypeDefinitionClassBuilder().withNestedInjectedServices()
        def projectType = new ProjectTypePluginClassBuilder(definition)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        return withProjectType(
            definition,
            projectType,
            settingsBuilder
        )
    }

    PluginBuilder withSafeProjectTypeAndMultipleInjectableDefinition() {
        def definition = new ProjectTypeDefinitionClassBuilder().withInjectedServices().withNestedInjectedServices()
        def projectType = new ProjectTypePluginClassBuilder(definition)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        return withProjectType(
            definition,
            projectType,
            settingsBuilder
        )
    }

    PluginBuilder withSafeProjectTypeAndInheritedInjectableDefinition() {
        def definition = new ProjectTypeDefinitionWithInjectableParentClassBuilder()
        def projectType = new ProjectTypePluginClassBuilder(definition)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        return withProjectType(
            definition,
            projectType,
            settingsBuilder
        )
    }

    PluginBuilder withPolyUnsafeProjectTypeDefinitionDeclaredSafe() {
        def definition = new ProjectTypeDefinitionAbstractClassBuilder().withInjectedServices()
        def projectType = new ProjectTypePluginClassBuilder(definition)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        return withProjectType(
            definition,
            projectType,
            settingsBuilder
        )
    }

    PluginBuilder withProjectTypeWithUnsafeApplyActionDeclaredUnsafe() {
        def definition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypeThatUsesUnsafeServicesClassBuilder(definition).withUnsafeApplyAction()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        return withProjectType(
            definition,
            projectType,
            settingsBuilder
        )
    }

    PluginBuilder withProjectTypeWithUnsafeApplyActionDeclaredSafe() {
        def definition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypeThatUsesUnsafeServicesClassBuilder(definition)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        return withProjectType(
            definition,
            projectType,
            settingsBuilder
        )
    }

    PluginBuilder withProjectTypeWithUnsafeApplyActionInjectingUnknownService() {
        def definition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypeThatUsesUnknownServicesClassBuilder(definition).withUnsafeApplyAction()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        return withProjectType(
            definition,
            projectType,
            settingsBuilder
        )
    }

    PluginBuilder withProjectTypeThatBindsWithClass() {
        def definition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new ProjectTypeThatBindsWithClassBuilder(definition)
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        return withProjectType(
            definition,
            projectType,
            settingsBuilder
        )
    }

    PluginBuilder withKotlinProjectTypeThatBindsWithClass() {
        def definition = new ProjectTypeDefinitionClassBuilder()
        def projectType = new KotlinProjectTypeThatBindsWithClassBuilder(definition)
        def settingsBuilder = new KotlinSettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        return withProjectType(
            definition,
            projectType,
            settingsBuilder
        )
    }


    static String getPluginsFromIncludedBuild() {
        return """
            pluginManagement {
                includeBuild("plugins")
            }
            plugins {
                id("com.example.test-software-ecosystem")
            }
        """
    }

    static String getPluginBuildScriptForJava() {
        return """

            tasks.withType(JavaCompile).configureEach {
                sourceCompatibility = "1.8"
                targetCompatibility = "1.8"
            }
        """
    }

    static String getPluginBuildScriptForKotlin() {
        return """
            import org.jetbrains.kotlin.gradle.dsl.JvmTarget

            repositories {
                mavenCentral()
            }

            kotlin {
                compilerOptions {
                    jvmTarget = JvmTarget.JVM_1_8
                }
            }

            ${pluginBuildScriptForJava}
        """
    }
}
