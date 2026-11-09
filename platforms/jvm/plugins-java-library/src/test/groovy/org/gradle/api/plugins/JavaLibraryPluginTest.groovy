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

import org.gradle.api.DomainObjectCollection
import org.gradle.api.GradleException
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.plugins.PluginApplicationException
import org.gradle.api.internal.tasks.JvmConstants
import org.gradle.api.plugins.internal.JavaPluginHelper
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.testing.base.TestSuite
import org.gradle.testing.base.TestSuiteTarget
import org.gradle.testing.base.TestingExtension
import org.gradle.util.GradleVersion
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
        api.extendsFrom == [] as Set
        !api.canBeConsumed
        !api.canBeResolved

        when:
        def implementation = project.configurations.getByName(JvmConstants.IMPLEMENTATION_CONFIGURATION_NAME)

        then:
        implementation.extendsFrom == [api] as Set
        !implementation.canBeConsumed
        !implementation.canBeResolved

        when:
        def runtimeOnly = project.configurations.getByName(JvmConstants.RUNTIME_ONLY_CONFIGURATION_NAME)

        then:
        runtimeOnly.transitive
        !runtimeOnly.canBeConsumed
        !runtimeOnly.canBeResolved
        runtimeOnly.extendsFrom == [] as Set

        when:
        def runtimeElements = project.configurations.getByName(JvmConstants.RUNTIME_ELEMENTS_CONFIGURATION_NAME)

        then:
        runtimeElements.transitive
        runtimeElements.canBeConsumed
        !runtimeElements.canBeResolved
        runtimeElements.extendsFrom == [implementation, runtimeOnly] as Set

        when:
        def runtimeClasspath = project.configurations.getByName(JvmConstants.RUNTIME_CLASSPATH_CONFIGURATION_NAME)

        then:
        runtimeClasspath.transitive
        !runtimeClasspath.canBeConsumed
        runtimeClasspath.canBeResolved
        runtimeClasspath.extendsFrom == [runtimeOnly, implementation] as Set

        when:
        def compileOnlyApi = project.configurations.getByName(JvmConstants.COMPILE_ONLY_API_CONFIGURATION_NAME)

        then:
        compileOnlyApi.extendsFrom == [] as Set
        compileOnlyApi.transitive
        !compileOnlyApi.canBeConsumed
        !compileOnlyApi.canBeResolved

        when:
        def compileOnly = project.configurations.getByName(JvmConstants.COMPILE_ONLY_CONFIGURATION_NAME)

        then:
        compileOnly.extendsFrom == [compileOnlyApi] as Set
        compileOnly.transitive

        when:
        def compileClasspath = project.configurations.getByName(JvmConstants.COMPILE_CLASSPATH_CONFIGURATION_NAME)

        then:
        compileClasspath.extendsFrom == toSet(compileOnly, implementation)
        compileClasspath.transitive

        when:
        def apiElements = project.configurations.getByName(JvmConstants.API_ELEMENTS_CONFIGURATION_NAME)

        then:
        apiElements.extendsFrom == [api, compileOnlyApi] as Set
        apiElements.canBeConsumed
        !apiElements.canBeResolved

        when:
        def testImplementation = project.configurations.getByName(JvmConstants.TEST_IMPLEMENTATION_CONFIGURATION_NAME)

        then:
        testImplementation.extendsFrom == toSet(implementation)
        !testImplementation.canBeConsumed
        !testImplementation.canBeResolved

        when:
        def testRuntimeOnly = project.configurations.getByName(JvmConstants.TEST_RUNTIME_ONLY_CONFIGURATION_NAME)

        then:
        testRuntimeOnly.transitive
        !testRuntimeOnly.canBeConsumed
        !testRuntimeOnly.canBeResolved
        testRuntimeOnly.extendsFrom == [runtimeOnly] as Set

        when:
        def testCompileOnly = project.configurations.getByName(JvmConstants.TEST_COMPILE_ONLY_CONFIGURATION_NAME)

        then:
        testCompileOnly.extendsFrom == toSet(compileOnlyApi)
        !testRuntimeOnly.canBeConsumed
        !testRuntimeOnly.canBeResolved
        testCompileOnly.transitive

        when:
        def testCompileClasspath = project.configurations.getByName(JvmConstants.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME)

        then:
        testCompileClasspath.extendsFrom == toSet(testCompileOnly, testImplementation)
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

    def "reports a missing default test suite"() {
        given:
        project.pluginManager.apply(JavaPlugin)
        def testing = project.extensions.getByType(TestingExtension)
        testing.suites.remove(testing.suites.named("test"))

        when:
        project.pluginManager.apply(JavaLibraryPlugin)

        then:
        PluginApplicationException e = thrown()
        e.cause.message == "The default test suite 'test' is required. Root project 'test-project' has no test suite named 'test' in 'testing.suites'."
        (e.cause as GradleException).resolutions == [
            "Don't remove the 'test' suite from 'testing.suites'. To skip its tests, disable the 'test' task instead.",
            "To change the default suite, configure it with testing.suites.named('test', JvmTestSuite) instead of replacing it.",
            "For more information on configuring the default test suite, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/jvm_test_suite_plugin.html#sec:configuring_the_built_in_test_suite in the Gradle documentation."
        ]
    }

    def "reports a default test suite that is not a JVM test suite"() {
        given:
        project.pluginManager.apply(JvmTestSuitePlugin)
        project.extensions.getByType(TestingExtension).suites.add(new CustomTestSuite(project.objects.domainObjectSet(TestSuiteTarget)))

        when:
        JavaPluginHelper.getDefaultTestSuite(project)

        then:
        GradleException e = thrown()
        e.message == "The default test suite 'test' must be a JvmTestSuite, but the suite named 'test' in 'testing.suites' has type '${CustomTestSuite.name}'."
        e.resolutions == [
            "Rename your custom suite. The name 'test' is reserved for the default JvmTestSuite.",
            "For more information on declaring additional test suites, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/jvm_test_suite_plugin.html#sec:declare_an_additional_test_suite in the Gradle documentation."
        ]
    }

    def "reports a missing testing extension"() {
        when:
        JavaPluginHelper.getDefaultTestSuite(project)

        then:
        GradleException e = thrown()
        e.message == "The Java plugin must be applied to access the default test suite ('test')."
        e.resolutions == [
            "Apply the 'java' plugin with plugins { id 'java' }. It applies the 'jvm-test-suite' plugin and registers the default 'test' suite.",
            "For more information on the Java plugin, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/java_plugin.html#sec:java_usage in the Gradle documentation."
        ]
    }

    def "reports a missing java component"() {
        given:
        project.pluginManager.apply(JavaPlugin)
        project.components.remove(project.components.named("java"))

        when:
        project.pluginManager.apply(JavaLibraryPlugin)

        then:
        PluginApplicationException e = thrown()
        e.cause.message == "The 'java' component is required. Root project 'test-project' has no component named 'java' in 'project.components'."
        (e.cause as GradleException).resolutions == [
            "Don't remove the 'java' component from 'project.components'.",
            "For more information on the 'java' component, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/java_plugin.html#sec:java_plugin_publishing in the Gradle documentation."
        ]
    }

    def "reports a java component that is not a JVM component"() {
        given:
        project.pluginManager.apply(JavaPlugin)
        project.components.remove(project.components.named("java"))
        project.components.add(project.services.get(SoftwareComponentFactory).adhoc("java"))

        when:
        project.pluginManager.apply(JavaLibraryPlugin)

        then:
        PluginApplicationException e = thrown()
        e.cause.message == "The 'java' component must be created by the Java plugin, but the component named 'java' in root project 'test-project' has type 'org.gradle.api.publish.internal.component.DefaultAdhocSoftwareComponent'."
        (e.cause as GradleException).resolutions == [
            "Don't register a software component named 'java'. That name is reserved for the Java plugin.",
            "For more information on the 'java' component, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/java_plugin.html#sec:java_plugin_publishing in the Gradle documentation."
        ]
    }

    static class CustomTestSuite implements TestSuite {
        private final DomainObjectCollection<TestSuiteTarget> targets

        CustomTestSuite(DomainObjectCollection<TestSuiteTarget> targets) {
            this.targets = targets
        }

        @Override
        String getName() {
            return "test"
        }

        @Override
        DomainObjectCollection<? extends TestSuiteTarget> getTargets() {
            return targets
        }
    }
}
