/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.tasks.JvmConstants
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil

import static org.gradle.util.internal.WrapUtil.toSet

class JavaLibraryPluginTest extends AbstractProjectBuilderSpec {
    def "applies Java plugin"() {
        when:
        project.pluginManager.apply(JavaLibraryPlugin)

        then:
        project.plugins.findPlugin(JavaPlugin)
    }

    def "adds configurations to the project"() {
        given:
        project.pluginManager.apply(JavaLibraryPlugin)

        when:
        def api = project.configurations.getByName(JvmConstants.API_CONFIGURATION_NAME)

        then:
        !api.visible
        api.extendsFrom == [] as Set
        !api.canBeConsumed
        !api.canBeResolved

        when:
        def implementation = project.configurations.getByName(JvmConstants.IMPLEMENTATION_CONFIGURATION_NAME)

        then:
        !implementation.visible
        implementation.extendsFrom == [api] as Set
        !implementation.canBeConsumed
        !implementation.canBeResolved

        when:
        def runtimeOnly = project.configurations.getByName(JvmConstants.RUNTIME_ONLY_CONFIGURATION_NAME)

        then:
        runtimeOnly.transitive
        !runtimeOnly.visible
        !runtimeOnly.canBeConsumed
        !runtimeOnly.canBeResolved
        runtimeOnly.extendsFrom == [] as Set

        when:
        def runtimeElements = project.configurations.getByName(JvmConstants.RUNTIME_ELEMENTS_CONFIGURATION_NAME)

        then:
        runtimeElements.transitive
        !runtimeElements.visible
        runtimeElements.canBeConsumed
        !runtimeElements.canBeResolved
        runtimeElements.extendsFrom == [implementation, runtimeOnly] as Set

        when:
        def runtimeClasspath = project.configurations.getByName(JvmConstants.RUNTIME_CLASSPATH_CONFIGURATION_NAME)

        then:
        runtimeClasspath.transitive
        !runtimeClasspath.visible
        !runtimeClasspath.canBeConsumed
        runtimeClasspath.canBeResolved
        runtimeClasspath.extendsFrom == [runtimeOnly, implementation] as Set

        when:
        def compileOnlyApi = project.configurations.getByName(JvmConstants.COMPILE_ONLY_API_CONFIGURATION_NAME)

        then:
        compileOnlyApi.extendsFrom == [] as Set
        !compileOnlyApi.visible
        compileOnlyApi.transitive
        !compileOnlyApi.canBeConsumed
        !compileOnlyApi.canBeResolved

        when:
        def compileOnly = project.configurations.getByName(JvmConstants.COMPILE_ONLY_CONFIGURATION_NAME)

        then:
        compileOnly.extendsFrom == [compileOnlyApi] as Set
        !compileOnly.visible
        compileOnly.transitive

        when:
        def compileClasspath = project.configurations.getByName(JvmConstants.COMPILE_CLASSPATH_CONFIGURATION_NAME)

        then:
        compileClasspath.extendsFrom == toSet(compileOnly, implementation)
        !compileClasspath.visible
        compileClasspath.transitive

        when:
        def apiElements = project.configurations.getByName(JvmConstants.API_ELEMENTS_CONFIGURATION_NAME)

        then:
        !apiElements.visible
        apiElements.extendsFrom == [api, compileOnlyApi] as Set
        apiElements.canBeConsumed
        !apiElements.canBeResolved

        when:
        def testImplementation = project.configurations.getByName(JvmConstants.TEST_IMPLEMENTATION_CONFIGURATION_NAME)

        then:
        testImplementation.extendsFrom == toSet(implementation)
        !testImplementation.visible
        !testImplementation.canBeConsumed
        !testImplementation.canBeResolved

        when:
        def testRuntimeOnly = project.configurations.getByName(JvmConstants.TEST_RUNTIME_ONLY_CONFIGURATION_NAME)

        then:
        testRuntimeOnly.transitive
        !testRuntimeOnly.visible
        !testRuntimeOnly.canBeConsumed
        !testRuntimeOnly.canBeResolved
        testRuntimeOnly.extendsFrom == [runtimeOnly] as Set

        when:
        def testCompileOnly = project.configurations.getByName(JvmConstants.TEST_COMPILE_ONLY_CONFIGURATION_NAME)

        then:
        testCompileOnly.extendsFrom == toSet(compileOnlyApi)
        !testCompileOnly.visible
        !testRuntimeOnly.canBeConsumed
        !testRuntimeOnly.canBeResolved
        testCompileOnly.transitive

        when:
        def testCompileClasspath = project.configurations.getByName(JvmConstants.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME)

        then:
        testCompileClasspath.extendsFrom == toSet(testCompileOnly, testImplementation)
        !testCompileClasspath.visible
        testCompileClasspath.transitive

        when:
        def defaultConfig = project.configurations.getByName(Dependency.DEFAULT_CONFIGURATION)

        then:
        defaultConfig.extendsFrom == toSet(runtimeElements)
    }

