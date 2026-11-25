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

package org.gradle.api.plugins.quality.checkstyle

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import static org.gradle.api.plugins.quality.checkstyle.CheckstylePluginMultiProjectTest.javaClassWithNewLineAtEnd
import static org.gradle.api.plugins.quality.checkstyle.CheckstylePluginMultiProjectTest.simpleCheckStyleConfig

class CheckstyleLazyPropertiesIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile """
            apply plugin: 'java'
            apply plugin: 'checkstyle'

            ${mavenCentralRepository()}
        """
    }

    def "task configProperties keep the entries of the extension when the task map is changed"() {
        buildFile """
            checkstyle.configProperties = [a: 'b']
            tasks.checkstyleMain.configProperties.put('c', 'd')
            println("task: " + tasks.checkstyleMain.configProperties)
            println("extension: " + checkstyle.configProperties)
        """

        when:
        succeeds("help")

        then:
        outputContains("task: [a:b, c:d]")
        outputContains("extension: [a:b]")
    }

    def "maxErrors set on the extension after the task was configured reaches the task"() {
        buildFile """
            def task = tasks.named("checkstyleMain").get()
            println("before: " + task.maxErrors)
            checkstyle.maxErrors = 5
            println("after: " + task.maxErrors)
        """

        when:
        succeeds("help")

        then:
        outputContains("before: 0")
        outputContains("after: 5")
    }

    def "setConfigProperties(null) makes the getter return null"() {
        buildFile """
            tasks.checkstyleMain.configProperties = null
            println("task: " + tasks.checkstyleMain.configProperties)
            checkstyle.configProperties = null
            println("extension: " + checkstyle.configProperties)
            println("checkstyleTest: " + tasks.checkstyleTest.configProperties)
        """

        when:
        succeeds("help")

        then:
        outputContains("task: null")
        outputContains("extension: null")
        outputContains("checkstyleTest: null")
    }

    def "classpath can be extended through the eager getter and setter"() {
        buildFile """
            def task = tasks.checkstyleMain
            task.classpath = task.classpath + files("extra.jar")
            println("classpath: " + task.classpath.files.collect { it.name }.sort())
        """

        when:
        succeeds("help")

        then:
        outputContains("classpath: [extra.jar, main, main]")
    }

    def "classpath can be reduced through the eager getter and setter"() {
        buildFile """
            def task = tasks.checkstyleMain
            task.classpath = task.classpath - files("build/resources/main")
            println("classpath: " + task.classpath.files.collect { it.name }.sort())
        """

        when:
        succeeds("help")

        then:
        outputContains("classpath: [main]")
    }

    def "classpath can be reduced and extended when the getter returns the lazy collection"() {
        buildFile """
            def task = tasks.checkstyleMain
            task.lazyProperties.classpath.from(files("extra.jar"))
            println("lazy: " + task.classpath.is(task.lazyProperties.classpath))
            task.classpath = task.classpath - files("build/resources/main")
            println("minus: " + task.classpath.files.collect { it.name }.sort())
            task.lazyProperties.classpath.from(files("other.jar"))
            task.classpath = task.classpath + files("last.jar")
            println("plus: " + task.classpath.files.collect { it.name }.sort())
        """

        when:
        succeeds("help")

        then:
        outputContains("lazy: true")
        outputContains("minus: [extra.jar, main]")
        outputContains("plus: [extra.jar, last.jar, main, other.jar]")
    }

    def "eager file collection getters return what was set or mapped"() {
        buildFile """
            def task = tasks.checkstyleMain
            println("checkstyleClasspath is configuration: " + task.checkstyleClasspath.is(configurations.checkstyle))
            def custom = files("custom.jar")
            task.checkstyleClasspath = custom
            println("checkstyleClasspath is set value: " + task.checkstyleClasspath.is(custom))
            task.classpath = null
            println("classpath after null: " + task.classpath)
        """

        when:
        succeeds("help")

        then:
        outputContains("checkstyleClasspath is configuration: true")
        outputContains("checkstyleClasspath is set value: true")
        outputContains("classpath after null: null")
    }

    def "the lazy properties accessor adds no input property"() {
        buildFile """
            tasks.checkstyleMain {
                doLast {
                    println("inputs: " + inputs.properties.keySet().sort())
                }
            }
        """
        file('src/main/java/Dummy.java') << javaClassWithNewLineAtEnd()
        file('config/checkstyle/checkstyle.xml') << simpleCheckStyleConfig()

        when:
        succeeds("checkstyleMain")

        then:
        outputContains("inputs: [config, config.inputProperties, configProperties, enableExternalDtdLoad, ignoreFailures, javaLauncher, javaLauncher.metadata, javaLauncher.metadata.languageVersion, javaLauncher.metadata.taskInputs, javaLauncher.metadata.taskInputs.implementation, javaLauncher.metadata.taskInputs.languageVersion, javaLauncher.metadata.taskInputs.vendor, maxErrors, maxHeapSize, maxWarnings, minHeapSize, reports, reports.enabledReports.html, reports.enabledReports.html.displayName, reports.enabledReports.html.name, reports.enabledReports.html.outputType, reports.enabledReports.html.required, reports.enabledReports.xml, reports.enabledReports.xml.displayName, reports.enabledReports.xml.name, reports.enabledReports.xml.outputType, reports.enabledReports.xml.required]")
    }

    def "lazy maxErrors bound to a mapped task output runs that task first"() {
        file('src/main/java/Dummy.java') << javaClassWithNewLineAtEnd()
        file('config/checkstyle/checkstyle.xml') << simpleCheckStyleConfig()
        buildFile """
            abstract class Producer extends DefaultTask {
                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void run() {
                    outputFile.get().asFile.text = "3"
                }
            }

            def producer = tasks.register("producer", Producer) {
                outputFile = layout.buildDirectory.file("max-errors.txt")
            }

            tasks.named("checkstyleMain", Checkstyle) {
                lazyProperties.maxErrors = producer.flatMap { it.outputFile }.map { it.asFile.text as Integer }
                doLast {
                    println("maxErrors: " + maxErrors)
                }
            }
        """

        when:
        succeeds("checkstyleMain")

        then:
        result.assertTaskScheduled(":producer")
        result.assertTaskOrder(":producer", ":checkstyleMain")
        outputContains("maxErrors: 3")
    }
}
