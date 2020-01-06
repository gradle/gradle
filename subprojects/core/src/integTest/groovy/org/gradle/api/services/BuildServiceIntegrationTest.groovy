/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.services

import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.OutputDirectories
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.InstantExecutionRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.fixtures.RequiredFeatures
import org.gradle.integtests.fixtures.UnsupportedWithInstantExecution
import org.gradle.internal.reflect.Instantiator
import org.gradle.process.ExecOperations
import org.junit.runner.RunWith
import spock.lang.Unroll

import javax.inject.Inject

@RunWith(InstantExecutionRunner)
class BuildServiceIntegrationTest extends AbstractIntegrationSpec {
    def "service is created once per build on first use and stopped at the end of the build"() {
        serviceImplementation()
        buildFile << """
            abstract class Consumer extends DefaultTask {
                @Internal
                abstract Property<CountingService> getCounter()

                @TaskAction
                def go() {
                    counter.get().increment()
                }
            }

            def provider = gradle.sharedServices.registerIfAbsent("counter", CountingService) {
                parameters.initial = 10
            }

            task first(type: Consumer) {
                counter = provider
            }

            task second(type: Consumer) {
                counter = provider
            }
        """

        when:
        run("first", "second")

        then:
        output.count("service:") == 4
        outputContains("service: created with value = 10")
        outputContains("service: value is 11")
        outputContains("service: value is 12")
        outputContains("service: closed with value 12")

        when:
        run("first", "second")

        then:
        output.count("service:") == 4
        outputContains("service: created with value = 10")
        outputContains("service: value is 11")
        outputContains("service: value is 12")
        outputContains("service: closed with value 12")

        when:
        run("help")

        then:
        result.assertNotOutput("service:")

        when:
        run("help")

        then:
        result.assertNotOutput("service:")
    }

    def "can use service from task doFirst() or doLast() action"() {
        serviceImplementation()
        buildFile << """
            def provider = gradle.sharedServices.registerIfAbsent("counter", CountingService) {
                parameters.initial = 10
            }

            task first {
                doFirst {
                    provider.get().increment()
                }
            }

            task second {
                doLast {
                    provider.get().increment()
                }
            }
        """

        when:
        run("first", "second")

        then:
        output.count("service:") == 4
        outputContains("service: created with value = 10")
        outputContains("service: value is 11")
        outputContains("service: value is 12")
        outputContains("service: closed with value 12")

        when:
        run("first", "second")

        then:
        output.count("service:") == 4
        outputContains("service: created with value = 10")
        outputContains("service: value is 11")
        outputContains("service: value is 12")
        outputContains("service: closed with value 12")
    }

    def "tasks can use mapped value of service"() {
        serviceImplementation()
        buildFile << """
            def provider = gradle.sharedServices.registerIfAbsent("counter", CountingService) {
                parameters.initial = 10
            }

            def count = provider.map { it.increment() + 10 }

            task first {
                doFirst {
                    println("got value = " + count.get())
                }
            }

            task second {
                doFirst {
                    println("got value = " + count.get())
                }
            }
        """

        when:
        run("first", "second")

        then:
        output.count("service:") == 4
        outputContains("service: created with value = 10")
        outputContains("service: value is 11")
        outputContains("got value = 21")
        outputContains("service: value is 12")
        outputContains("got value = 22")
        outputContains("service: closed with value 12")

        when:
        run("first", "second")

        then:
        output.count("service:") == 4
        outputContains("service: created with value = 10")
        outputContains("service: value is 11")
        outputContains("got value = 21")
        outputContains("service: value is 12")
        outputContains("got value = 22")
        outputContains("service: closed with value 12")
    }

