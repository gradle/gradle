/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.jvm.component.internal

import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.DocsType
import org.gradle.api.attributes.Usage
import org.gradle.api.component.internal.ConfigurationBackedConsumableVariant
import org.gradle.api.internal.artifacts.configurations.ConfigurationRoles
import org.gradle.api.internal.tasks.JvmConstants
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

/**
 * Tests {@link JvmFeature}.
 */
class DefaultJvmFeatureTest extends AbstractProjectBuilderSpec {

    def sourceSets

    def setup() {
        project.plugins.apply(JavaBasePlugin)
        sourceSets = project.getExtensions().getByType(JavaPluginExtension.class).sourceSets
    }

    def "configures the project when using the main source set"() {
        when:
        def mainFeature = newFeature("main")

        then:
        mainFeature instanceof DefaultJvmFeature
        mainFeature.sourceSet == sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
        mainFeature.runtimeClasspathConfiguration == project.configurations.getByName(JvmConstants.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
        mainFeature.compileClasspathConfiguration == project.configurations.getByName(JvmConstants.COMPILE_CLASSPATH_CONFIGURATION_NAME)
        mainFeature.runtimeElementsConfiguration == project.configurations.getByName(JvmConstants.RUNTIME_ELEMENTS_CONFIGURATION_NAME)
        mainFeature.apiElementsConfiguration == project.configurations.getByName(JvmConstants.API_ELEMENTS_CONFIGURATION_NAME)
        mainFeature.implementationConfiguration == project.configurations.getByName(JvmConstants.IMPLEMENTATION_CONFIGURATION_NAME)
        mainFeature.runtimeOnlyConfiguration == project.configurations.getByName(JvmConstants.RUNTIME_ONLY_CONFIGURATION_NAME)
        mainFeature.compileOnlyConfiguration == project.configurations.getByName(JvmConstants.COMPILE_ONLY_CONFIGURATION_NAME)
        project.configurations.getByName(JvmConstants.ANNOTATION_PROCESSOR_CONFIGURATION_NAME)
        mainFeature.compileJavaTask.get() == project.tasks.getByName(JvmConstants.COMPILE_JAVA_TASK_NAME)
        mainFeature.jarTask.get() == project.tasks.getByName(JvmConstants.JAR_TASK_NAME)
        project.tasks.getByName(JvmConstants.JAVADOC_TASK_NAME)
        project.tasks.getByName(JvmConstants.PROCESS_RESOURCES_TASK_NAME)
    }

    def "configures the project when using the non-main source set"() {
        when:
        def feature = newFeature("feature")

        then:
        feature instanceof DefaultJvmFeature
        feature.sourceSet == sourceSets.getByName('feature')
        feature.runtimeClasspathConfiguration == project.configurations.getByName('featureRuntimeClasspath')
        feature.compileClasspathConfiguration == project.configurations.getByName('featureCompileClasspath')
        feature.runtimeElementsConfiguration == project.configurations.getByName('featureRuntimeElements')
        feature.apiElementsConfiguration == project.configurations.getByName('featureApiElements')
        feature.implementationConfiguration == project.configurations.getByName('featureImplementation')
        feature.runtimeOnlyConfiguration == project.configurations.getByName('featureRuntimeOnly')
        feature.compileOnlyConfiguration == project.configurations.getByName('featureCompileOnly')
        project.configurations.getByName('featureAnnotationProcessor')
        feature.compileJavaTask.get() == project.tasks.getByName('compileFeatureJava')
        feature.jarTask.get() == project.tasks.getByName('featureJar')
        project.tasks.getByName('featureJavadoc')
        project.tasks.getByName('processFeatureResources')
    }

    def "can configure an api"() {
        when:
        def feature = newFeature("feature")

        then:
        feature.apiConfiguration == null
        feature.compileOnlyApiConfiguration == null
        feature.apiElementsConfiguration.extendsFrom.isEmpty()
        feature.compileOnlyConfiguration.extendsFrom.isEmpty()

        when:
        feature.withApi()

        then:
        feature.apiConfiguration == project.configurations.getByName('featureApi')
        feature.compileOnlyApiConfiguration == project.configurations.getByName('featureCompileOnlyApi')
        feature.apiElementsConfiguration.extendsFrom == [feature.apiConfiguration, feature.compileOnlyApiConfiguration] as Set
        feature.compileOnlyConfiguration.extendsFrom == [feature.compileOnlyApiConfiguration] as Set
    }

    def "can configure javadoc jar variant"() {
        when:
        def component = project.objects.newInstance(DefaultJvmSoftwareComponent, "name")
        def mainFeature = component.features.create("main", JvmFeature)
        mainFeature.withJavadocJar()

        then:
        def configuration = project.configurations.getByName(JvmConstants.JAVADOC_ELEMENTS_CONFIGURATION_NAME)
        configuration != null
        !configuration.visible
        !configuration.canBeResolved
        configuration.canBeConsumed
        (configuration.attributes.getAttribute(Usage.USAGE_ATTRIBUTE) as Usage).name == Usage.JAVA_RUNTIME
        (configuration.attributes.getAttribute(Category.CATEGORY_ATTRIBUTE) as Category).name == Category.DOCUMENTATION
        (configuration.attributes.getAttribute(Bundling.BUNDLING_ATTRIBUTE) as Bundling).name == Bundling.EXTERNAL
        (configuration.attributes.getAttribute(DocsType.DOCS_TYPE_ATTRIBUTE) as DocsType).name == DocsType.JAVADOC
        project.tasks.getByName(mainFeature.sourceSet.javadocJarTaskName)
        component.usages.find { it.name == JvmConstants.JAVADOC_ELEMENTS_CONFIGURATION_NAME}
    }

    def "can configure sources jar variant"() {
        when:
        def component = project.objects.newInstance(DefaultJvmSoftwareComponent, "name")
        def mainFeature = component.features.create("main", JvmFeature)
        mainFeature.withSourcesJar()

        then:
        def configuration = project.configurations.getByName(JvmConstants.SOURCES_ELEMENTS_CONFIGURATION_NAME)
        configuration != null
        !configuration.visible
        !configuration.canBeResolved
        configuration.canBeConsumed
        (configuration.attributes.getAttribute(Usage.USAGE_ATTRIBUTE) as Usage).name == Usage.JAVA_RUNTIME
        (configuration.attributes.getAttribute(Category.CATEGORY_ATTRIBUTE) as Category).name == Category.DOCUMENTATION
        (configuration.attributes.getAttribute(Bundling.BUNDLING_ATTRIBUTE) as Bundling).name == Bundling.EXTERNAL
        (configuration.attributes.getAttribute(DocsType.DOCS_TYPE_ATTRIBUTE) as DocsType).name == DocsType.SOURCES
        project.tasks.getByName(mainFeature.sourceSet.sourcesJarTaskName)
        component.usages.find { it.name == JvmConstants.SOURCES_ELEMENTS_CONFIGURATION_NAME}
    }

    def "can instantiate configuration backed variants"() {
        when:
        def feature = newFeature("feature")
        def variant = feature.variants.create("variant", ConfigurationBackedConsumableVariant)

        then:
        variant.configuration != null
        variant.configuration == project.configurations.variant
    }

    JvmFeature newFeature(String featureName) {
        return project.objects.newInstance(DefaultJvmFeature.class,
            featureName, sourceSets.create(featureName), [], "description",
            project, ConfigurationRoles.CONSUMABLE, false
        )
    }
}
