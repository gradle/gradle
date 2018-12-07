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

import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.attributes.Usage
import org.gradle.api.internal.artifacts.dsl.dependencies.PlatformSupport
import org.gradle.api.internal.component.UsageContext
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

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
        api.canBeConsumed
        !api.canBeResolved
        api.extendsFrom.empty

        when:
        def runtime = project.configurations.getByName(JavaPlatformPlugin.RUNTIME_CONFIGURATION_NAME)

        then:
        runtime.canBeConsumed
        !runtime.canBeResolved
        runtime.extendsFrom == [api] as Set

        when:
        def classpath = project.configurations.getByName(JavaPlatformPlugin.CLASSPATH_CONFIGURATION_NAME)

        then:
        !classpath.canBeConsumed
        classpath.canBeResolved
        classpath.extendsFrom == [runtime] as Set
        def attributes = classpath.attributes.keySet()
        attributes.size() == 1
        def usage = classpath.attributes.getAttribute(attributes[0])
        usage.name == Usage.JAVA_RUNTIME

    }

    def "adds Java library component"() {
        given:
        project.pluginManager.apply(JavaPlatformPlugin)

        project.dependencies.add(JavaPlatformPlugin.API_CONFIGURATION_NAME, "org:api1:1.0")
        project.dependencies.constraints.add(JavaPlatformPlugin.API_CONFIGURATION_NAME, "org:api2:2.0")

        project.dependencies.add(JavaPlatformPlugin.RUNTIME_CONFIGURATION_NAME, "org:runtime1:1.0")
        project.dependencies.constraints.add(JavaPlatformPlugin.RUNTIME_CONFIGURATION_NAME, "org:runtime2:2.0")

        when:
        def javaPlatform = project.components.getByName("javaPlatform")
        UsageContext apiUsage = javaPlatform.usages[0]
        UsageContext runtimeUsage = javaPlatform.usages[1]

        then:
        runtimeUsage.dependencies.size() == 2
        runtimeUsage.dependencies == project.configurations.getByName(JavaPlatformPlugin.RUNTIME_CONFIGURATION_NAME).allDependencies.withType(ModuleDependency)
        runtimeUsage.dependencyConstraints.size() == 2
        runtimeUsage.dependencyConstraints == project.configurations.getByName(JavaPlatformPlugin.RUNTIME_CONFIGURATION_NAME).allDependencyConstraints
        runtimeUsage.attributes.keySet() == [Usage.USAGE_ATTRIBUTE, PlatformSupport.COMPONENT_CATEGORY] as Set
        runtimeUsage.attributes.getAttribute(Usage.USAGE_ATTRIBUTE).name == Usage.JAVA_RUNTIME
        runtimeUsage.attributes.getAttribute(PlatformSupport.COMPONENT_CATEGORY) == PlatformSupport.REGULAR_PLATFORM

        apiUsage.dependencies.size() == 1
        apiUsage.dependencies == project.configurations.getByName(JavaPlatformPlugin.API_CONFIGURATION_NAME).allDependencies.withType(ModuleDependency)
        apiUsage.dependencyConstraints.size() == 1
        apiUsage.dependencyConstraints == project.configurations.getByName(JavaPlatformPlugin.API_CONFIGURATION_NAME).allDependencyConstraints
        apiUsage.attributes.keySet() == [Usage.USAGE_ATTRIBUTE, PlatformSupport.COMPONENT_CATEGORY] as Set
        apiUsage.attributes.getAttribute(Usage.USAGE_ATTRIBUTE).name == Usage.JAVA_API
        apiUsage.attributes.getAttribute(PlatformSupport.COMPONENT_CATEGORY) == PlatformSupport.REGULAR_PLATFORM
    }

}
