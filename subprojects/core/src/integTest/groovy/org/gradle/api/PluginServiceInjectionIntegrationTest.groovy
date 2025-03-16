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

package org.gradle.api

import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.ProjectLayout
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.execution.ExecutionEngine
import org.gradle.process.ExecOperations

import javax.inject.Inject

class PluginServiceInjectionIntegrationTest extends AbstractIntegrationSpec {

    def "can apply a plugin with @Inject services constructor arg"() {
        buildFile """
            class CustomPlugin implements Plugin<Project> {
                private final WorkerExecutor executor

                @Inject
                CustomPlugin(WorkerExecutor executor) {
                    this.executor = executor
                }

                void apply(Project p) {
                    println(executor != null ? "got it" : "NOT IT")

                    // is not generated
                    assert getClass() == CustomPlugin
                }
            }

            apply plugin: CustomPlugin
        """

        expect:
        succeeds()
        outputContains("got it")
    }

    def "fails when plugin constructor is not annotated with @Inject"() {
        buildFile """
            class CustomPlugin implements Plugin<Project> {
                CustomPlugin(WorkerExecutor executor) {
                }

                void apply(Project p) {
                }
            }

            apply plugin: CustomPlugin
        """

        expect:
        fails()
        failure.assertHasCause("Failed to apply plugin class 'CustomPlugin'")
        failure.assertHasCause("Could not create plugin of type 'CustomPlugin'.")
        failure.assertHasCause("The constructor for type CustomPlugin should be annotated with @Inject.")
    }

    def "fails when plugin constructor requests unknown service"() {
        buildFile """
            interface Unknown { }

            class CustomPlugin implements Plugin<Project> {
                @Inject
                CustomPlugin(Unknown x) {
                }

                void apply(Project p) {
                }
            }

            apply plugin: CustomPlugin
        """

        expect:
        fails()
        failure.assertHasCause("Failed to apply plugin class 'CustomPlugin'")
        failure.assertHasCause("Could not create plugin of type 'CustomPlugin'.")
        failure.assertHasCause("Unable to determine constructor argument #1: missing parameter of type Unknown, or no service of type Unknown")
    }

    def "can inject service using getter method"() {
        buildFile """
            class CustomPlugin implements Plugin<Project> {
                @Inject
                WorkerExecutor getExecutor() { }

                void apply(Project p) {
                    println(executor != null ? "got it" : "NOT IT")

                    // is generated but not extensible
                    assert getClass() != CustomPlugin
                    assert (this instanceof org.gradle.api.internal.GeneratedSubclass)
                    assert !(this instanceof ExtensionAware)
                }
            }

            apply plugin: CustomPlugin
        """

        expect:
        succeeds()
        outputContains("got it")
    }

    def "can inject service using abstract getter method"() {
        buildFile """
            abstract class CustomPlugin implements Plugin<Project> {
                @Inject
                abstract WorkerExecutor getExecutor()

                void apply(Project p) {
                    println(executor != null ? "got it" : "NOT IT")

                    // is generated
                    assert getClass() != CustomPlugin
                    assert (this instanceof org.gradle.api.internal.GeneratedSubclass)
                }
            }

            apply plugin: CustomPlugin
        """

        expect:
        succeeds()
        outputContains("got it")
    }

    def "service of type #serviceType is available for injection into project plugin"() {
        buildFile << """
            class CustomPlugin implements Plugin<Project> {
                private final ${serviceType} service

                @Inject
                CustomPlugin(${serviceType} service) {
                    this.service = service
                }

                void apply(Project p) {
                    println(service != null ? "got it" : "NOT IT")
                }
            }

            apply plugin: CustomPlugin
        """

        expect:
        succeeds()
        outputContains("got it")

        where:
        serviceType << [
            ObjectFactory,
            ProjectLayout,
            ProviderFactory,
            ExecutionEngine,
            FileSystemOperations,
            ExecOperations,
            ConfigurationContainer, // this is a supertype of the RoleBasedConfigurationContainerInternal, we want to ensure it can still be injected without asking for the subtype
            RoleBasedConfigurationContainerInternal
        ].collect { it.name }
    }

    def "service of type #serviceType is available for injection into settings plugin"() {
        settingsFile << """
            import ${Inject.name}

            class CustomPlugin implements Plugin<Settings> {
                private final ${serviceType} service

                @Inject
                CustomPlugin(${serviceType} service) {
                    this.service = service
                }

                void apply(Settings s) {
                    println(service != null ? "got it" : "NOT IT")
                }
            }

            apply plugin: CustomPlugin
        """

        expect:
        succeeds()
        outputContains("got it")

        where:
        serviceType << [
            ObjectFactory,
            ProviderFactory,
            FileSystemOperations,
            ExecOperations,
        ].collect { it.name }
    }

    def "service of type #serviceType is available for injection into gradle object plugin"() {
        settingsFile << """
            import ${Inject.name}

            class CustomPlugin implements Plugin<Gradle> {
                private final ${serviceType} service

                @Inject
                CustomPlugin(${serviceType} service) {
                    this.service = service
                }

                void apply(Gradle s) {
                    println(service != null ? "got it" : "NOT IT")
                }
            }

            gradle.apply plugin: CustomPlugin
        """

        expect:
        succeeds()
        outputContains("got it")

        where:
        serviceType << [
            ObjectFactory,
            ProviderFactory,
            FileSystemOperations,
            ExecOperations,
        ].collect { it.name }
    }
}
