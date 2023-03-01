/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.internal.changedetection.state

import com.google.common.collect.ImmutableSet
import org.gradle.api.internal.file.archive.ZipEntry
import org.gradle.internal.file.FileMetadata
import org.gradle.internal.file.impl.DefaultFileMetadata
import org.gradle.internal.fingerprint.hashing.RegularFileSnapshotContext
import org.gradle.internal.fingerprint.hashing.ResourceHasher
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hasher
import org.gradle.internal.io.IoFunction
import org.gradle.internal.snapshot.RegularFileSnapshot
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.util.function.Supplier
import java.util.jar.Attributes
import java.util.jar.Manifest

class MetaInfAwareClasspathResourceHasherTest extends Specification {
    public static final String MANIFEST_PATH = 'META-INF/MANIFEST.MF'

    @TempDir File tmpDir

    ResourceEntryFilter manifestResourceFilter = new IgnoringResourceEntryFilter(ImmutableSet.copyOf("created-by"))

    def defaultDelegate = new RuntimeClasspathResourceHasher()
    def hasher = new MetaInfAwareClasspathResourceHasher(defaultDelegate, manifestResourceFilter)
    def unfilteredHasher = new MetaInfAwareClasspathResourceHasher(defaultDelegate, ResourceEntryFilter.FILTER_NOTHING)

    void useDelegate(ResourceHasher delegate) {
        hasher = new MetaInfAwareClasspathResourceHasher(delegate, manifestResourceFilter)
        unfilteredHasher = new MetaInfAwareClasspathResourceHasher(delegate, ResourceEntryFilter.FILTER_NOTHING)
    }

    def "uses delegate for META-INF files that are not manifest files"() {
        def delegate = Mock(ResourceHasher)
        useDelegate(delegate)

        when:
        hasher.hash(zipEntry('META-INF/foo'))
        hasher.hash(zipEntry('META-INF/foo/MANIFEST.MF'))
        hasher.hash(zipEntry('META-INF/properties'))
        hasher.hash(zipEntry('META-INF/build.propertiesX'))
        hasher.hash(zipEntry('bar.properties'))
        hasher.hash(zipEntry('resources/foo.properties'))
        hasher.hash(zipEntry('foo'))
        hasher.hash(zipEntry('org/gradle/foo.class'))
        hasher.hash(zipEntry('MANIFEST.MF'))

        then:
        9 * delegate.hash(_)
    }

    def "falls back to delegate when manifest hasher fails"() {
        def delegate = Mock(ResourceHasher)
        useDelegate(delegate)

        when:
        hasher.hash(zipEntry(MANIFEST_PATH, [:], new IOException()))

        then:
        1 * delegate.hash(_)
    }

    def "unexpected failures are thrown"() {
        def delegate = Mock(ResourceHasher)
        useDelegate(delegate)

        when:
        hasher.hash(zipEntry(MANIFEST_PATH, [:], new IllegalArgumentException()))

        then:
        0 * delegate.hash(_)

        and:
        thrown(IllegalArgumentException)
    }

    def "changing unfiltered manifest attributes changes the hashcode"() {
        given:
        def attributes1 = ["Implementation-Version": "1.0.0"]
        def attributes2 = ["Implementation-Version": "1.0.1"]

        when:
        def manifestEntry1 = zipEntry(MANIFEST_PATH, attributes1)
        def manifestEntry2 = zipEntry(MANIFEST_PATH, attributes2)

        def hash1 = unfilteredHasher.hash(manifestEntry1)
        def hash2 = unfilteredHasher.hash(manifestEntry2)
        def hash3 = hasher.hash(manifestEntry1)
        def hash4 = hasher.hash(manifestEntry2)

        then:
        hash1 != hash2
        hash3 != hash4

        and:
        hash1 == hash3
        hash2 == hash4

        when:
        manifestEntry1 = fileSnapshot(MANIFEST_PATH, attributes1)
        manifestEntry2 = fileSnapshot(MANIFEST_PATH, attributes2)

        hash1 = unfilteredHasher.hash(manifestEntry1)
        hash2 = unfilteredHasher.hash(manifestEntry2)
        hash3 = hasher.hash(manifestEntry1)
        hash4 = hasher.hash(manifestEntry2)

        then:
        hash1 != hash2
        hash3 != hash4

        and:
        hash1 == hash3
        hash2 == hash4
    }

