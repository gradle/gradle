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

package org.gradle.api.plugins.jvm.internal

import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.DocsType
import org.gradle.api.attributes.Usage
import org.gradle.api.internal.tasks.JvmConstants
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

/**
 * Tests {@link DefaultJvmFeature}.
 */
class DefaultJvmFeatureTest extends AbstractProjectBuilderSpec {

    def "can create a single main feature with a main source set"() {
        given:
        project.plugins.apply(JavaBasePlugin)
        def ext = project.getExtensions().getByType(JavaPluginExtension.class)

        when:
        def feature = createFeature("main")

        then:
        feature.name == "main"
        feature.sourceSet == ext.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
        feature.runtimeClasspathConfiguration == project.configurations.getByName(JvmConstants.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
        feature.compileClasspathConfiguration == project.configurations.getByName(JvmConstants.COMPILE_CLASSPATH_CONFIGURATION_NAME)
        feature.runtimeElementsConfiguration == project.configurations.getByName(JvmConstants.RUNTIME_ELEMENTS_CONFIGURATION_NAME)
        feature.apiElementsConfiguration == project.configurations.getByName(JvmConstants.API_ELEMENTS_CONFIGURATION_NAME)
        feature.implementationConfiguration == project.configurations.getByName(JvmConstants.IMPLEMENTATION_CONFIGURATION_NAME)
        feature.runtimeOnlyConfiguration == project.configurations.getByName(JvmConstants.RUNTIME_ONLY_CONFIGURATION_NAME)
        feature.compileOnlyConfiguration == project.configurations.getByName(JvmConstants.COMPILE_ONLY_CONFIGURATION_NAME)
        project.configurations.getByName(JvmConstants.ANNOTATION_PROCESSOR_CONFIGURATION_NAME)
        feature.compileJavaTask.get() == project.tasks.getByName(JvmConstants.COMPILE_JAVA_TASK_NAME)
        feature.jarTask.get() == project.tasks.getByName(JvmConstants.JAR_TASK_NAME)
        project.tasks.getByName(JvmConstants.JAVADOC_TASK_NAME)
        project.tasks.getByName(JvmConstants.PROCESS_RESOURCES_TASK_NAME)
    }

    def "can create a single non-main feature with a non-main source set"() {
        given:
        project.plugins.apply(JavaBasePlugin)
        def ext = project.getExtensions().getByType(JavaPluginExtension.class)

        when:
        def feature = createFeature("feature")

        then:
        feature.name == "feature"
        feature.sourceSet == ext.sourceSets.getByName('feature')
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

    def "can configure javadoc jar variant"() {
        given:
        project.plugins.apply(JavaBasePlugin)

        when:
        def feature = createFeature("main")
        feature.withJavadocJar()

        then:
        def configuration = project.configurations.getByName(JvmConstants.JAVADOC_ELEMENTS_CONFIGURATION_NAME)
        !configuration.visible
        !configuration.canBeResolved
        configuration.canBeConsumed
        (configuration.attributes.getAttribute(Usage.USAGE_ATTRIBUTE) as Usage).name == Usage.JAVA_RUNTIME
        (configuration.attributes.getAttribute(Category.CATEGORY_ATTRIBUTE) as Category).name == Category.DOCUMENTATION
        (configuration.attributes.getAttribute(Bundling.BUNDLING_ATTRIBUTE) as Bundling).name == Bundling.EXTERNAL
        (configuration.attributes.getAttribute(DocsType.DOCS_TYPE_ATTRIBUTE) as DocsType).name == DocsType.JAVADOC
        project.tasks.getByName(feature.sourceSet.javadocJarTaskName)
    }

    def "can configure sources jar variant"() {
        given:
        project.plugins.apply(JavaBasePlugin)

        when:
        def feature = createFeature("main")
        feature.withSourcesJar()

        then:
        def configuration = project.configurations.getByName(JvmConstants.SOURCES_ELEMENTS_CONFIGURATION_NAME)
        !configuration.visible
        !configuration.canBeResolved
        configuration.canBeConsumed
        (configuration.attributes.getAttribute(Usage.USAGE_ATTRIBUTE) as Usage).name == Usage.JAVA_RUNTIME
        (configuration.attributes.getAttribute(Category.CATEGORY_ATTRIBUTE) as Category).name == Category.DOCUMENTATION
        (configuration.attributes.getAttribute(Bundling.BUNDLING_ATTRIBUTE) as Bundling).name == Bundling.EXTERNAL
        (configuration.attributes.getAttribute(DocsType.DOCS_TYPE_ATTRIBUTE) as DocsType).name == DocsType.SOURCES
        project.tasks.getByName(feature.sourceSet.sourcesJarTaskName)
    }

    def "can create multiple features in a project without adding them to a component"() {
        given:
        project.plugins.apply(JavaBasePlugin)
        def ext = project.getExtensions().getByType(JavaPluginExtension)

        SourceSet one = ext.getSourceSets().create("one")
        SourceSet two = ext.getSourceSets().create("two")

        when:
        // The constructor and `with` methods have side effects, like creating domain objects in project-scope containers
        def f1 = new DefaultJvmFeature("feature1", one, Collections.emptyList(), project, false, false)
        def f2 = new DefaultJvmFeature("feature2", two, Collections.emptyList(), project, false, false)

        f1.withJavadocJar()
        f1.withSourcesJar()
        f1.withApi()
        f1.withSourceElements()

        f2.withJavadocJar()
        f2.withSourcesJar()
        f2.withApi()
        f2.withSourceElements()

        then:
        noExceptionThrown()
    }

    private JvmFeatureInternal createFeature(String name, String sourceSetName = name) {
        SourceSet sourceSet = project.getExtensions().getByType(JavaPluginExtension).getSourceSets().create(sourceSetName)
        return new DefaultJvmFeature(
            name,
            sourceSet,
            Collections.emptyList(),
            project,
            false,
            false
        )
    }
}
