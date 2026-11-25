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

import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class JarLazyPropertiesIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        file("src/a.txt").text = "a"
        file("src/a.txt").setLastModified(978307200000L)
        buildFile """
            tasks.register("myJar", Jar) {
                from("src")
                destinationDirectory = layout.buildDirectory.dir("out")
                archiveFileName = "test.jar"
                manifest {
                    attributes("Name": "caf\\u00e9")
                }
            }
        """
    }

    private ZipEntry jarEntry(String name) {
        def zip = new ZipFile(file("build/out/test.jar"))
        try {
            return zip.getEntry(name)
        } finally {
            zip.close()
        }
    }

    private byte[] manifestBytes() {
        def zip = new ZipFile(file("build/out/test.jar"))
        try {
            return zip.getInputStream(zip.getEntry("META-INF/MANIFEST.MF")).bytes
        } finally {
            zip.close()
        }
    }

    private static boolean contains(byte[] bytes, byte[] part) {
        Collections.indexOfSubList(bytes.toList(), part.toList()) >= 0
    }

    def "charsets default to UTF-8, an eager null fails as before, and a lazy null reveals the default"() {
        buildFile """
            def jar = tasks.named("myJar").get()
            println("defaults: " + [jar.metadataCharset, jar.manifestContentCharset])
            println("lazy defaults: " + [jar.lazyProperties.metadataCharset.get(), jar.lazyProperties.manifestContentCharset.get()])
            jar.metadataCharset = "ISO-8859-1"
            jar.lazyProperties.metadataCharset.set(null)
            println("lazy null: " + jar.metadataCharset)
            try {
                jar.metadataCharset = null
            } catch (InvalidUserDataException e) {
                println("eager null: " + e.message)
            }
            try {
                jar.manifestContentCharset = "UNSUPPORTED"
            } catch (InvalidUserDataException e) {
                println("eager unsupported: " + e.message)
            }
        """

        when:
        succeeds("help")

        then:
        outputContains("defaults: [UTF-8, UTF-8]")
        outputContains("lazy defaults: [UTF-8, UTF-8]")
        outputContains("lazy null: UTF-8")
        outputContains("eager null: metadataCharset must not be null")
        outputContains("eager unsupported: Charset for manifestContentCharset 'UNSUPPORTED' is not supported by your JVM")
    }

    def "manifestContentCharset set #description reaches the manifest"() {
        buildFile """
            tasks.named("myJar") {
                $assignment
                doLast {
                    println("manifestContentCharset: " + manifestContentCharset)
                }
            }
        """

        when:
        succeeds("myJar")

        then:
        outputContains("manifestContentCharset: ISO-8859-1")
        def bytes = manifestBytes()
        contains(bytes, "café".getBytes(StandardCharsets.ISO_8859_1))
        !contains(bytes, "café".getBytes(StandardCharsets.UTF_8))

        where:
        description | assignment
        "eagerly"   | 'manifestContentCharset = "ISO-8859-1"'
        "lazily"    | 'lazyProperties.manifestContentCharset = providers.provider { "ISO-8859-1" }'
    }

    def "an unsupported manifestContentCharset set lazily fails at execution"() {
        buildFile """
            tasks.named("myJar") {
                lazyProperties.manifestContentCharset = "UNSUPPORTED"
            }
        """

        when:
        fails("myJar")

        then:
        failureDescriptionStartsWith("Execution failed for task ':myJar'")
        failure.assertHasCause("Charset for manifestContentCharset 'UNSUPPORTED' is not supported by your JVM")
    }

    def "manifestContentCharset bound to a task output runs that task first"() {
        buildFile """
            abstract class Producer extends DefaultTask {
                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void run() {
                    outputFile.get().asFile.text = "ISO-8859-1"
                }
            }
            def producer = tasks.register("producer", Producer) {
                outputFile = layout.buildDirectory.file("charset.txt")
            }
            tasks.named("myJar") {
                lazyProperties.manifestContentCharset = producer.flatMap { it.outputFile }.map { it.asFile.text }
            }
        """

        when:
        succeeds("myJar")

        then:
        result.assertTaskOrder(":producer", ":myJar")
        contains(manifestBytes(), "café".getBytes(StandardCharsets.ISO_8859_1))
    }

    def "Kotlin DSL forms set the values"() {
        buildFile.delete()
        buildKotlinFile """
            tasks.register<Jar>("myJar") {
                from("src")
                destinationDirectory = layout.buildDirectory.dir("out")
                archiveFileName = "test.jar"
                isZip64 = true
                isPreserveFileTimestamps = true
                isReproducibleFileOrder = false
                entryCompression = ZipEntryCompression.STORED
                manifestContentCharset = "ISO-8859-1"
                lazyProperties.metadataCharset = "ISO-8859-1"
                doLast {
                    println("jar: " + listOf(isZip64, isPreserveFileTimestamps, isReproducibleFileOrder, entryCompression, manifestContentCharset, metadataCharset))
                }
            }
        """

        when:
        succeeds("myJar")

        then:
        outputContains("jar: [true, true, false, STORED, ISO-8859-1, ISO-8859-1]")
        jarEntry("a.txt").method == ZipEntry.STORED
    }

    def "manifestContentCharset survives the configuration cache and is read when the task runs"() {
        def configurationCache = new ConfigurationCacheFixture(this)
        file("charset.txt").text = "ISO-8859-1"
        buildFile """
            tasks.named("myJar") {
                lazyProperties.manifestContentCharset = providers.fileContents(layout.projectDirectory.file("charset.txt")).asText.map { it.trim() }
                entryCompression = ZipEntryCompression.STORED
            }
        """

        when:
        succeeds("myJar", "--configuration-cache")

        then:
        configurationCache.assertStateStored()
        contains(manifestBytes(), "café".getBytes(StandardCharsets.ISO_8859_1))
        jarEntry("a.txt").method == ZipEntry.STORED

        when:
        file("charset.txt").text = "UTF-8"
        succeeds("myJar", "--configuration-cache")

        then:
        configurationCache.assertStateLoaded()
        executedAndNotSkipped(":myJar")
        contains(manifestBytes(), "café".getBytes(StandardCharsets.UTF_8))
        jarEntry("a.txt").method == ZipEntry.STORED

        when:
        succeeds("myJar", "--configuration-cache")

        then:
        skipped(":myJar")
    }

    def "a compiled Jar subclass that sets values in its constructor, overrides getters and reads them in its copy action keeps working"() {
        file("buildSrc/src/main/java/CustomJar.java") << """
            import org.gradle.api.internal.file.copy.CopyAction;
            import org.gradle.api.internal.file.copy.ZipCompressor;
            import org.gradle.api.tasks.Internal;
            import org.gradle.api.tasks.bundling.ZipEntryCompression;
            import org.gradle.jvm.tasks.Jar;

            import java.util.Arrays;

            public abstract class CustomJar extends Jar {
                private boolean customZip64;

                public CustomJar() {
                    setPreserveFileTimestamps(true);
                    setEntryCompression(ZipEntryCompression.STORED);
                    setManifestContentCharset("ISO-8859-1");
                    setMetadataCharset("UTF-8");
                    setZip64(true);
                }

                // A setter and getter with their own storage
                @Override
                public void setZip64(boolean zip64) {
                    customZip64 = zip64;
                }

                @Override
                public boolean isZip64() {
                    return customZip64;
                }

                // An override that only changes the annotation
                @Override
                @Internal
                public boolean isReproducibleFileOrder() {
                    return super.isReproducibleFileOrder();
                }

                @Override
                protected ZipCompressor getCompressor() {
                    System.out.println("compressor: " + getEntryCompression() + " " + isZip64());
                    return super.getCompressor();
                }

                @Override
                protected CopyAction createCopyAction() {
                    System.out.println("copy action: " + Arrays.asList(isPreserveFileTimestamps(), isReproducibleFileOrder(), getEntryCompression(), isZip64(), getMetadataCharset(), getManifestContentCharset()));
                    return super.createCopyAction();
                }
            }
        """
        buildFile """
            tasks.register("customJar", CustomJar) {
                from("src")
                destinationDirectory = layout.buildDirectory.dir("out")
                archiveFileName = "test.jar"
                manifest {
                    attributes("Name": "caf\\u00e9")
                }
                lazyProperties.zip64 = false
                doLast {
                    println("inputs: " + inputs.properties.keySet().findAll { !it.startsWith("rootSpec") }.sort())
                    println("zip64 input: " + inputs.properties.zip64)
                }
            }
        """

        when:
        succeeds("customJar")

        then:
        outputContains("copy action: [true, true, STORED, true, UTF-8, ISO-8859-1]")
        outputContains("compressor: STORED true")
        outputContains("inputs: [entryCompression, manifestContentCharset, metadataCharset, preserveFileTimestamps, reproducibleFileTimestamp, zip64]")
        outputContains("zip64 input: true")
        def entry = jarEntry("a.txt")
        entry.method == ZipEntry.STORED
        entry.time != ZipEntryConstants.CONSTANT_TIME_FOR_ZIP_ENTRIES
        contains(manifestBytes(), "café".getBytes(StandardCharsets.ISO_8859_1))

        when:
        succeeds("customJar")

        then:
        skipped(":customJar")
    }
}