    def "manifest attributes can be filtered out"() {
        given:
        def atributes1 = ["Created-By": "1.8.0_232-b18 (Azul Systems, Inc.)"]
        def attributes2 = ["Created-By": "1.8.0_232-b19 (Azul Systems, Inc.)"]

        when:
        def manifestEntry1 = zipEntry(MANIFEST_PATH, atributes1)
        def manifestEntry2 = zipEntry(MANIFEST_PATH, attributes2)

        def hash1 = unfilteredHasher.hash(manifestEntry1)
        def hash2 = unfilteredHasher.hash(manifestEntry2)
        def hash3 = hasher.hash(manifestEntry1)
        def hash4 = hasher.hash(manifestEntry2)

        then:
        hash1 != hash2
        hash1 != hash3
        hash2 != hash4

        and:
        hash3 == hash4

        when:
        manifestEntry1 = fileSnapshot(MANIFEST_PATH, atributes1)
        manifestEntry2 = fileSnapshot(MANIFEST_PATH, attributes2)

        hash1 = unfilteredHasher.hash(manifestEntry1)
        hash2 = unfilteredHasher.hash(manifestEntry2)
        hash3 = hasher.hash(manifestEntry1)
        hash4 = hasher.hash(manifestEntry2)

        then:
        hash1 != hash2
        hash1 != hash3
        hash2 != hash4

        and:
        hash3 == hash4
    }

    def "manifest attributes are case insensitive"() {
        given:
        def attributes1 = ["Created-By": "1.8.0_232-b18 (Azul Systems, Inc.)"]
        def attributes2 = ["created-by": "1.8.0_232-b18 (Azul Systems, Inc.)"]

        when:
        def manifestEntry1 = zipEntry(MANIFEST_PATH, attributes1)
        def manifestEntry2 = zipEntry(MANIFEST_PATH, attributes2)

        def hash1 = unfilteredHasher.hash(manifestEntry1)
        def hash2 = unfilteredHasher.hash(manifestEntry2)
        def hash3 = hasher.hash(manifestEntry1)
        def hash4 = hasher.hash(manifestEntry2)

        then:
        hash1 != hash3
        hash2 != hash4

        and:
        hash1 == hash2
        hash3 == hash4

        when:
        manifestEntry1 = fileSnapshot(MANIFEST_PATH, attributes1)
        manifestEntry2 = fileSnapshot(MANIFEST_PATH, attributes2)

        hash1 = unfilteredHasher.hash(manifestEntry1)
        hash2 = unfilteredHasher.hash(manifestEntry2)
        hash3 = hasher.hash(manifestEntry1)
        hash4 = hasher.hash(manifestEntry2)

        then:
        hash1 != hash3
        hash2 != hash4

        and:
        hash1 == hash2
        hash3 == hash4
    }

    def "manifest attributes are section order insensitive"() {
        given:
        def attributes1 = [
            "${Attributes.Name.MANIFEST_VERSION}": "1.0",
            "Created-By": "1.8.0_232-b18 (Azul Systems, Inc.)",
            "${Attributes.Name.IMPLEMENTATION_VERSION}": "1.0",

            "org/gradle/api": [
                "Sealed": "true"
            ],
            "org/gradle/base": [
                "Sealed": "true"
            ]
        ]
        def atributes2 = [
            "${Attributes.Name.MANIFEST_VERSION}": "1.0",
            "Created-By": "1.8.0_232-b18 (Azul Systems, Inc.)",
            "${Attributes.Name.IMPLEMENTATION_VERSION}": "1.0",

            "org/gradle/base": [
                "Sealed": "true"
            ],
            "org/gradle/api": [
                "Sealed": "true"
            ]
        ]

        when:
        def manifestEntry1 = zipEntry(MANIFEST_PATH, attributes1)
        def manifestEntry2 = zipEntry(MANIFEST_PATH, atributes2)

        def hash1 = unfilteredHasher.hash(manifestEntry1)
        def hash2 = unfilteredHasher.hash(manifestEntry2)
        def hash3 = hasher.hash(manifestEntry1)
        def hash4 = hasher.hash(manifestEntry2)

        then:
        hash1 != hash3
        hash2 != hash4

        and:
        hash1 == hash2
        hash3 == hash4

        when:
        manifestEntry1 = fileSnapshot(MANIFEST_PATH, attributes1)
        manifestEntry2 = fileSnapshot(MANIFEST_PATH, atributes2)

        hash1 = unfilteredHasher.hash(manifestEntry1)
        hash2 = unfilteredHasher.hash(manifestEntry2)
        hash3 = hasher.hash(manifestEntry1)
        hash4 = hasher.hash(manifestEntry2)

        then:
        hash1 != hash3
        hash2 != hash4

        and:
        hash1 == hash2
        hash3 == hash4
    }

