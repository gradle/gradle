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
import org.gradle.integtests.fixtures.modes.ToBeFixedForConfigurationCache
import org.gradle.testing.jacoco.plugins.fixtures.JacocoCoverage
import org.gradle.testing.jacoco.plugins.fixtures.JavaProjectUnderTest

import java.nio.charset.StandardCharsets

class JacocoTaskExtensionLazyPropertiesIntegrationTest extends AbstractIntegrationSpec {

    private final JavaProjectUnderTest javaProjectUnderTest = new JavaProjectUnderTest(testDirectory)

    def setup() {
        JacocoCoverage.assumeDefaultJacocoWorksOnCurrentJdk()
        javaProjectUnderTest.writeBuildScript().writeSourceFiles()
        buildFile << """
            abstract class Producer extends DefaultTask {
                @Input
                abstract Property<String> getContent()

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void run() {
                    outputFile.get().asFile.text = content.get()
                }
            }

            def producer = tasks.register("producer", Producer) {
                content = "from-producer"
                outputFile = layout.buildDirectory.file("producer.txt")
            }
        """
    }

    def "eager getters keep their defaults and types"() {
        buildFile << """
            def ext = tasks.test.extensions.getByType(JacocoTaskExtension)
            println("enabled: " + ext.enabled)
            println("includes: " + ext.includes + " list: " + (ext.includes instanceof List))
            println("excludes: " + ext.excludes)
            println("excludeClassLoaders: " + ext.excludeClassLoaders)
            println("includeNoLocationClasses: " + ext.includeNoLocationClasses)
            println("sessionId: " + ext.sessionId)
            println("dumpOnExit: " + ext.dumpOnExit)
            println("output: " + ext.output)
            println("address: " + ext.address)
            println("port: " + ext.port)
            println("jmx: " + ext.jmx)
            println("destinationFile: " + (ext.destinationFile == file("build/jacoco/test.exec")))
        """

        when:
        succeeds("help")

        then:
        outputContains("enabled: true")
        outputContains("includes: [] list: true")
        outputContains("excludes: []")
        outputContains("excludeClassLoaders: []")
        outputContains("includeNoLocationClasses: false")
        outputContains("sessionId: null")
        outputContains("dumpOnExit: true")
        outputContains("output: FILE")
        outputContains("address: null")
        outputContains("port: 0")
        outputContains("jmx: false")
        outputContains("destinationFile: true")
    }

    def "eager setters and getters work as before"() {
        buildFile << """
            def ext = tasks.test.jacoco
            ext.includes = ['a']
            ext.includes.add('b')
            println("includes: " + ext.includes + " lazy: " + ext.lazyProperties.includes.get())
            ext.includes = null
            println("includes after null: " + ext.includes)
            ext.sessionId = 'session'
            ext.sessionId = null
            println("sessionId after null: " + ext.sessionId)
            ext.output = JacocoTaskExtension.Output.TCP_SERVER
            ext.port = 100
            ext.enabled = false
            println("output: " + ext.output + " port: " + ext.port + " enabled: " + ext.enabled)
            ext.destinationFile = file("custom.exec")
            println("destinationFile: " + (ext.destinationFile == file("custom.exec")) + " lazy: " + (ext.lazyProperties.destinationFile.get().asFile == file("custom.exec")))
        """

        when:
        succeeds("help")

        then:
        outputContains("includes: [a, b] lazy: [a, b]")
        outputContains("includes after null: null")
        outputContains("sessionId after null: null")
        outputContains("output: TCP_SERVER port: 100 enabled: false")
        outputContains("destinationFile: true lazy: true")
    }

    def "a lazy value reads back through the eager getter, and eager and lazy changes combine"() {
        buildFile << """
            def ext = tasks.test.jacoco
            ext.lazyProperties.includes = provider { ['a'] }
            ext.includes.add('b')
            println("eager add after lazy binding: " + ext.includes)

            ext.sessionId = 'eager'
            ext.lazyProperties.sessionId = provider { 'lazy' }
            println("lazy after eager: " + ext.sessionId)

            ext.lazyProperties.sessionId = provider { 'lazy2' }
            ext.sessionId = 'eager2'
            println("eager after lazy: " + ext.sessionId)

            ext.lazyProperties.excludes = provider { ['x'] }
            ext.excludes = ['y']
            println("eager set after lazy binding: " + ext.excludes)

            ext.lazyProperties.port = provider { 6301 }
            println("port: " + ext.port)
        """

        when:
        succeeds("help")

        then:
        outputContains("eager add after lazy binding: [a, b]")
        outputContains("lazy after eager: lazy")
        outputContains("eager after lazy: eager2")
        outputContains("eager set after lazy binding: [y]")
        outputContains("port: 6301")
    }

