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

import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.Dependency
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil

import static org.gradle.util.WrapUtil.toSet

class JavaLibraryPluginTest extends AbstractProjectBuilderSpec {
    private final def javaLibraryPlugin = new JavaLibraryPlugin()
    private final def javaPlugin = new JavaPlugin()

    def "applies Java plugin"() {
        when:
        javaLibraryPlugin.apply(project)

        then:
        project.plugins.findPlugin(JavaPlugin)
    }

    def "adds configurations to the project"() {
        given:
        javaLibraryPlugin.apply(project)

        when:
        def compile = project.configurations.getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME)

        then:
        compile.extendsFrom == [] as Set
        !compile.visible
        compile.transitive

        when:
        def api = project.configurations.getByName(JavaPlugin.API_CONFIGURATION_NAME)

        then:
        !api.visible
        api.extendsFrom == [compile] as Set
        !api.canBeConsumed
        !api.canBeResolved

        when:
        def apiCompile = project.configurations.getByName(JavaPlugin.API_COMPILE_CONFIGURATION_NAME)

        then:
        !apiCompile.visible
        apiCompile.extendsFrom == [api] as Set
        apiCompile.canBeConsumed
        !apiCompile.canBeResolved

        when:
        def implementation = project.configurations.getByName(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME)

        then:
        !implementation.visible
        implementation.extendsFrom == [api, compile] as Set
        !implementation.canBeConsumed
        !implementation.canBeResolved

        when:
        def runtime = project.configurations.getByName(JavaPlugin.RUNTIME_CONFIGURATION_NAME)

        then:
        runtime.extendsFrom == toSet(compile)
        !runtime.visible
        runtime.transitive

        when:
        def runtimeOnly = project.configurations.getByName(JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME)

        then:
        runtimeOnly.transitive
        !runtimeOnly.visible
        !runtimeOnly.canBeConsumed
        !runtimeOnly.canBeResolved
        runtimeOnly.extendsFrom == [] as Set

        when:
        def runtimeElements = project.configurations.getByName(JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME)

        then:
        runtimeElements.transitive
        !runtimeElements.visible
        runtimeElements.canBeConsumed
        !runtimeElements.canBeResolved
        runtimeElements.extendsFrom == [implementation] as Set

        when:
        def runtimeClasspath = project.configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)

        then:
        runtimeClasspath.transitive
        !runtimeClasspath.visible
        !runtimeClasspath.canBeConsumed
        runtimeClasspath.canBeResolved
        runtimeClasspath.extendsFrom == [runtimeOnly, runtime, runtimeElements] as Set

        when:
        def compileOnly = project.configurations.getByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME)

        then:
        compileOnly.extendsFrom == toSet(implementation)
        !compileOnly.visible
        compileOnly.transitive

        when:
        def compileClasspath = project.configurations.getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME)

        then:
        compileClasspath.extendsFrom == toSet(compileOnly)
        !compileClasspath.visible
        compileClasspath.transitive

        when:
        def testCompile = project.configurations.getByName(JavaPlugin.TEST_COMPILE_CONFIGURATION_NAME)

        then:
        testCompile.extendsFrom == toSet(implementation)
        !testCompile.visible
        testCompile.transitive

        when:
        def testImplementation = project.configurations.getByName(JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME)

        then:
        testImplementation.extendsFrom == toSet(testCompile, implementation)
        !testImplementation.visible
        !testImplementation.canBeConsumed
        !testImplementation.canBeResolved

        when:
        def testRuntime = project.configurations.getByName(JavaPlugin.TEST_RUNTIME_CONFIGURATION_NAME)

        then:
        testRuntime.extendsFrom == toSet(runtime, testCompile, testImplementation)
        !testRuntime.visible
        testRuntime.transitive

        when:
        def testRuntimeOnly = project.configurations.getByName(JavaPlugin.TEST_RUNTIME_ONLY_CONFIGURATION_NAME)

        then:
        testRuntimeOnly.transitive
        !testRuntimeOnly.visible
        !testRuntimeOnly.canBeConsumed
        !testRuntimeOnly.canBeResolved
        testRuntimeOnly.extendsFrom == [] as Set

        when:
        def testCompileOnly = project.configurations.getByName(JavaPlugin.TEST_COMPILE_ONLY_CONFIGURATION_NAME)

        then:
        testCompileOnly.extendsFrom == toSet(testImplementation)
        !testCompileOnly.visible
        testCompileOnly.transitive

        when:
        def testCompileClasspath = project.configurations.getByName(JavaPlugin.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME)

        then:
        testCompileClasspath.extendsFrom == toSet(testCompileOnly)
        !testCompileClasspath.visible
        testCompileClasspath.transitive

        when:
        def defaultConfig = project.configurations.getByName(Dependency.DEFAULT_CONFIGURATION)

        then:
        defaultConfig.extendsFrom == toSet(runtime)
    }

    def "cannot add dependencies to `compile` configuration"() {
        given:
        def commonProject = TestUtil.createChildProject(project, "common")
        javaLibraryPlugin.apply(project)

        when:
        project.dependencies {
            compile commonProject
        }

        then:
        def e = thrown(InvalidUserDataException)
        e.message == "The 'compile' configuration should not be used to declare dependencies. Please use 'api' or 'implementation' instead."
    }

    def "can declare API and implementation dependencies"() {
        given:
        def commonProject = TestUtil.createChildProject(project, "common")
        def toolsProject = TestUtil.createChildProject(project, "tools")
        def internalProject = TestUtil.createChildProject(project, "internal")

        javaPlugin.apply(project)
        javaLibraryPlugin.apply(commonProject)
        javaLibraryPlugin.apply(toolsProject)
        javaLibraryPlugin.apply(internalProject)

        when:
        project.dependencies {
            compile commonProject
        }
        commonProject.dependencies {
            api toolsProject
            implementation internalProject
        }

        def task = project.tasks.compileJava

        then:
        task.taskDependencies.getDependencies(task)*.path as Set == [':common:compileJava', ':tools:compileJava'] as Set

        when:
        task = commonProject.tasks.compileJava

        then:
        task.taskDependencies.getDependencies(task)*.path as Set == [':tools:compileJava', ':internal:compileJava'] as Set
    }
}