    def "manifest attributes are filtered in sub-sections"() {
        given:
        def attributes1 = [
            "${Attributes.Name.MANIFEST_VERSION}": "1.0",
            "Created-By": "1.8.0_232-b18 (Azul Systems, Inc.)",
            "${Attributes.Name.IMPLEMENTATION_VERSION}": "1.0",

            "org/gradle/api": [
                "Created-By": "1.8.0_232-b18 (Azul Systems, Inc.)",
            ],
            "org/gradle/base": [
                "Created-By": "1.8.0_232-b18 (Azul Systems, Inc.)",
            ]
        ]
        def attributes2 = [
            "${Attributes.Name.MANIFEST_VERSION}": "1.0",
            "Created-By": "1.8.0_232-b19 (Azul Systems, Inc.)",
            "${Attributes.Name.IMPLEMENTATION_VERSION}": "1.0",

            "org/gradle/base": [
                "Created-By": "1.8.0_232-b19 (Azul Systems, Inc.)",
            ],
            "org/gradle/api": [
                "Created-By": "1.8.0_232-b19 (Azul Systems, Inc.)",
            ]
        ]

        when:
        def manifestEntry1 = zipEntry(MANIFEST_PATH, attributes1)
        def manifestEntry2 = zipEntry(MANIFEST_PATH, attributes2)

        def hash1 = unfilteredHasher.hash(manifestEntry1)
        def hash2 = unfilteredHasher.hash(manifestEntry2)
        def hash3 = hasher.hash(manifestEntry1)
        def hash4 = hasher.hash(manifestEntry2)

        then:
        hash1 != hash2
        hash1 != hash3
        hash2 != hash4

        and:
        hash3 == hash4

        when:
        manifestEntry1 = fileSnapshot(MANIFEST_PATH, attributes1)
        manifestEntry2 = fileSnapshot(MANIFEST_PATH, attributes2)

        hash1 = unfilteredHasher.hash(manifestEntry1)
        hash2 = unfilteredHasher.hash(manifestEntry2)
        hash3 = hasher.hash(manifestEntry1)
        hash4 = hasher.hash(manifestEntry2)

        then:
        hash1 != hash2
        hash1 != hash3
        hash2 != hash4

        and:
        hash3 == hash4
    }

    def "manifest attributes in sub-sections are ignored when all attributes are ignored"() {
        given:
        def attributes1 = [
            "${Attributes.Name.MANIFEST_VERSION}": "1.0",
            "Created-By": "1.8.0_232-b18 (Azul Systems, Inc.)",
            "${Attributes.Name.IMPLEMENTATION_VERSION}": "1.0",

            "org/gradle/api": [
                "Created-By": "1.8.0_232-b18 (Azul Systems, Inc.)",
            ],
            "org/gradle/base": [
                "Created-By": "1.8.0_232-b18 (Azul Systems, Inc.)",
            ]
        ]
        def attributes2 = [
            "${Attributes.Name.MANIFEST_VERSION}": "1.0",
            "Created-By": "1.8.0_232-b19 (Azul Systems, Inc.)",
            "${Attributes.Name.IMPLEMENTATION_VERSION}": "1.0",
        ]

        when:
        def manifestEntry1 = zipEntry(MANIFEST_PATH, attributes1)
        def manifestEntry2 = zipEntry(MANIFEST_PATH, attributes2)

        def hash1 = unfilteredHasher.hash(manifestEntry1)
        def hash2 = unfilteredHasher.hash(manifestEntry2)
        def hash3 = hasher.hash(manifestEntry1)
        def hash4 = hasher.hash(manifestEntry2)

        then:
        hash1 != hash2
        hash1 != hash3
        hash2 != hash4

        and:
        hash3 == hash4

        when:
        manifestEntry1 = fileSnapshot(MANIFEST_PATH, attributes1)
        manifestEntry2 = fileSnapshot(MANIFEST_PATH, attributes2)

        hash1 = unfilteredHasher.hash(manifestEntry1)
        hash2 = unfilteredHasher.hash(manifestEntry2)
        hash3 = hasher.hash(manifestEntry1)
        hash4 = hasher.hash(manifestEntry2)

        then:
        hash1 != hash2
        hash1 != hash3
        hash2 != hash4

        and:
        hash3 == hash4
    }

