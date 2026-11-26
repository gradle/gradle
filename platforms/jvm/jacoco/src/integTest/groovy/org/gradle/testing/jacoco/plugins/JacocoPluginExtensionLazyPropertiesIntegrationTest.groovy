/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.testing.jacoco.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.testing.jacoco.plugins.fixtures.JacocoCoverage
import org.gradle.testing.jacoco.plugins.fixtures.JavaProjectUnderTest

/**
 * Covers {@link JacocoPluginExtension#getToolVersion()} and {@link org.gradle.testing.jacoco.tasks.JacocoBase#getJacocoClasspath()}.
 */
class JacocoPluginExtensionLazyPropertiesIntegrationTest extends AbstractIntegrationSpec {

    private final JavaProjectUnderTest javaProjectUnderTest = new JavaProjectUnderTest(testDirectory)

    def setup() {
        JacocoCoverage.assumeDefaultJacocoWorksOnCurrentJdk()
        javaProjectUnderTest.writeBuildScript().writeSourceFiles()
    }

    def "toolVersion keeps its default, and a lazy value reads back and selects the tool dependencies"() {
        buildFile << """
            println("default: " + (jacoco.toolVersion == JacocoPlugin.DEFAULT_JACOCO_VERSION))
            jacoco.lazyProperties.toolVersion = provider { '0.8.12' }
            println("lazy: " + jacoco.toolVersion)
        """

        when:
        succeeds("dependencies", "--configuration", "jacocoAnt")

        then:
        outputContains("default: true")
        outputContains("lazy: 0.8.12")
        outputContains("org.jacoco:org.jacoco.ant:0.8.12")
    }

    def "an eager toolVersion after a lazy binding wins"() {
        buildFile << """
            jacoco.lazyProperties.toolVersion = provider { '0.8.12' }
            jacoco.toolVersion = '0.8.11'
            println("version: " + jacoco.toolVersion)
        """

        when:
        succeeds("dependencies", "--configuration", "jacocoAgent")

        then:
        outputContains("version: 0.8.11")
        outputContains("org.jacoco:org.jacoco.agent:0.8.11")
    }

    def "jacocoClasspath getter returns what was set"() {
        buildFile << """
            def task = tasks.jacocoTestReport
            println("is configuration: " + task.jacocoClasspath.is(configurations.jacocoAnt))
            def custom = files("custom.jar")
            task.jacocoClasspath = custom
            println("is set value: " + task.jacocoClasspath.is(custom))
            task.lazyProperties.jacocoClasspath.from(files("extra.jar"))
            println("after lazy addition: " + task.jacocoClasspath.files.collect { it.name }.sort())
            task.jacocoClasspath = files("other.jar")
            println("after eager set: " + task.jacocoClasspath.files.collect { it.name }.sort())
            task.jacocoClasspath = null
            println("after null: " + task.jacocoClasspath)
        """

        when:
        succeeds("help")

        then:
        outputContains("is configuration: true")
        outputContains("is set value: true")
        outputContains("after lazy addition: [custom.jar, extra.jar]")
        outputContains("after eager set: [other.jar]")
        outputContains("after null: null")
    }

    def "lazy jacocoClasspath with a task output runs that task first"() {
        buildFile << """
            abstract class Producer extends DefaultTask {
                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void run() {
                    outputFile.get().asFile.text = "not a jar"
                }
            }

            def producer = tasks.register("producer", Producer) {
                outputFile = layout.buildDirectory.file("extra.jar")
            }

            tasks.jacocoTestCoverageVerification.lazyProperties.jacocoClasspath.from(producer)
        """

        when:
        succeeds("jacocoTestCoverageVerification")

        then:
        result.assertTaskScheduled(":producer")
        result.assertTaskOrder(":producer", ":jacocoTestCoverageVerification")
    }
}