    @RequiredFeatures(
        [@RequiredFeature(feature = "org.gradle.unsafe.instant-execution", value = "false")]
    )
    @UnsupportedWithInstantExecution
    def "service can be used at configuration and execution time"() {
        serviceImplementation()
        buildFile << """
            def provider = gradle.sharedServices.registerIfAbsent("counter", CountingService) {
                parameters.initial = 10
            }

            task count {
                doFirst {
                    provider.get().increment()
                }
            }

            provider.get().increment()
        """

        when:
        run("count")

        then:
        output.count("service:") == 4
        outputContains("service: created with value = 10")
        outputContains("service: value is 11")
        outputContains("service: value is 12")
        outputContains("service: closed with value 12")

        when:
        run("count")

        then:
        output.count("service:") == 4
        outputContains("service: created with value = 10")
        outputContains("service: value is 11")
        outputContains("service: value is 12")
        outputContains("service: closed with value 12")

        when:
        run("help")

        then:
        output.count("service:") == 3
        outputContains("service: created with value = 10")
        outputContains("service: value is 11")
        outputContains("service: closed with value 11")
    }

    @RequiredFeatures(
        [@RequiredFeature(feature = "org.gradle.unsafe.instant-execution", value = "true")]
    )
    def "service used at configuration and execution time can be used with instant execution"() {
        serviceImplementation()
        buildFile << """
            def provider = gradle.sharedServices.registerIfAbsent("counter", CountingService) {
                parameters.initial = 10
            }

            task count {
                doFirst {
                    provider.get().increment()
                }
            }

            provider.get().increment()
        """

        when:
        run("count")

        then:
        output.count("service:") == 4
        outputContains("service: created with value = 10")
        outputContains("service: value is 11")
        outputContains("service: value is 12")
        outputContains("service: closed with value 12")

        when:
        run("count")

        then:
        output.count("service:") == 3
        outputContains("service: created with value = 10")
        outputContains("service: value is 11")
        outputContains("service: closed with value 11")

        when:
        run("help")

        then:
        output.count("service:") == 3
        outputContains("service: created with value = 10")
        outputContains("service: value is 11")
        outputContains("service: closed with value 11")

        when:
        run("help")

        then:
        result.assertNotOutput("service:")
    }

    def "plugin applied to multiple projects can register a shared service"() {
        settingsFile << "include 'a', 'b', 'c'"
        serviceImplementation()
        buildFile << """
            class CounterPlugin implements Plugin<Project> {
                void apply(Project project) {
                    def provider = project.gradle.sharedServices.registerIfAbsent("counter", CountingService) {
                        parameters.initial = 10
                    }
                    project.tasks.register("count") {
                        doFirst {
                            provider.get().increment()
                        }
                    }
                }
            }
            subprojects {
                apply plugin: CounterPlugin
            }
        """

        when:
        run("count")

        then:
        output.count("service:") == 5
        outputContains("service: created with value = 10")
        outputContains("service: value is 11")
        outputContains("service: value is 12")
        outputContains("service: value is 13")
        outputContains("service: closed with value 13")

        when:
        run("count")

        then:
        output.count("service:") == 5
        outputContains("service: created with value = 10")
        outputContains("service: value is 11")
        outputContains("service: value is 12")
        outputContains("service: value is 13")
        outputContains("service: closed with value 13")
    }

    def "plugin can apply conventions to shared services of a given type"() {
        serviceImplementation()
        buildFile << """
            class CounterConventionPlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.gradle.sharedServices.registrations.configureEach {
                        if (parameters instanceof CountingParams) {
                            parameters.initial = parameters.initial.get() + 5
                        }
                    }
                }
            }
            apply plugin: CounterConventionPlugin

            def counter1 = project.gradle.sharedServices.registerIfAbsent("counter1", CountingService) {
                parameters.initial = 0
            }
            def counter2 = project.gradle.sharedServices.registerIfAbsent("counter2", CountingService) {
                parameters.initial = 10
            }
            task count {
                doLast {
                    counter1.get().increment()
                    counter2.get().increment()
                }
            }
        """

        when:
        run("count")

        then:
        output.count("service:") == 6
        outputContains("service: created with value = 5")
        outputContains("service: created with value = 15")
        outputContains("service: value is 6")
        outputContains("service: value is 16")
        outputContains("service: closed with value 6")
        outputContains("service: closed with value 16")

        when:
        run("count")

        then:
        output.count("service:") == 6
        outputContains("service: created with value = 5")
        outputContains("service: created with value = 15")
        outputContains("service: value is 6")
        outputContains("service: value is 16")
        outputContains("service: closed with value 6")
        outputContains("service: closed with value 16")
    }

