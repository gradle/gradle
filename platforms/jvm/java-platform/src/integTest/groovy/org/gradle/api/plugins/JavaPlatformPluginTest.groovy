/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.plugins

import org.gradle.api.ProjectConfigurationException
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.component.SoftwareComponentVariant
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import spock.lang.Unroll

class JavaPlatformPluginTest extends AbstractProjectBuilderSpec {
    def "applies base plugin"() {
        when:
        project.pluginManager.apply(JavaPlatformPlugin)

        then:
        project.plugins.findPlugin(BasePlugin)
    }

    def "adds configurations to the project"() {
        given:
        project.pluginManager.apply(JavaPlatformPlugin)

        when:
        def api = project.configurations.getByName(JavaPlatformPlugin.API_CONFIGURATION_NAME)

        then:
        !api.canBeConsumed
        !api.canBeResolved
        api.extendsFrom.empty

        when:
        def runtime = project.configurations.getByName(JavaPlatformPlugin.RUNTIME_CONFIGURATION_NAME)

        then:
        !runtime.canBeConsumed
        !runtime.canBeResolved
        runtime.extendsFrom == [api] as Set

        when:
        def apiElements = project.configurations.getByName(JavaPlatformPlugin.API_ELEMENTS_CONFIGURATION_NAME)

        then:
        apiElements.canBeConsumed
        !apiElements.canBeResolved
        apiElements.extendsFrom == [api] as Set

        when:
        def runtimeElements = project.configurations.getByName(JavaPlatformPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME)

        then:
        runtimeElements.canBeConsumed
        !runtimeElements.canBeResolved
        runtimeElements.extendsFrom == [runtime] as Set

        when:
        def enforcedApiElements = project.configurations.getByName(JavaPlatformPlugin.ENFORCED_API_ELEMENTS_CONFIGURATION_NAME)

        then:
        enforcedApiElements.canBeConsumed
        !enforcedApiElements.canBeResolved
        enforcedApiElements.extendsFrom == [api] as Set

        when:
        def enforcedRuntimeElements = project.configurations.getByName(JavaPlatformPlugin.ENFORCED_RUNTIME_ELEMENTS_CONFIGURATION_NAME)

        then:
        enforcedRuntimeElements.canBeConsumed
        !enforcedRuntimeElements.canBeResolved
        enforcedRuntimeElements.extendsFrom == [runtime] as Set


        when:
        def classpath = project.configurations.getByName(JavaPlatformPlugin.CLASSPATH_CONFIGURATION_NAME)

        then:
        !classpath.canBeConsumed
        classpath.canBeResolved
        classpath.extendsFrom == [runtimeElements] as Set
        def attributes = classpath.attributes.keySet()
        attributes.size() == 2
        def usage = classpath.attributes.getAttribute(Usage.USAGE_ATTRIBUTE)
        usage.name == Usage.JAVA_RUNTIME
        def format = classpath.attributes.getAttribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE)
        format.name == LibraryElements.JAR

    }

    def "adds Java library component"() {
        given:
        project.pluginManager.apply(JavaPlatformPlugin)

        project.dependencies.add(JavaPlatformPlugin.API_CONFIGURATION_NAME, "org:api1:1.0")
        project.dependencies.constraints.add(JavaPlatformPlugin.API_CONFIGURATION_NAME, "org:api2:2.0")

        project.dependencies.add(JavaPlatformPlugin.RUNTIME_CONFIGURATION_NAME, "org:runtime1:1.0")
        project.dependencies.constraints.add(JavaPlatformPlugin.RUNTIME_CONFIGURATION_NAME, "org:runtime2:2.0")

        when:
        SoftwareComponentInternal javaPlatform = project.components.getByName("javaPlatform")
        SoftwareComponentVariant apiVariant = javaPlatform.usages[0]
        SoftwareComponentVariant runtimeVariant = javaPlatform.usages[1]

        then:
        runtimeVariant.dependencies.size() == 2
        runtimeVariant.dependencies == project.configurations.getByName(JavaPlatformPlugin.RUNTIME_CONFIGURATION_NAME).allDependencies.withType(ModuleDependency)
        runtimeVariant.dependencyConstraints.size() == 2
        runtimeVariant.dependencyConstraints == project.configurations.getByName(JavaPlatformPlugin.RUNTIME_CONFIGURATION_NAME).allDependencyConstraints
        runtimeVariant.attributes.keySet() == [Usage.USAGE_ATTRIBUTE, Category.CATEGORY_ATTRIBUTE] as Set
        runtimeVariant.attributes.getAttribute(Usage.USAGE_ATTRIBUTE).name == Usage.JAVA_RUNTIME
        runtimeVariant.attributes.getAttribute(Category.CATEGORY_ATTRIBUTE).name == Category.REGULAR_PLATFORM

        apiVariant.dependencies.size() == 1
        apiVariant.dependencies == project.configurations.getByName(JavaPlatformPlugin.API_CONFIGURATION_NAME).allDependencies.withType(ModuleDependency)
        apiVariant.dependencyConstraints.size() == 1
        apiVariant.dependencyConstraints == project.configurations.getByName(JavaPlatformPlugin.API_CONFIGURATION_NAME).allDependencyConstraints
        apiVariant.attributes.keySet() == [Usage.USAGE_ATTRIBUTE, Category.CATEGORY_ATTRIBUTE] as Set
        apiVariant.attributes.getAttribute(Usage.USAGE_ATTRIBUTE).name == Usage.JAVA_API
        apiVariant.attributes.getAttribute(Category.CATEGORY_ATTRIBUTE).name == Category.REGULAR_PLATFORM
    }

    @Unroll("cannot add a dependency to the #configuration configuration by default")
    def "adding a dependency is not allowed by default"() {
        given:
        project.pluginManager.apply(JavaPlatformPlugin)
        project.dependencies.add(JavaPlatformPlugin.API_CONFIGURATION_NAME, "org:api1:1.0")
        project.dependencies.constraints.add(JavaPlatformPlugin.RUNTIME_CONFIGURATION_NAME, "org:api2:1.0")

        when:
        project.evaluate()

        then:
        ProjectConfigurationException ex = thrown()

        and:
        ex.cause.message.contains("Found dependencies in the 'api' configuration.")

        where:
        configuration << [
                JavaPlatformPlugin.API_CONFIGURATION_NAME,
                JavaPlatformPlugin.RUNTIME_CONFIGURATION_NAME
        ]
    }


    @Unroll("can add a dependency to the #configuration configuration when extension configured")
    def "adding a dependency is allowed when activated on the project extension"() {
        given:
        project.pluginManager.apply(JavaPlatformPlugin)
        project.extensions.javaPlatform.allowDependencies()
        project.dependencies.add(JavaPlatformPlugin.API_CONFIGURATION_NAME, "org:api1:1.0")

        when:
        project.evaluate()

        then:
        noExceptionThrown()

        where:
        configuration << [
                JavaPlatformPlugin.API_CONFIGURATION_NAME,
                JavaPlatformPlugin.RUNTIME_CONFIGURATION_NAME
        ]
    }

}
