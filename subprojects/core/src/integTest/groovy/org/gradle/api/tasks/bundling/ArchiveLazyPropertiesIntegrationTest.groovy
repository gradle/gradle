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

import org.gradle.api.internal.file.archive.ZipEntryConstants
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture
import org.gradle.integtests.fixtures.modes.ToBeFixedForConfigurationCache

import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class ArchiveLazyPropertiesIntegrationTest extends AbstractIntegrationSpec {

    private static final long FILE_TIME = 978307200000L // 2001-01-01T00:00:00Z

    def setup() {
        file("src/a.txt").text = "a"
        file("src/a.txt").setLastModified(FILE_TIME)
        buildFile """
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

            tasks.register("zip", Zip) {
                from("src")
                destinationDirectory = layout.buildDirectory.dir("out")
                archiveFileName = "test.zip"
            }

            tasks.register("tar", Tar) {
                from("src")
                destinationDirectory = layout.buildDirectory.dir("out")
                archiveBaseName = "test"
            }
        """
    }

    private ZipEntry zipEntry(String name) {
        def zip = new ZipFile(file("build/out/test.zip"))
        try {
            return zip.getEntry(name)
        } finally {
            zip.close()
        }
    }

    private static boolean isGzip(File file) {
        try {
            new GZIPInputStream(new FileInputStream(file)).withCloseable { it.read() }
            return true
        } catch (IOException ignored) {
            return false
        }
    }

    def "eager getters return the defaults"() {
        buildFile """
            def zip = tasks.named("zip").get()
            def tar = tasks.named("tar").get()
            println("zip: " + [zip.preserveFileTimestamps, zip.reproducibleFileOrder, zip.entryCompression, zip.zip64, zip.metadataCharset])
            println("tar: " + [tar.preserveFileTimestamps, tar.reproducibleFileOrder, tar.compression])
            def lazy = zip.lazyProperties
            println("lazy zip: " + [lazy.preserveFileTimestamps.get(), lazy.reproducibleFileOrder.get(), lazy.entryCompression.get(), lazy.zip64.get(), lazy.metadataCharset.present])
            println("lazy tar: " + tar.lazyProperties.compression.get())
        """

        when:
        succeeds("help")

        then:
        outputContains("zip: [false, true, DEFLATED, false, null]")
        outputContains("tar: [false, true, NONE]")
        outputContains("lazy zip: [false, true, DEFLATED, false, false]")
        outputContains("lazy tar: NONE")
    }

    def "eager values are stored in the lazy properties"() {
        buildFile """
            tasks.zip {
                preserveFileTimestamps = true
                reproducibleFileOrder = false
                entryCompression = ZipEntryCompression.STORED
                zip64 = true
                metadataCharset = "ISO-8859-1"
            }
            tasks.tar {
                compression = Compression.GZIP
            }
            def lazy = tasks.named("zip").get().lazyProperties
            println("lazy zip: " + [lazy.preserveFileTimestamps.get(), lazy.reproducibleFileOrder.get(), lazy.entryCompression.get(), lazy.zip64.get(), lazy.metadataCharset.get()])
            println("lazy tar: " + tasks.named("tar").get().lazyProperties.compression.get())
        """

        when:
        succeeds("help")

        then:
        outputContains("lazy zip: [true, false, STORED, true, ISO-8859-1]")
        outputContains("lazy tar: GZIP")
    }

    def "lazy values are read by the eager getters and reach the archives"() {
        buildFile """
            tasks.zip {
                lazyProperties.preserveFileTimestamps = providers.provider { true }
                lazyProperties.reproducibleFileOrder = false
                lazyProperties.entryCompression = providers.provider { ZipEntryCompression.STORED }
                lazyProperties.zip64 = true
                lazyProperties.metadataCharset = "ISO-8859-1"
                doLast {
                    println("zip: " + [preserveFileTimestamps, reproducibleFileOrder, entryCompression, zip64, metadataCharset])
                }
            }
            tasks.tar {
                lazyProperties.compression = providers.provider { Compression.GZIP }
                doLast {
                    println("tar: " + compression)
                }
            }
        """

        when:
        succeeds("zip", "tar")

        then:
        outputContains("zip: [true, false, STORED, true, ISO-8859-1]")
        outputContains("tar: GZIP")
        def entry = zipEntry("a.txt")
        entry.method == ZipEntry.STORED
        entry.time != ZipEntryConstants.CONSTANT_TIME_FOR_ZIP_ENTRIES
        isGzip(file("build/out/test.tgz"))
    }

    def "Groovy DSL forms set the values: #description"() {
        buildFile """
            tasks.zip {
                $assignments
                doLast {
                    println("zip: " + [preserveFileTimestamps, reproducibleFileOrder, entryCompression, zip64])
                }
            }
        """

        when:
        succeeds("zip")

        then:
        outputContains("zip: [true, false, STORED, true]")
        zipEntry("a.txt").method == ZipEntry.STORED

        where:
        description     | assignments
        "assignment"    | "preserveFileTimestamps = true; reproducibleFileOrder = false; entryCompression = ZipEntryCompression.STORED; zip64 = true"
        "string enum"   | "preserveFileTimestamps = true; reproducibleFileOrder = false; entryCompression = 'STORED'; zip64 = true"
        "setter call"   | "setPreserveFileTimestamps(true); setReproducibleFileOrder(false); setEntryCompression(ZipEntryCompression.STORED); setZip64(true)"
        "lazy property" | "lazyProperties.preserveFileTimestamps = true; lazyProperties.reproducibleFileOrder = false; lazyProperties.entryCompression = ZipEntryCompression.STORED; lazyProperties.zip64 = true"
    }

    def "Kotlin DSL forms set the values"() {
        buildFile.delete()
        buildKotlinFile """
            tasks.register<Zip>("zip") {
                from("src")
                destinationDirectory = layout.buildDirectory.dir("out")
                archiveFileName = "test.zip"
                isPreserveFileTimestamps = true
                isReproducibleFileOrder = false
                entryCompression = ZipEntryCompression.STORED
                isZip64 = true
                // The getter is nullable and the setter is not, so Kotlin has no property to assign, as before
                setMetadataCharset("ISO-8859-1")
                doLast {
                    println("zip: " + listOf(isPreserveFileTimestamps, isReproducibleFileOrder, entryCompression, isZip64, metadataCharset))
                }
            }
            tasks.register<Tar>("tar") {
                from("src")
                destinationDirectory = layout.buildDirectory.dir("out")
                archiveBaseName = "test"
                lazyProperties.compression = Compression.GZIP
                lazyProperties.preserveFileTimestamps = providers.provider { true }
                doLast {
                    println("tar: " + listOf(compression, isPreserveFileTimestamps))
                }
            }
        """

        when:
        succeeds("zip", "tar")

        then:
        outputContains("zip: [true, false, STORED, true, ISO-8859-1]")
        outputContains("tar: [GZIP, true]")
        zipEntry("a.txt").method == ZipEntry.STORED
        isGzip(file("build/out/test.tgz"))
    }

    def "entryCompression bound to a task output runs that task first"() {
        buildFile """
            def producer = tasks.register("producer", Producer) {
                content = "STORED"
                outputFile = layout.buildDirectory.file("compression.txt")
            }
            tasks.zip {
                lazyProperties.entryCompression = producer.flatMap { it.outputFile }.map { ZipEntryCompression.valueOf(it.asFile.text) }
            }
        """

        when:
        succeeds("zip")

        then:
        result.assertTaskOrder(":producer", ":zip")
        zipEntry("a.txt").method == ZipEntry.STORED
    }

    @ToBeFixedForConfigurationCache(because = "The configuration cache evaluates archiveFileName when the entry is stored, and the name reads the compression")
    def "tar compression bound to a task output runs that task first and names the archive"() {
        buildFile """
            def producer = tasks.register("producer", Producer) {
                content = "GZIP"
                outputFile = layout.buildDirectory.file("compression.txt")
            }
            tasks.tar {
                lazyProperties.compression = producer.flatMap { it.outputFile }.map { Compression.valueOf(it.asFile.text) }
            }
        """

        when:
        succeeds("tar")

        then:
        result.assertTaskOrder(":producer", ":tar")
        isGzip(file("build/out/test.tgz"))
    }

    def "convention mapping becomes the convention, an explicit value wins, and unset brings the convention back"() {
        buildFile """
            def zip = tasks.named("zip").get()
            zip.conventionMapping.map("entryCompression") { ZipEntryCompression.STORED }
            zip.conventionMapping.map("zip64") { true }
            zip.conventionMapping.map("preserveFileTimestamps") { true }
            println("mapped: " + [zip.entryCompression, zip.zip64, zip.preserveFileTimestamps])
            zip.entryCompression = ZipEntryCompression.DEFLATED
            zip.zip64 = false
            println("explicit: " + [zip.entryCompression, zip.zip64, zip.preserveFileTimestamps])
            zip.lazyProperties.entryCompression.unset()
            println("unset: " + zip.entryCompression)
        """

        when:
        succeeds("help")

        then:
        outputContains("mapped: [STORED, true, true]")
        outputContains("explicit: [DEFLATED, false, true]")
        outputContains("unset: STORED")
    }

    def "convention mapping reaches the archive"() {
        buildFile """
            tasks.zip {
                conventionMapping.map("entryCompression") { ZipEntryCompression.STORED }
            }
        """

        when:
        succeeds("zip")

        then:
        zipEntry("a.txt").method == ZipEntry.STORED
    }

    def "eager null hides the convention and lazy null reveals it"() {
        buildFile """
            def zip = tasks.named("zip").get()
            def tar = tasks.named("tar").get()
            zip.entryCompression = null
            tar.compression = null
            println("eager null: " + [zip.entryCompression, tar.compression])
            zip.lazyProperties.entryCompression.set(null)
            tar.lazyProperties.compression.set(null)
            println("lazy null: " + [zip.entryCompression, tar.compression])
            zip.lazyProperties.metadataCharset.set(null)
            println("metadataCharset: " + zip.metadataCharset)
        """

        when:
        succeeds("help")

        then:
        outputContains("eager null: [null, null]")
        outputContains("lazy null: [DEFLATED, NONE]")
        outputContains("metadataCharset: null")
    }

    def "eager null entryCompression fails validation as before"() {
        buildFile """
            tasks.zip {
                entryCompression = null
            }
        """

        when:
        fails("zip")

        then:
        failure.assertHasDescription("A problem was found with the configuration of task ':zip' (type 'Zip').")
        outputContains("property 'entryCompression' doesn't have a configured value")
    }

    def "metadataCharset setter validates as before and a lazy value is validated at execution"() {
        buildFile """
            tasks.zip {
                if (providers.gradleProperty("eager").present) {
                    metadataCharset = "UNSUPPORTED"
                } else {
                    lazyProperties.metadataCharset = "UNSUPPORTED"
                }
            }
        """

        when:
        fails("zip", "-Peager")

        then:
        failure.assertHasCause("Charset for metadataCharset 'UNSUPPORTED' is not supported by your JVM")

        when:
        fails("zip")

        then:
        failureDescriptionStartsWith("Execution failed for task ':zip'")
        failure.assertHasCause("Charset for metadataCharset 'UNSUPPORTED' is not supported by your JVM")
    }

    def "up-to-date check reacts to a change of a lazy value"() {
        buildFile """
            tasks.zip {
                lazyProperties.entryCompression = providers.gradleProperty("compression").map { ZipEntryCompression.valueOf(it) }
                lazyProperties.zip64 = providers.gradleProperty("zip64").map { it.toBoolean() }.orElse(false)
            }
        """

        when:
        succeeds("zip", "-Pcompression=STORED")

        then:
        executedAndNotSkipped(":zip")
        zipEntry("a.txt").method == ZipEntry.STORED

        when:
        succeeds("zip", "-Pcompression=STORED")

        then:
        skipped(":zip")

        when:
        succeeds("zip", "-Pcompression=DEFLATED")

        then:
        executedAndNotSkipped(":zip")
        zipEntry("a.txt").method == ZipEntry.DEFLATED

        when:
        succeeds("zip", "-Pcompression=DEFLATED", "-Pzip64=true")

        then:
        executedAndNotSkipped(":zip")
    }

    def "lazy values survive the configuration cache and are read when the task runs"() {
        def configurationCache = new ConfigurationCacheFixture(this)
        file("settings.txt").text = "STORED"
        buildFile """
            tasks.zip {
                lazyProperties.entryCompression = providers.fileContents(layout.projectDirectory.file("settings.txt")).asText.map { ZipEntryCompression.valueOf(it.trim()) }
                preserveFileTimestamps = true
                zip64 = true
                doLast {
                    println("zip: " + [entryCompression, preserveFileTimestamps, zip64, reproducibleFileOrder, metadataCharset])
                }
            }
            tasks.tar {
                compression = Compression.BZIP2
                doLast {
                    println("tar: " + compression)
                }
            }
        """

        when:
        succeeds("zip", "tar", "--configuration-cache")

        then:
        configurationCache.assertStateStored()
        outputContains("zip: [STORED, true, true, true, null]")
        outputContains("tar: BZIP2")
        zipEntry("a.txt").method == ZipEntry.STORED
        file("build/out/test.tbz2").exists()

        when:
        file("settings.txt").text = "DEFLATED"
        succeeds("zip", "tar", "--configuration-cache")

        then:
        configurationCache.assertStateLoaded()
        executedAndNotSkipped(":zip")
        skipped(":tar")
        outputContains("zip: [DEFLATED, true, true, true, null]")
        zipEntry("a.txt").method == ZipEntry.DEFLATED
    }

    def "the lazy properties accessor adds no input property"() {
        buildFile """
            tasks.zip {
                doLast {
                    println("zip inputs: " + inputs.properties.keySet().findAll { !it.startsWith("rootSpec") }.sort())
                }
            }
            tasks.tar {
                doLast {
                    println("tar inputs: " + inputs.properties.keySet().findAll { !it.startsWith("rootSpec") }.sort())
                }
            }
        """

        when:
        succeeds("zip", "tar")

        then:
        outputContains("zip inputs: [entryCompression, metadataCharset, preserveFileTimestamps, reproducibleFileOrder, reproducibleFileTimestamp, zip64]")
        outputContains("tar inputs: [compression, preserveFileTimestamps, reproducibleFileOrder, reproducibleFileTimestamp]")
    }
}