    def "delegate configuration is added to hasher"() {
        def configurationHasher = Mock(Hasher)
        def delegate = Mock(ResourceHasher)
        useDelegate(delegate)

        when:
        hasher.appendConfigurationToHasher(configurationHasher)

        then:
        1 * delegate.appendConfigurationToHasher(configurationHasher)
    }

    def "hashes original context with delegate for files that are not manifest files (filename: #filename)"() {
        def delegate = Mock(ResourceHasher)
        hasher = new MetaInfAwareClasspathResourceHasher(delegate, ResourceEntryFilter.FILTER_NOTHING)
        def notManifest = unsafeContextFor(filename)

        when:
        hasher.hash(notManifest)

        then:
        1 * delegate.hash(notManifest)

        where:
        filename << ["foo.txt", "some/path/to/MANIFEST.MF"]
    }

    def "always calls delegate for directories"() {
        def delegate = Mock(ResourceHasher)
        hasher = new MetaInfAwareClasspathResourceHasher(delegate, ResourceEntryFilter.FILTER_NOTHING)
        def directory = directoryContextFor(MANIFEST_PATH)

        when:
        hasher.hash(directory)

        then:
        1 * delegate.hash(directory)
    }

    void populateAttributes(Attributes attributes, Map<String, Object> attributesMap) {
        attributesMap.each { String name, Object value ->
            if (value instanceof String) {
                attributes.put(new Attributes.Name(name), value)
            }
        }
    }

    def unsafeContextFor(String path) {
        return zipEntry(path, [:], null, true)
    }

    def directoryContextFor(String path) {
        return zipEntry(path, [:], null, true, true)
    }

    def zipEntry(String path, Map<String, Object> attributesMap = [:], Exception exception = null, boolean unsafe = false, boolean directory = false) {
        ByteArrayOutputStream bos = getManifestByteStream(attributesMap)
        def zipEntry = new ZipEntry() {
            @Override
            boolean isDirectory() {
                return directory
            }

            @Override
            String getName() {
                return path
            }

            @Override
            byte[] getContent() throws IOException {
                return bos.toByteArray()
            }

            @Override
            <T> T withInputStream(IoFunction<InputStream, T> action) throws IOException {
                if (exception) {
                    throw exception
                }
                return action.apply(new ByteArrayInputStream(bos.toByteArray()))
            }

            @Override
            int size() {
                return bos.size()
            }

            @Override
            boolean canReopen() {
                return !unsafe
            }

            @Override
            ZipEntry.ZipCompressionMethod getCompressionMethod() {
                return ZipEntry.ZipCompressionMethod.DEFLATED
            }
        }
        return new DefaultZipEntryContext(zipEntry, path, "foo.zip")
    }

    def fileSnapshot(String path, Map<String, Object> attributesMap = [:], Exception exception = null) {
        ByteArrayOutputStream manifestBytes = getManifestByteStream(attributesMap)
        File root = Files.createTempDirectory(tmpDir.toPath(), null).toFile()
        File manifestFile = new File(root, MANIFEST_PATH)
        manifestFile.parentFile.mkdirs()
        manifestFile.write(manifestBytes.toString())
        return new RegularFileSnapshotContext() {
            @Override
            Supplier<String[]> getRelativePathSegments() {
                return { path.split('/') }
            }

            @Override
            RegularFileSnapshot getSnapshot() {
                return new RegularFileSnapshot(
                    manifestFile.absolutePath,
                    manifestFile.name,
                    HashCode.fromBytes(manifestBytes.toByteArray()),
                    DefaultFileMetadata.file(manifestFile.lastModified(), manifestFile.length(), FileMetadata.AccessType.DIRECT)
                )
            }
        }
    }

    private ByteArrayOutputStream getManifestByteStream(Map<String, Object> attributesMap) {
        def manifest = new Manifest()
        def mainAttributes = manifest.getMainAttributes()
        mainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0")
        populateAttributes(mainAttributes, attributesMap)
        attributesMap.each { name, value ->
            if (value instanceof Map) {
                def secondaryAttributes = new Attributes()
                populateAttributes(secondaryAttributes, value)
                manifest.entries.put(name, secondaryAttributes)
            }
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream()
        manifest.write(bos)
        bos
    }
}
