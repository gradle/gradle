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
package org.gradle.integtests

import org.gradle.integtests.fixtures.TargetVersions
import org.gradle.integtests.fixtures.executer.GradleExecuter

import java.util.function.Consumer

@TargetVersions("8.0.2")
class PropertyUpgradesBinaryCompatibilityCrossVersionSpec extends AbstractPropertyUpgradesBinaryCompatibilityCrossVersionSpec {

    def "can use upgraded Checkstyle in a Groovy plugin compiled with a previous Gradle version"() {
        given:
        prepareGroovyPluginTest """
            project.tasks.register("myCheckstyle", Checkstyle) {
                maxErrors = 1
                int currentMaxErrors = maxErrors
                assert currentMaxErrors == 1
            }
        """

        expect:
        succeedsWithPluginCompiledWithPreviousVersion()
    }

    def "can use upgraded Checkstyle in a Java plugin compiled with a previous Gradle version"() {
        given:
        prepareJavaPluginTest """
            project.getTasks().register("myCheckstyle", Checkstyle.class, it -> {
                it.setMaxErrors(1);
                int currentMaxErrors = it.getMaxErrors();
                assert currentMaxErrors == 1;
            });
        """

        expect:
        succeedsWithPluginCompiledWithPreviousVersion()
    }

    def "can use upgraded Checkstyle in a Kotlin plugin compiled with a previous Gradle version"() {
        given:
        prepareKotlinPluginTest """
            project.tasks.register("myCheckstyle", Checkstyle::class.java) {
                maxErrors = 1
                val currentMaxErrors = maxErrors
                assert(currentMaxErrors == 1)
            }
        """

        expect:
        succeedsWithPluginCompiledWithPreviousVersion()
    }

    def "prints deprecation when we use removed method with compatibility shim in newer Gradle version"() {
        given:
        prepareGroovyPluginTest """
            project.tasks.register("myCompile", JavaCompile) {
                options.annotationProcessorGeneratedSourcesDirectory = project.layout.buildDirectory.dir("generated/sources").get().asFile
            }
        """

        expect:
        succeedsWithPluginCompiledWithPreviousVersion() {
            it.expectDocumentedDeprecationWarning(
                "The usage of CompileOptions.annotationProcessorGeneratedSourcesDirectory has been deprecated. " +
                    "This will fail with an error in Gradle 10.0. " +
                    "Property 'annotationProcessorGeneratedSourcesDirectory' was removed and this compatibility shim will be removed in Gradle 10.0. " +
                    "Please use 'generatedSourceOutputDirectory' property instead."
            )
        }
    }

    def succeedsWithPluginCompiledWithPreviousVersion(Consumer<GradleExecuter> additionalChecks = { }) {
        version(previous).withTasks('assemble').inDirectory(file("producer")).run()

        def currentExecuter = version(current)
            .withTasks('tasks')
            .withStacktraceEnabled()
            .requireDaemon()
            .requireIsolatedDaemons()
        additionalChecks.accept(currentExecuter)
        currentExecuter.run()
    }
}