    def "can declare API and implementation dependencies [compileClasspathPackaging=#compileClasspathPackaging]"() {
        if (compileClasspathPackaging) {
            System.setProperty(JavaBasePlugin.COMPILE_CLASSPATH_PACKAGING_SYSTEM_PROPERTY, "true")
        }

        given:
        def commonProject = TestUtil.createChildProject(project, "common")
        def toolsProject = TestUtil.createChildProject(project, "tools")
        def internalProject = TestUtil.createChildProject(project, "internal")

        project.pluginManager.apply(JavaPlugin)
        commonProject.pluginManager.apply(JavaLibraryPlugin)
        toolsProject.pluginManager.apply(JavaLibraryPlugin)
        internalProject.pluginManager.apply(JavaLibraryPlugin)

        when:
        project.dependencies {
            implementation commonProject
        }
        commonProject.dependencies {
            api toolsProject
            implementation internalProject
        }

        def task = project.tasks.compileJava

        then:
        task.taskDependencies.getDependencies(task)*.path as Set == [":common:$producingTask", ":tools:$producingTask"] as Set<String>

        when:
        task = commonProject.tasks.compileJava

        then:
        task.taskDependencies.getDependencies(task)*.path as Set == [":tools:$producingTask", ":internal:$producingTask"] as Set<String>

        cleanup:
        System.setProperty(JavaBasePlugin.COMPILE_CLASSPATH_PACKAGING_SYSTEM_PROPERTY, "")

        where:
        compileClasspathPackaging | producingTask
        true                      | 'jar'
        false                     | 'compileJava'
    }

    def "adds Java library component"() {
        given:
        project.pluginManager.apply(JavaLibraryPlugin)
        project.dependencies.add(JvmConstants.API_CONFIGURATION_NAME, "org:api1:1.0")
        project.dependencies.constraints.add(JvmConstants.API_CONFIGURATION_NAME, "org:api2:2.0")
        project.dependencies.add(JvmConstants.IMPLEMENTATION_CONFIGURATION_NAME, "org:impl1:1.0")
        project.dependencies.constraints.add(JvmConstants.IMPLEMENTATION_CONFIGURATION_NAME, "org:impl2:2.0")

        when:
        def jarTask = project.tasks.getByName(JvmConstants.JAR_TASK_NAME)
        SoftwareComponentInternal javaLibrary = project.components.getByName(JvmConstants.JAVA_MAIN_COMPONENT_NAME)
        def runtimeVariant = javaLibrary.usages.find { it.name == 'runtimeElements' }
        def apiVariant = javaLibrary.usages.find { it.name == 'apiElements' }

        then:
        runtimeVariant.artifacts.collect {it.file} == [jarTask.archiveFile.get().asFile]
        runtimeVariant.dependencies.size() == 2
        runtimeVariant.dependencies == project.configurations.getByName(JvmConstants.RUNTIME_CLASSPATH_CONFIGURATION_NAME).allDependencies.withType(ModuleDependency)
        runtimeVariant.dependencyConstraints.size() == 2
        runtimeVariant.dependencyConstraints == project.configurations.getByName(JvmConstants.RUNTIME_CLASSPATH_CONFIGURATION_NAME).allDependencyConstraints

        apiVariant.artifacts.collect {it.file} == [jarTask.archiveFile.get().asFile]
        apiVariant.dependencies.size() == 1
        apiVariant.dependencies == project.configurations.getByName(JvmConstants.API_CONFIGURATION_NAME).allDependencies.withType(ModuleDependency)
        apiVariant.dependencyConstraints.size() == 1
        apiVariant.dependencyConstraints == project.configurations.getByName(JvmConstants.API_CONFIGURATION_NAME).allDependencyConstraints
    }

}