    def "service parameters are isolated when the service is instantiated"() {
        serviceImplementation()
        buildFile << """
            def params

            def provider = gradle.sharedServices.registerIfAbsent("counter", CountingService) {
                params = parameters
                parameters.initial = 10
            }

            assert params.initial.get() == 10
            params.initial = 12

            task first {
                doFirst {
                    params.initial = 15 // should have an effect
                    provider.get().reset()
                    params.initial = 1234 // should be ignored. Ideally should fail too
                }
            }

            task second {
                dependsOn first
                doFirst {
                    provider.get().increment()
                    params.initial = 456 // should be ignored
                }
            }
        """

        when:
        run("first", "second")

        then:
        output.count("service:") == 4
        outputContains("service: created with value = 15")
        outputContains("service: value is 15")
        outputContains("service: value is 16")
        outputContains("service: closed with value 16")

        when:
        run("first", "second")

        then:
        output.count("service:") == 4
        outputContains("service: created with value = 15")
        outputContains("service: value is 15")
        outputContains("service: value is 16")
        outputContains("service: closed with value 16")
    }

    def "service can take no parameters"() {
        noParametersServiceImplementation()
        buildFile << """
            def provider = gradle.sharedServices.registerIfAbsent("counter", CountingService) {}

            task first {
                doFirst {
                    provider.get().increment()
                }
            }

            task second {
                doFirst {
                    provider.get().increment()
                }
            }
        """

        when:
        run("first", "second")

        then:
        output.count("service:") == 3
        outputContains("service: created with value = 0")
        outputContains("service: value is 1")
        outputContains("service: value is 2")

        when:
        run("first", "second")

        then:
        output.count("service:") == 3
        outputContains("service: created with value = 0")
        outputContains("service: value is 1")
        outputContains("service: value is 2")
    }

    def "service can take another service as a parameter"() {
        serviceImplementation()
        buildFile << """
            interface ForwardingParams extends BuildServiceParameters {
                Property<CountingService> getService()
            }

            abstract class ForwardingService implements BuildService<ForwardingParams> {
                void increment() {
                    println("delegating to counting service")
                    parameters.service.get().increment()
                }
            }

            def countingService = gradle.sharedServices.registerIfAbsent("counter", CountingService) {
                parameters.initial = 10
            }
            def service = gradle.sharedServices.registerIfAbsent("service", ForwardingService) {
                parameters.service = countingService
            }

            task first {
                doFirst {
                    service.get().increment()
                }
            }

            task second {
                dependsOn first
                doFirst {
                    service.get().increment()
                }
            }
        """

        when:
        run("first", "second")

        then:
        output.count("delegating to counting service") == 2
        output.count("service:") == 4
        outputContains("service: created with value = 10")
        outputContains("service: value is 11")
        outputContains("service: value is 12")
        outputContains("service: closed with value 12")

        when:
        run("first", "second")

        then:
        output.count("delegating to counting service") == 2
        output.count("service:") == 4
        outputContains("service: created with value = 10")
        outputContains("service: value is 11")
        outputContains("service: value is 12")
        outputContains("service: closed with value 12")
    }

    @Unroll
    def "can inject Gradle provided service #serviceType into build service"() {
        serviceWithInjectedService(serviceType)
        buildFile << """
            def provider = gradle.sharedServices.registerIfAbsent("counter", CountingService) {
            }

            task check {
                doFirst {
                    provider.get().increment()
                }
            }
        """

        expect:
        run("check")
        run("check")

        where:
        serviceType << [
            ExecOperations,
            FileSystemOperations,
            ObjectFactory,
            ProviderFactory,
        ].collect { it.name }
    }

