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

package org.gradle.plugins.ear

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture
import org.gradle.integtests.fixtures.modes.ToBeFixedForConfigurationCache

import java.util.zip.ZipFile

class EarLazyPropertiesIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        file("earLib.jar").text = "lib"
        settingsFile "rootProject.name = 'root'"
        buildFile """
            plugins {
                id("ear")
            }
            dependencies {
                earlib(files("earLib.jar"))
            }
        """
    }

    private List<String> earEntries() {
        def zip = new ZipFile(file("build/libs/root.ear"))
        try {
            return zip.entries().collect { it.name }.findAll { !it.endsWith("/") && !it.startsWith("META-INF") }.sort()
        } finally {
            zip.close()
        }
    }

    private String descriptor() {
        def zip = new ZipFile(file("build/libs/root.ear"))
        try {
            return zip.getInputStream(zip.getEntry("META-INF/application.xml")).text
        } finally {
            zip.close()
        }
    }

    def "libDirName follows the plugin convention, an explicit value wins, and null hides the convention"() {
        buildFile """
            def ear = tasks.named("ear").get()
            println("convention: " + [ear.libDirName, ear.lazyProperties.libDirName.get()])
            ear.libDirName = "jars"
            println("explicit: " + ear.libDirName)
            ear.libDirName = null
            println("null: " + ear.libDirName)
            ear.lazyProperties.libDirName.unset()
            println("unset: " + ear.libDirName)
            ear.lazyProperties.libDirName = "lazy"
            println("lazy: " + ear.libDirName)
        """

        when:
        succeeds("help")

        then:
        outputContains("convention: [lib, lib]")
        outputContains("explicit: jars")
        outputContains("null: null")
        outputContains("unset: lib")
        outputContains("lazy: lazy")
    }

    def "libDirName set #description reaches the archive and the descriptor"() {
        buildFile """
            tasks.ear {
                $assignment
            }
        """

        when:
        succeeds("ear")

        then:
        earEntries() == ["jars/earLib.jar"]
        descriptor().contains("<library-directory>jars</library-directory>")

        where:
        description | assignment
        "eagerly"   | 'libDirName = "jars"'
        "lazily"    | 'lazyProperties.libDirName = providers.provider { "jars" }'
    }

    def "libDirName set to null puts the libraries into lib"() {
        buildFile """
            tasks.ear {
                libDirName = null
            }
        """

        when:
        succeeds("ear")

        then:
        earEntries() == ["lib/earLib.jar"]
        !descriptor().contains("library-directory")
    }

    @ToBeFixedForConfigurationCache(because = "The configuration cache stores the destination of the lib copy spec, which reads libDirName before the producer ran")
    def "libDirName bound to a task output runs that task first when no deployment descriptor is generated"() {
        buildFile """
            abstract class Producer extends DefaultTask {
                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void run() {
                    outputFile.get().asFile.text = "jars"
                }
            }
            def producer = tasks.register("producer", Producer) {
                outputFile = layout.buildDirectory.file("lib-dir.txt")
            }
            tasks.ear {
                lazyProperties.libDirName = producer.flatMap { it.outputFile }.map { it.asFile.text }
                generateDeploymentDescriptor = false
            }
        """

        when:
        succeeds("ear")

        then:
        result.assertTaskOrder(":producer", ":ear")
        earEntries() == ["jars/earLib.jar"]
    }

    @ToBeFixedForConfigurationCache(because = "The configuration cache stores the destination of the lib copy spec, which reads libDirName before the producer ran")
    def "libDirName bound to a task output runs that task first when a deployment descriptor is generated"() {
        buildFile """
            abstract class Producer extends DefaultTask {
                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void run() {
                    outputFile.get().asFile.text = "jars"
                }
            }
            def producer = tasks.register("producer", Producer) {
                outputFile = layout.buildDirectory.file("lib-dir.txt")
            }
            tasks.ear {
                lazyProperties.libDirName = producer.flatMap { it.outputFile }.map { it.asFile.text }
            }
        """

        when:
        succeeds("ear")

        then:
        result.assertTaskOrder(":producer", ":ear")
        earEntries() == ["jars/earLib.jar"]
        descriptor().contains("<library-directory>jars</library-directory>")
    }

    def "Kotlin DSL forms set libDirName"() {
        buildFile.delete()
        buildKotlinFile """
            plugins {
                ear
            }
            dependencies {
                earlib(files("earLib.jar"))
            }
            tasks.ear {
                libDirName = "eager"
                lazyProperties.libDirName = "jars"
                isZip64 = true
                doLast {
                    println("ear: " + listOf(libDirName, isZip64, metadataCharset))
                }
            }
        """

        when:
        succeeds("ear")

        then:
        outputContains("ear: [jars, true, UTF-8]")
        earEntries() == ["jars/earLib.jar"]
    }

    def "libDirName is read when the configuration cache entry is stored, because the copy spec stores its destination"() {
        def configurationCache = new ConfigurationCacheFixture(this)
        file("lib-dir.txt").text = "jars"
        buildFile """
            tasks.ear {
                lazyProperties.libDirName = providers.fileContents(layout.projectDirectory.file("lib-dir.txt")).asText.map { it.trim() }
            }
        """

        when:
        succeeds("ear", "--configuration-cache")

        then:
        configurationCache.assertStateStored()
        earEntries() == ["jars/earLib.jar"]
        descriptor().contains("<library-directory>jars</library-directory>")

        when:
        succeeds("ear", "--configuration-cache")

        then:
        configurationCache.assertStateLoaded()
        skipped(":ear")

        when:
        file("lib-dir.txt").text = "other"
        succeeds("ear", "--configuration-cache")

        then:
        // The file is an input of the entry, so the entry is stored again instead of reused
        postBuildOutputContains("Configuration cache entry stored.")
        executedAndNotSkipped(":ear")
        earEntries() == ["other/earLib.jar"]
        descriptor().contains("<library-directory>other</library-directory>")
    }

    def "the lazy properties accessor adds no input property"() {
        buildFile """
            tasks.ear {
                doLast {
                    println("inputs: " + inputs.properties.keySet().findAll { !it.startsWith("rootSpec") }.sort())
                }
            }
        """

        when:
        succeeds("ear")

        then:
        outputContains("inputs: [entryCompression, generateDeploymentDescriptor, libDirName, manifestContentCharset, metadataCharset, preserveFileTimestamps, reproducibleFileOrder, reproducibleFileTimestamp, zip64]")
    }
}