    def "lazy values reach the agent"() {
        buildFile << """
            test.jacoco.lazyProperties.sessionId = provider { 'lazy-session' }
            test.jacoco.lazyProperties.includes.add('org.gradle.*')
            test.doLast {
                def agent = allJvmArgs.find { it.startsWith('-javaagent:') }
                println("agent has includes: " + agent.contains('includes=org.gradle.*'))
                println("agent has sessionid: " + agent.contains('sessionid=lazy-session'))
            }
        """

        when:
        succeeds("test")

        then:
        outputContains("agent has includes: true")
        outputContains("agent has sessionid: true")
        sessionIdInExecutionData("build/jacoco/test.exec", "lazy-session")
    }

    def "input names do not change and the lazy properties add no input"() {
        buildFile << """
            test.doLast {
                println("inputs: " + inputs.properties.keySet().findAll { it.contains('jacoco') }.sort())
            }
        """

        when:
        succeeds("test")

        then:
        outputContains('inputs: [jvmArgumentProviders.jacocoAgent$0, jvmArgumentProviders.jacocoAgent$0.jacoco, jvmArgumentProviders.jacocoAgent$0.jacoco.address, jvmArgumentProviders.jacocoAgent$0.jacoco.dumpOnExit, jvmArgumentProviders.jacocoAgent$0.jacoco.enabled, jvmArgumentProviders.jacocoAgent$0.jacoco.excludeClassLoaders, jvmArgumentProviders.jacocoAgent$0.jacoco.excludes, jvmArgumentProviders.jacocoAgent$0.jacoco.includeNoLocationClasses, jvmArgumentProviders.jacocoAgent$0.jacoco.includes, jvmArgumentProviders.jacocoAgent$0.jacoco.jmx, jvmArgumentProviders.jacocoAgent$0.jacoco.output, jvmArgumentProviders.jacocoAgent$0.jacoco.port, jvmArgumentProviders.jacocoAgent$0.jacoco.sessionId]')
    }

    def "lazy sessionId bound to a task output runs that task first"() {
        buildFile << """
            tasks.withType(Test).configureEach {
                extensions.getByType(JacocoTaskExtension).lazyProperties.sessionId = producer.flatMap { it.outputFile }.map { it.asFile.text }
            }
        """

        when:
        succeeds("test")

        then:
        result.assertTaskScheduled(":producer")
        result.assertTaskOrder(":producer", ":test")
        sessionIdInExecutionData("build/jacoco/test.exec", "from-producer")
    }

    def "lazy includes bound to a task output runs that task first"() {
        buildFile << """
            producer.configure { content = "org.gradle.*" }
            test.jacoco.lazyProperties.includes.add(producer.flatMap { it.outputFile }.map { it.asFile.text })
            test.doLast {
                println("agent has includes: " + allJvmArgs.find { it.startsWith('-javaagent:') }.contains('includes=org.gradle.*'))
            }
        """

        when:
        succeeds("test")

        then:
        result.assertTaskScheduled(":producer")
        result.assertTaskOrder(":producer", ":test")
        outputContains("agent has includes: true")
    }

    // The argument provider reads it to decide whether it has nested inputs.
    // With the configuration cache, the doNotCacheIf predicate also reads it when the entry is stored.
    def "lazy enabled bound to a task output fails before the producer runs"() {
        buildFile << """
            producer.configure { content = "false" }
            test.jacoco.lazyProperties.enabled = producer.flatMap { it.outputFile }.map { it.asFile.text.toBoolean() }
        """

        when:
        fails("test")

        then:
        outputDoesNotContain("> Task :producer")
        failure.assertHasCause("Failed to query the value of extension 'jacoco' property 'lazyProperties.enabled'.")
    }

    @ToBeFixedForConfigurationCache(because = "JacocoPluginExtension.applyTo reads output in providers.provider { } for doNotCacheIf and the cleanup action, which the configuration cache evaluates when it stores the entry")
    def "lazy output bound to a task output runs that task first"() {
        buildFile << """
            producer.configure { content = "NONE" }
            test.jacoco.lazyProperties.output = producer.flatMap { it.outputFile }.map { JacocoTaskExtension.Output.valueOf(it.asFile.text) }
            test.doLast {
                println("agent has output: " + allJvmArgs.find { it.startsWith('-javaagent:') }.contains('output=none'))
            }
        """

        when:
        succeeds("test")

        then:
        result.assertTaskOrder(":producer", ":test")
        outputContains("agent has output: true")
        file("build/jacoco/test.exec").assertDoesNotExist()
    }

    def "destinationFile set through the lazy property is used by the agent and by the report"() {
        buildFile << """
            test.jacoco.lazyProperties.destinationFile = layout.buildDirectory.file("custom/coverage.exec")
            println("destinationFile: " + (test.jacoco.destinationFile == file("build/custom/coverage.exec")))
        """

        when:
        succeeds("test", "jacocoTestReport")

        then:
        outputContains("destinationFile: true")
        file("build/custom/coverage.exec").assertIsFile()
        file("build/jacoco/test.exec").assertDoesNotExist()
        executedAndNotSkipped(":jacocoTestReport")
        file("build/reports/jacoco/test/html/index.html").assertIsFile()
    }

    private void sessionIdInExecutionData(String path, String sessionId) {
        def content = new String(file(path).bytes, StandardCharsets.ISO_8859_1)
        assert content.contains(sessionId)
    }
}