    @Unroll
    def "cannot inject Gradle provided service #serviceType into build service"() {
        serviceWithInjectedService(serviceType.name)
        buildFile << """
            def provider = gradle.sharedServices.registerIfAbsent("counter", CountingService) {
            }

            task check {
                doFirst {
                    provider.get().increment()
                }
            }
        """

        when:
        fails("check")

        then:
        failure.assertHasDescription("Execution failed for task ':check'.")
        failure.assertHasCause("Services of type ${serviceType.simpleName} are not available for injection into instances of type BuildService.")

        where:
        serviceType << [
            ProjectLayout, // not isolated
            Instantiator, // internal
        ]
    }

    def "injected FileSystemOperations resolves paths relative to build root directory"() {
        serviceCopiesFiles()
        buildFile << """
            def provider = gradle.sharedServices.registerIfAbsent("copier", CopyingService) {
            }

            task copy {
                doFirst {
                    provider.get().copy("a", "b")
                }
            }
        """

        file("a").createFile()
        def dest = file("b/a")
        assert !dest.file

        when:
        run("copy")

        then:
        dest.file
    }

    @Unroll
    def "task cannot use build service for #annotationType property"() {
        serviceImplementation()
        buildFile << """
            abstract class Consumer extends DefaultTask {
                @${annotationType}
                abstract Property<CountingService> getCounter()

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                def go() {
                    outputFile.get().asFile.text = counter.get().increment()
                }
            }

            def provider = gradle.sharedServices.registerIfAbsent("counter", CountingService) {
                parameters.initial = 10
            }

            task broken(type: Consumer) {
                counter = provider
                outputFile = layout.buildDirectory.file("out.txt")
            }
        """

        expect:
        fails("broken")

        // The failure is currently very specific to the annotation type
        // TODO  - fail earlier and add some expectations here

        fails("broken")

        where:
        annotationType << [
            Input,
            InputFile,
            InputDirectory,
            InputFiles,
            OutputDirectory,
            OutputDirectories,
            OutputFile,
            LocalState].collect { it.simpleName }
    }

    def "service is stopped even if build fails"() {
        serviceImplementation()
        buildFile << """
            def counter1 = project.gradle.sharedServices.registerIfAbsent("counter1", CountingService) {
                parameters.initial = 0
            }
            def counter2 = project.gradle.sharedServices.registerIfAbsent("counter2", CountingService) {
                parameters.initial = 10
            }
            task count {
                doLast {
                    counter1.get().increment()
                    throw new RuntimeException("broken")
                }
            }
        """

        when:
        fails("count")

        then:
        output.count("service:") == 3
        outputContains("service: created with value = 0")
        outputContains("service: value is 1")
        outputContains("service: closed with value 1")

        when:
        fails("count")

        then:
        output.count("service:") == 3
        outputContains("service: created with value = 0")
        outputContains("service: value is 1")
        outputContains("service: closed with value 1")
    }

    def "reports failure to create the service instance"() {
        brokenServiceImplementation()
        buildFile << """
            def provider1 = gradle.sharedServices.registerIfAbsent("counter1", CountingService) {
                parameters.initial = 10
            }
            def provider2 = gradle.sharedServices.registerIfAbsent("counter2", CountingService) {
                parameters.initial = 10
            }

            task first {
                doFirst {
                    provider1.get().increment()
                }
            }

            task second {
                doFirst {
                    provider2.get().increment()
                }
            }
        """

        when:
        fails("first", "second", "--continue")

        then:
        failure.assertHasFailures(2)
        failure.assertHasDescription("Execution failed for task ':first'.")
        failure.assertHasCause("Failed to create service 'counter1'.")
        failure.assertHasCause("Could not create an instance of type CountingService.")
        failure.assertHasCause("broken")
        failure.assertHasDescription("Execution failed for task ':second'.")
        failure.assertHasCause("Failed to create service 'counter2'.")
        failure.assertHasCause("Could not create an instance of type CountingService.")
        failure.assertHasCause("broken")

        when:
        fails("first", "second", "--continue")

        then:
        failure.assertHasFailures(2)
        failure.assertHasDescription("Execution failed for task ':first'.")
        failure.assertHasCause("Failed to create service 'counter1'.")
        failure.assertHasCause("Could not create an instance of type CountingService.")
        failure.assertHasCause("broken")
        failure.assertHasDescription("Execution failed for task ':second'.")
        failure.assertHasCause("Failed to create service 'counter2'.")
        failure.assertHasCause("Could not create an instance of type CountingService.")
        failure.assertHasCause("broken")
    }

