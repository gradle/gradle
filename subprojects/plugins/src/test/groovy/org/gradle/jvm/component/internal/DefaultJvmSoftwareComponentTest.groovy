/*
 * Copyright 2022 the original author or authors.
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
import org.gradle.api.internal.tasks.JvmConstants
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.reflect.ObjectInstantiationException
import org.gradle.api.tasks.SourceSet
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

/**
 * Tests {@link DefaultJvmSoftwareComponent}.
 */
class DefaultJvmSoftwareComponentTest extends AbstractProjectBuilderSpec {

    def "configures the project when using the main source set"() {
        when:
        project.plugins.apply(JavaBasePlugin)
        def ext = project.getExtensions().getByType(JavaPluginExtension.class)

        // Verify the JavaBasePlugin does not create the below tested objects so we can
        // ensure the DefaultJvmSoftwareComponent is actually creating these domain objects.
        then:
        ext.sourceSets.findByName(SourceSet.MAIN_SOURCE_SET_NAME) == null
        project.configurations.findByName(JvmConstants.RUNTIME_CLASSPATH_CONFIGURATION_NAME) == null
        project.configurations.findByName(JvmConstants.COMPILE_CLASSPATH_CONFIGURATION_NAME) == null
        project.configurations.findByName(JvmConstants.RUNTIME_ELEMENTS_CONFIGURATION_NAME) == null
        project.configurations.findByName(JvmConstants.API_ELEMENTS_CONFIGURATION_NAME) == null
        project.configurations.findByName('mainSourceElements') == null
        project.configurations.findByName(JvmConstants.IMPLEMENTATION_CONFIGURATION_NAME) == null
        project.configurations.findByName(JvmConstants.RUNTIME_ONLY_CONFIGURATION_NAME) == null
        project.configurations.findByName(JvmConstants.COMPILE_ONLY_CONFIGURATION_NAME) == null
        project.configurations.findByName(JvmConstants.ANNOTATION_PROCESSOR_CONFIGURATION_NAME) == null
        project.tasks.findByName(JvmConstants.COMPILE_JAVA_TASK_NAME) == null
        project.tasks.findByName(JvmConstants.JAR_TASK_NAME) == null
        project.tasks.findByName(JvmConstants.JAVADOC_TASK_NAME) == null
        project.tasks.findByName(JvmConstants.PROCESS_RESOURCES_TASK_NAME) == null

        when:
        def component = project.objects.newInstance(DefaultJvmSoftwareComponent, "name", "main")

        then:
        component.sourceSet == ext.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
        component.mainOutput == component.getSourceSet().getOutput()
        component.runtimeClasspathConfiguration == project.configurations.getByName(JvmConstants.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
        component.compileClasspathConfiguration == project.configurations.getByName(JvmConstants.COMPILE_CLASSPATH_CONFIGURATION_NAME)
        component.runtimeElementsConfiguration == project.configurations.getByName(JvmConstants.RUNTIME_ELEMENTS_CONFIGURATION_NAME)
        component.apiElementsConfiguration == project.configurations.getByName(JvmConstants.API_ELEMENTS_CONFIGURATION_NAME)
        project.configurations.getByName('mainSourceElements')
        component.implementationConfiguration == project.configurations.getByName(JvmConstants.IMPLEMENTATION_CONFIGURATION_NAME)
        component.runtimeOnlyConfiguration == project.configurations.getByName(JvmConstants.RUNTIME_ONLY_CONFIGURATION_NAME)
        component.compileOnlyConfiguration == project.configurations.getByName(JvmConstants.COMPILE_ONLY_CONFIGURATION_NAME)
        project.configurations.getByName(JvmConstants.ANNOTATION_PROCESSOR_CONFIGURATION_NAME)
        component.mainCompileJavaTask.get() == project.tasks.getByName(JvmConstants.COMPILE_JAVA_TASK_NAME)
        component.mainJarTask.get() == project.tasks.getByName(JvmConstants.JAR_TASK_NAME)
        project.tasks.getByName(JvmConstants.JAVADOC_TASK_NAME)
        project.tasks.getByName(JvmConstants.PROCESS_RESOURCES_TASK_NAME)
    }

    def "configures the project when using the non-main source set"() {
        when:
        project.plugins.apply(JavaBasePlugin)
        def ext = project.getExtensions().getByType(JavaPluginExtension.class)

        // Verify the JavaBasePlugin does not create the below tested objects so we can
        // ensure the DefaultJvmSoftwareComponent is actually creating these domain objects.
        then:
        ext.sourceSets.findByName("feature") == null
        project.configurations.findByName('featureRuntimeClasspath') == null
        project.configurations.findByName('featureCompileClasspath') == null
        project.configurations.findByName('featureRuntimeElements') == null
        project.configurations.findByName('featureApiElements') == null
        project.configurations.findByName('featureSourceElements') == null
        project.configurations.findByName('featureImplementation') == null
        project.configurations.findByName('featureRuntimeOnly') == null
        project.configurations.findByName('featureCompileOnly') == null
        project.configurations.findByName('featureAnnotationProcessor') == null
        project.tasks.findByName('compileFeatureJava') == null
        project.tasks.findByName('featureJar') == null
        project.tasks.findByName('featureJavadoc') == null
        project.tasks.findByName('processFeatureResources') == null

        when:
        def component = project.objects.newInstance(DefaultJvmSoftwareComponent, "name", "feature")

        then:
        component.sourceSet == ext.sourceSets.getByName('feature')
        component.mainOutput == component.getSourceSet().getOutput()
        component.runtimeClasspathConfiguration == project.configurations.getByName('featureRuntimeClasspath')
        component.compileClasspathConfiguration == project.configurations.getByName('featureCompileClasspath')
        component.runtimeElementsConfiguration == project.configurations.getByName('featureRuntimeElements')
        component.apiElementsConfiguration == project.configurations.getByName('featureApiElements')
        project.configurations.getByName('featureSourceElements')
        component.implementationConfiguration == project.configurations.getByName('featureImplementation')
        component.runtimeOnlyConfiguration == project.configurations.getByName('featureRuntimeOnly')
        component.compileOnlyConfiguration == project.configurations.getByName('featureCompileOnly')
        project.configurations.getByName('featureAnnotationProcessor')
        component.mainCompileJavaTask.get() == project.tasks.getByName('compileFeatureJava')
        component.mainJarTask.get() == project.tasks.getByName('featureJar')
        project.tasks.getByName('featureJavadoc')
        project.tasks.getByName('processFeatureResources')
    }

    def "can create multiple component instances"() {
        given:
        project.plugins.apply(JavaBasePlugin)
        def ext = project.getExtensions().getByType(JavaPluginExtension.class)

        when:
        project.objects.newInstance(DefaultJvmSoftwareComponent, "name", "main")
        project.objects.newInstance(DefaultJvmSoftwareComponent, "name", "feature1")
        project.objects.newInstance(DefaultJvmSoftwareComponent, "name", "feature2")

        then:
        ext.sourceSets.getByName('main')
        ext.sourceSets.getByName('feature1')
        ext.sourceSets.getByName('feature2')
    }

    def "cannot create multiple component instances with the same source set name"() {
        given:
        project.plugins.apply(JavaBasePlugin)
        def ext = project.getExtensions().getByType(JavaPluginExtension.class)

        when:
        project.objects.newInstance(DefaultJvmSoftwareComponent, "name1", "feature")
        project.objects.newInstance(DefaultJvmSoftwareComponent, "name2", "feature")

        then:
        def e = thrown(ObjectInstantiationException)
        e.cause.message == "Cannot create multiple instances of DefaultJvmSoftwareComponent with source set name 'feature'."
    }

    def "can configure javadoc jar variant"() {
        when:
        project.plugins.apply(JavaBasePlugin)
        def ext = project.getExtensions().getByType(JavaPluginExtension.class)

        // Verify the JavaBasePlugin does not create the below tested objects so we can
        // ensure the DefaultJvmSoftwareComponent is actually creating these domain objects.
        then:
        project.configurations.findByName(JvmConstants.JAVADOC_ELEMENTS_CONFIGURATION_NAME) == null
        project.tasks.findByName("javadoc") == null

        when:
        def component = project.objects.newInstance(DefaultJvmSoftwareComponent, "name", "main")
        component.enableJavadocJarVariant()

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
        project.tasks.getByName(component.sourceSet.javadocJarTaskName)
        component.usages.find { it.name == JvmConstants.JAVADOC_ELEMENTS_CONFIGURATION_NAME}
    }

    def "can configure sources jar variant"() {
        when:
        project.plugins.apply(JavaBasePlugin)
        def ext = project.getExtensions().getByType(JavaPluginExtension.class)

        // Verify the JavaBasePlugin does not create the below tested objects so we can
        // ensure the DefaultJvmSoftwareComponent is actually creating these domain objects.
        then:
        project.configurations.findByName(JvmConstants.SOURCES_ELEMENTS_CONFIGURATION_NAME) == null
        project.tasks.findByName("sources") == null

        when:
        def component = project.objects.newInstance(DefaultJvmSoftwareComponent, "name", "main")
        component.enableSourcesJarVariant()

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
        project.tasks.getByName(component.sourceSet.sourcesJarTaskName)
        component.usages.find { it.name == JvmConstants.SOURCES_ELEMENTS_CONFIGURATION_NAME}
    }

    def "has expected variants"() {
        when:
        project.plugins.apply(JavaBasePlugin)
        def ext = project.getExtensions().getByType(JavaPluginExtension.class)
        def component = project.objects.newInstance(DefaultJvmSoftwareComponent, "name", "main")

        then:
        component.usages*.name == ["apiElements", "runtimeElements"]
    }
}
