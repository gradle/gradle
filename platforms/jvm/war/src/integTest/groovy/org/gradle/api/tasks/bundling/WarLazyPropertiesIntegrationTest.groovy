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

package org.gradle.api.tasks.bundling

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture

import java.util.zip.ZipFile

class WarLazyPropertiesIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        file("libs/a.jar").text = "a"
        file("libs/b.jar").text = "b"
        file("classes/C.class").text = "c"
    }

    private List<String> warEntries(String path = "build/out/test.war") {
        def zip = new ZipFile(file(path))
        try {
            return zip.entries().collect { it.name }.findAll { !it.endsWith("/") && !it.startsWith("META-INF") }.sort()
        } finally {
            zip.close()
        }
    }

    private void standaloneWar() {
        buildFile """
            def names = { fc -> fc == null ? null : fc.files*.name.sort() }
            def war = tasks.register("myWar", War) {
                destinationDirectory = layout.buildDirectory.dir("out")
                archiveFileName = "test.war"
            }.get()
        """
    }

    def "classpath is null when unset and the lazy collection when set"() {
        standaloneWar()
        buildFile """
            println("unset: " + war.classpath)
            def value = files("libs/a.jar")
            war.classpath = value
            println("set value: " + war.classpath.is(value))
            println("lazy: " + war.classpath.is(war.lazyProperties.classpath))
            println("configurable: " + (war.classpath instanceof ConfigurableFileCollection))
            war.classpath = configurations.create("custom")
            war.classpath.from("libs/b.jar")
            println("from after a configuration was set: " + names(war.classpath))
            war.classpath = null
            println("null: " + names(war.classpath))
        """

        when:
        succeeds("help")

        then:
        outputContains("unset: null")
        outputContains("set value: false")
        outputContains("lazy: true")
        outputContains("configurable: true")
        outputContains("from after a configuration was set: [b.jar]")
        outputContains("null: []")
    }

    def "classpath can refer to itself through the eager getter and setter"() {
        standaloneWar()
        buildFile """
            war.classpath = files("a.jar")
            war.classpath = war.classpath + files("b.jar")
            println("plus: " + names(war.classpath))
            war.classpath = files(war.classpath, "c.jar")
            println("files: " + names(war.classpath))
            war.classpath = war.classpath.filter { it.name != "a.jar" }
            println("filter: " + names(war.classpath))
            war.classpath = war.classpath - files("b.jar")
            println("minus: " + names(war.classpath))
            war.classpath("d.jar")
            println("classpath(): " + names(war.classpath))
        """

        when:
        succeeds("help")

        then:
        outputContains("plus: [a.jar, b.jar]")
        outputContains("files: [a.jar, b.jar, c.jar]")
        outputContains("filter: [b.jar, c.jar]")
        outputContains("minus: [c.jar]")
        outputContains("classpath(): [c.jar, d.jar]")
    }

    def "a classpath read before classpath() sees the later addition, unlike before"() {
        standaloneWar()
        buildFile """
            war.classpath = files("a.jar")
            def held = war.classpath
            war.classpath("b.jar")
            println("held: " + names(held) + " same: " + held.is(war.classpath))
        """

        when:
        succeeds("help")

        then:
        outputContains("held: [a.jar, b.jar] same: true")
    }

    def "classpath() without files declares an empty classpath"() {
        standaloneWar()
        buildFile """
            war.classpath()
            println("empty: " + names(war.classpath))
            def other = tasks.register("otherWar", War).get()
            other.classpath((Object[]) null)
            println("null array: " + names(other.classpath))
        """

        when:
        succeeds("help")

        then:
        outputContains("empty: []")
        outputContains("null array: []")
    }

    def "classpath set #description reaches WEB-INF"() {
        standaloneWar()
        buildFile """
            war.$assignment
        """

        when:
        succeeds("myWar")

        then:
        warEntries() == ["WEB-INF/classes/C.class", "WEB-INF/lib/a.jar", "WEB-INF/lib/b.jar"]

        where:
        description            | assignment
        "eagerly"              | 'classpath = files("libs/a.jar", "classes")\nwar.classpath("libs/b.jar")'
        "lazily"               | 'lazyProperties.classpath.from(fileTree("libs"), providers.provider { "classes" })'
        "through both"         | 'classpath = files("classes")\nwar.lazyProperties.classpath.from("libs/a.jar")\nwar.classpath(files("libs/b.jar"))'
    }

    def "classpath bound to a task output runs that task first"() {
        standaloneWar()
        buildFile """
            abstract class Producer extends DefaultTask {
                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void run() {
                    outputFile.get().asFile.text = "produced"
                }
            }
            def producer = tasks.register("producer", Producer) {
                outputFile = layout.buildDirectory.file("produced.jar")
            }
            war.lazyProperties.classpath.from(producer.flatMap { it.outputFile })
        """

        when:
        succeeds("myWar")

        then:
        result.assertTaskOrder(":producer", ":myWar")
        warEntries() == ["WEB-INF/lib/produced.jar"]
    }

    def "the war plugin classpath is lazy and can be extended lazily"() {
        buildFile """
            plugins {
                id("war")
            }
            tasks.war {
                destinationDirectory = layout.buildDirectory.dir("out")
                archiveFileName = "test.war"
                lazyProperties.classpath.from("libs/b.jar")
            }
            dependencies {
                implementation(files("libs/a.jar"))
            }
            println("plugin: " + tasks.war.classpath.is(tasks.war.lazyProperties.classpath))
        """

        when:
        succeeds("war")

        then:
        outputContains("plugin: true")
        warEntries() == ["WEB-INF/lib/a.jar", "WEB-INF/lib/b.jar"]
    }

    def "convention mapping is the convention: classpath() adds to it and setClasspath() replaces it"() {
        standaloneWar()
        buildFile """
            war.conventionMapping.map("classpath") { files("libs/a.jar") }
            println("mapped: " + names(war.classpath))
            war.classpath("libs/b.jar")
            println("after classpath(): " + names(war.classpath))
            war.classpath = files("libs/b.jar")
            println("after setClasspath(): " + names(war.classpath))
        """

        when:
        succeeds("help")

        then:
        outputContains("mapped: [a.jar]")
        // Before, the mapping won over classpath(), so this was [a.jar]
        outputContains("after classpath(): [a.jar, b.jar]")
        outputContains("after setClasspath(): [b.jar]")
    }

    def "convention mapping does not replace the classpath of the war plugin"() {
        buildFile """
            plugins {
                id("war")
            }
            dependencies {
                implementation(files("libs/a.jar"))
            }
            tasks.war.conventionMapping.map("classpath") { files("libs/b.jar") }
            println("classpath: " + tasks.war.classpath.files*.name.findAll { it.endsWith(".jar") }.sort())
        """

        when:
        succeeds("help")

        then:
        // Before, the mapping won over the classpath that the plugin adds with classpath(), so this was [b.jar]
        outputContains("classpath: [a.jar]")
    }

    def "Kotlin DSL forms set the classpath"() {
        buildKotlinFile """
            val war = tasks.register<War>("myWar") {
                destinationDirectory = layout.buildDirectory.dir("out")
                archiveFileName = "test.war"
                setClasspath(files("libs/a.jar"))
                classpath(files("classes"))
                lazyProperties.classpath.from("libs/b.jar")
            }
        """

        when:
        succeeds("myWar")

        then:
        warEntries() == ["WEB-INF/classes/C.class", "WEB-INF/lib/a.jar", "WEB-INF/lib/b.jar"]
    }

    def "classpath survives the configuration cache and the up-to-date check reacts to a lazy change"() {
        def configurationCache = new ConfigurationCacheFixture(this)
        file("classpath.txt").text = "libs/a.jar"
        standaloneWar()
        buildFile """
            war.classpath("classes")
            war.lazyProperties.classpath.from(providers.fileContents(layout.projectDirectory.file("classpath.txt")).asText.map { it.trim() })
        """

        when:
        succeeds("myWar", "--configuration-cache")

        then:
        configurationCache.assertStateStored()
        warEntries() == ["WEB-INF/classes/C.class", "WEB-INF/lib/a.jar"]

        when:
        succeeds("myWar", "--configuration-cache")

        then:
        configurationCache.assertStateLoaded()
        skipped(":myWar")

        when:
        file("classpath.txt").text = "libs/b.jar"
        succeeds("myWar", "--configuration-cache")

        then:
        configurationCache.assertStateLoaded()
        executedAndNotSkipped(":myWar")
        warEntries() == ["WEB-INF/classes/C.class", "WEB-INF/lib/b.jar"]
    }

    def "an unset classpath stays null after the configuration cache"() {
        standaloneWar()
        buildFile """
            war.doLast {
                println("classpath: " + classpath)
            }
            war.from("libs")
        """

        when:
        succeeds("myWar", "--configuration-cache")
        succeeds("myWar", "--configuration-cache", "--rerun-tasks")

        then:
        outputContains("classpath: null")
    }

    def "the lazy properties accessor adds no input property"() {
        standaloneWar()
        buildFile """
            war.classpath = files("libs/a.jar")
            war.doLast {
                println("inputs: " + inputs.properties.keySet().findAll { !it.startsWith("rootSpec") }.sort())
            }
        """

        when:
        succeeds("myWar")

        then:
        outputContains("inputs: [entryCompression, manifestContentCharset, metadataCharset, preserveFileTimestamps, reproducibleFileOrder, reproducibleFileTimestamp, zip64]")
    }
}