    def "reports failure to stop the service instance"() {
        brokenStopServiceImplementation()
        buildFile << """
            def provider1 = gradle.sharedServices.registerIfAbsent("counter1", CountingService) {
                parameters.initial = 10
            }
            def provider2 = gradle.sharedServices.registerIfAbsent("counter2", CountingService) {
                parameters.initial = 10
            }

            task first {
                doFirst {
                    provider1.get().increment()
                }
            }

            task second {
                doFirst {
                    provider2.get().increment()
                }
            }
        """

        when:
        fails("first", "second")

        then:
        // TODO - improve the error handling so as to report both failures as top level failures
        // This documents existing behaviour, not desired behaviour
        failure.assertHasDescription("Failed to notify build listener")
        failure.assertHasCause("Failed to stop service 'counter1'.")
        failure.assertHasCause("broken")
        failure.assertHasCause("Failed to stop service 'counter2'.")
        failure.assertHasCause("broken")
    }

    def serviceImplementation() {
        buildFile << """
            interface CountingParams extends BuildServiceParameters {
                Property<Integer> getInitial()
            }

            abstract class CountingService implements BuildService<CountingParams>, AutoCloseable {
                int value

                CountingService() {
                    value = parameters.initial.get()
                    println("service: created with value = \${value}")
                }

                synchronized int getInitialValue() { return parameters.initial.get() }

                // Service must be thread-safe
                synchronized void reset() {
                    value = parameters.initial.get()
                    println("service: value is \${value}")
                }

                // Service must be thread-safe
                synchronized int increment() {
                    value++
                    println("service: value is \${value}")
                    return value
                }

                void close() {
                    println("service: closed with value \${value}")
                }
            }
        """
    }

    def noParametersServiceImplementation() {
        buildFile << """
            abstract class CountingService implements BuildService<${BuildServiceParameters.name}.None> {
                int value

                CountingService() {
                    value = 0
                    println("service: created with value = \${value}")
                }

                // Service must be thread-safe
                synchronized int increment() {
                    value++
                    println("service: value is \${value}")
                    return value
                }
            }
        """
    }

    def serviceWithInjectedService(String serviceType) {
        buildFile << """
            import ${Inject.name}

            abstract class CountingService implements BuildService<${BuildServiceParameters.name}.None> {
                int value

                CountingService() {
                    value = 0
                    println("service: created with value = \${value}")
                }

                @Inject
                abstract ${serviceType} getInjectedService()

                // Service must be thread-safe
                synchronized int increment() {
                    assert injectedService != null
                    value++
                    return value
                }
            }
        """
    }

    def serviceCopiesFiles() {
        buildFile << """
            import ${Inject.name}

            abstract class CopyingService implements BuildService<${BuildServiceParameters.name}.None> {
                @Inject
                abstract FileSystemOperations getFiles()

                void copy(String source, String dest) {
                    files.copy {
                        it.from(source)
                        it.into(dest)
                    }
                }
            }
        """
    }

    def brokenServiceImplementation() {
        buildFile << """
            interface Params extends BuildServiceParameters {
                Property<Integer> getInitial()
            }

            abstract class CountingService implements BuildService<Params> {
                CountingService() {
                    throw new IOException("broken") // use a checked exception
                }

                void increment() {
                    throw new IOException("broken") // use a checked exception
                }
            }
        """
    }

    def brokenStopServiceImplementation() {
        buildFile << """
            interface Params extends BuildServiceParameters {
                Property<Integer> getInitial()
            }

            abstract class CountingService implements BuildService<Params>, AutoCloseable {
                CountingService() {
                }

                void increment() {
                }

                void close() {
                    throw new IOException("broken") // use a checked exception
                }
            }
        """
    }
}
