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
import com.google.common.collect.Maps
import org.gradle.api.internal.file.archive.ZipEntry
import org.gradle.internal.SystemProperties
import org.gradle.internal.file.FileMetadata
import org.gradle.internal.file.impl.DefaultFileMetadata
import org.gradle.internal.fingerprint.hashing.RegularFileSnapshotContext
import org.gradle.internal.fingerprint.hashing.ResourceHasher
import org.gradle.internal.fingerprint.hashing.ZipEntryContext
import org.gradle.internal.hash.Hasher
import org.gradle.internal.hash.Hashing
import org.gradle.internal.io.IoFunction
import org.gradle.internal.snapshot.RegularFileSnapshot
import org.gradle.internal.util.PropertiesUtils
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.charset.Charset
import java.nio.file.Files
import java.util.function.Supplier

class PropertiesFileAwareClasspathResourceHasherTest extends Specification {
    @TempDir File tmpdir
    Map<String, ResourceEntryFilter> filters = Maps.newHashMap()
    ResourceHasher delegate = new RuntimeClasspathResourceHasher()
    ResourceHasher unfilteredHasher = new PropertiesFileAwareClasspathResourceHasher(delegate, PropertiesFileFilter.FILTER_NOTHING)

    def getFilteredHasher() {
        return new PropertiesFileAwareClasspathResourceHasher(delegate, filters)
    }

    def setup() {
        filters = [
            '**/*.properties': filter("created-by", "पशुपतिरपि")
        ]
    }

    def "properties are case sensitive (context: #context)"() {
        given:
        def propertiesEntry1 = contextFor(context, ["Created-By": "1.8.0_232-b18 (Azul Systems, Inc.)"])
        def propertiesEntry2 = contextFor(context, ["created-by": "1.8.0_232-b18 (Azul Systems, Inc.)"])

        def hash1 = unfilteredHasher.hash(propertiesEntry1)
        def hash2 = unfilteredHasher.hash(propertiesEntry2)
        def hash3 = filteredHasher.hash(propertiesEntry1)
        def hash4 = filteredHasher.hash(propertiesEntry2)

        expect:
        hash1 != hash2
        hash2 != hash4
        hash3 != hash4
        hash1 != hash3

        where:
        context << SnapshotContext.values()
    }

    def "properties are normalized and filtered out (context: #context)"() {
        given:
        def propertiesEntry1 = contextFor(context, ["created-by": "1.8.0_232-b18 (Azul Systems, Inc.)", "foo": "true"])
        def propertiesEntry2 = contextFor(context, ["created-by": "1.8.0_232-b15 (Azul Systems, Inc.)", "foo": "true"])

        def hash1 = unfilteredHasher.hash(propertiesEntry1)
        def hash2 = unfilteredHasher.hash(propertiesEntry2)
        def hash3 = filteredHasher.hash(propertiesEntry1)
        def hash4 = filteredHasher.hash(propertiesEntry2)

        expect:
        hash1 != hash2
        hash1 != hash3
        hash2 != hash4

        and:
        hash3 == hash4

        where:
        context << SnapshotContext.values()
    }

    def "properties can have UTF-8 encoding (context: #context)"() {
        def propertiesEntry1 = contextFor(context, ["Created-By": "1.8.0_232-b18 (Azul Systems, Inc.)", "पशुपतिरपि": "some sanskrit", "तान्यहानि": "more sanskrit"])
        def propertiesEntry2 = contextFor(context, ["Created-By": "1.8.0_232-b18 (Azul Systems, Inc.)", "पशुपतिरपि": "changed sanskrit", "तान्यहानि": "more sanskrit"])

        def hash1 = unfilteredHasher.hash(propertiesEntry1)
        def hash2 = unfilteredHasher.hash(propertiesEntry2)
        def hash3 = filteredHasher.hash(propertiesEntry1)
        def hash4 = filteredHasher.hash(propertiesEntry2)

        expect:
        hash1 != hash2
        hash1 != hash3
        hash2 != hash4

        and:
        hash3 == hash4

        where:
        context << SnapshotContext.values()
    }

    def "properties are order insensitive (context: #context)"() {
        given:
        def propertiesEntry1 = contextFor(context, ["created-by": "1.8.0_232-b18 (Azul Systems, Inc.)", "foo": "true"])
        def propertiesEntry2 = contextFor(context, ["foo": "true", "created-by": "1.8.0_232-b15 (Azul Systems, Inc.)"])

        def hash1 = unfilteredHasher.hash(propertiesEntry1)
        def hash2 = unfilteredHasher.hash(propertiesEntry2)
        def hash3 = filteredHasher.hash(propertiesEntry1)
        def hash4 = filteredHasher.hash(propertiesEntry2)

        expect:
        hash1 != hash2
        hash1 != hash3
        hash2 != hash4

        and:
        hash3 == hash4

        where:
        context << SnapshotContext.values()
    }

    def "comments are always filtered out when filters are applied (context: #context)"() {
        def propertiesEntry1 = contextFor(context, ["foo": "true"], "Build information 1.0")
        def propertiesEntry2 = contextFor(context, ["foo": "true"], "Build information 1.1")

        def hash1 = unfilteredHasher.hash(propertiesEntry1)
        def hash2 = unfilteredHasher.hash(propertiesEntry2)
        def hash3 = filteredHasher.hash(propertiesEntry1)
        def hash4 = filteredHasher.hash(propertiesEntry2)

        expect:
        hash3 == hash4

        and:
        hash1 != hash2
        hash1 != hash3

        where:
        context << SnapshotContext.values()
    }

    def "can filter files selectively based on pattern (pattern: #fooPattern, context: #context)"() {
        given:
        filters = [
            '**/*.properties': ResourceEntryFilter.FILTER_NOTHING,
            (fooPattern): filter("created-by", "पशुपतिरपि")
        ]

        def propertiesEntry1 = contextFor(context, 'some/path/to/foo.properties', ["created-by": "1.8.0_232-b18 (Azul Systems, Inc.)"])
        def propertiesEntry2 = contextFor(context, 'some/path/to/bar.properties', ["created-by": "1.8.0_232-b18 (Azul Systems, Inc.)"])

        def hash1 = unfilteredHasher.hash(propertiesEntry1)
        def hash2 = unfilteredHasher.hash(propertiesEntry2)
        def hash3 = filteredHasher.hash(propertiesEntry1)
        def hash4 = filteredHasher.hash(propertiesEntry2)

        expect:
        hash2 != hash4
        hash3 != hash4
        hash1 != hash3

        and:
        hash1 == hash2

        where:
        [context, fooPattern] << [SnapshotContext.values(), ['**/foo.properties', '**/f*.properties', 'some/**/f*.properties', 'some/path/to/foo.properties']].combinations()*.flatten()
    }

    def "can filter multiple files selectively based on pattern (pattern: #fPattern, context: #context)"() {
        given:
        filters = [
            '**/*.properties': ResourceEntryFilter.FILTER_NOTHING,
            (fPattern.toString()): filter("created-by", "पशुपतिरपि")
        ]

        def propertiesEntry1 = contextFor(context, 'some/path/to/foo.properties', ["created-by": "1.8.0_232-b18 (Azul Systems, Inc.)"])
        def propertiesEntry2 = contextFor(context, 'some/path/to/bar.properties', ["created-by": "1.8.0_232-b18 (Azul Systems, Inc.)"])
        def propertiesEntry3 = contextFor(context, 'some/other/path/to/fuzz.properties', ["created-by": "1.8.0_232-b18 (Azul Systems, Inc.)"])

        def hash1 = unfilteredHasher.hash(propertiesEntry1)
        def hash2 = unfilteredHasher.hash(propertiesEntry2)
        def hash3 = unfilteredHasher.hash(propertiesEntry3)
        def hash4 = filteredHasher.hash(propertiesEntry1)
        def hash5 = filteredHasher.hash(propertiesEntry2)
        def hash6 = filteredHasher.hash(propertiesEntry3)

        expect:
        hash1 != hash4
        hash4 != hash5

        and:
        hash1 == hash2
        hash1 == hash3
        hash4 == hash6

        where:
        [context, fPattern] << [SnapshotContext.values(), ['**/f*.properties', 'some/**/f*.properties']].combinations()*.flatten()
    }

    def "multiple filters can be applied to the same file (context: #context)"() {
        given:
        filters = [
            '**/*.properties': filter("created-by"),
            '**/foo.properties': filter("पशुपतिरपि")
        ]

        def propertiesEntry1 = contextFor(context, 'some/path/to/foo.properties', ["created-by": "1.8.0_232-b18 (Azul Systems, Inc.)", "पशुपतिरपि": "some sanskrit"])
        def propertiesEntry2 = contextFor(context, 'some/path/to/bar.properties', ["created-by": "1.8.0_232-b18 (Azul Systems, Inc.)"])
        def propertiesEntry3 = contextFor(context, 'some/path/to/foo.properties', ["created-by": "1.8.0_232-b18 (Azul Systems, Inc.)"])

        def hash1 = unfilteredHasher.hash(propertiesEntry1)
        def hash2 = unfilteredHasher.hash(propertiesEntry2)
        def hash3 = unfilteredHasher.hash(propertiesEntry3)
        def hash4 = filteredHasher.hash(propertiesEntry1)
        def hash5 = filteredHasher.hash(propertiesEntry2)
        def hash6 = filteredHasher.hash(propertiesEntry3)

        expect:
        hash1 != hash2
        hash2 != hash4

        and:
        hash2 == hash3
        hash4 == hash5
        hash4 == hash6

        where:
        context << SnapshotContext.values()
    }

    def "delegates to file hasher when bad unicode escape sequences are present (error in: #location, context: #context)"() {
        given:
        filters = [ '**/*.properties': ResourceEntryFilter.FILTER_NOTHING ]
        def properties = contextFor(context, 'some/path/to/foo.properties', property.bytes)

        expect:
        filteredHasher.hash(properties) == delegate.hash(properties)

        where:
        context                       | location | property
        SnapshotContext.ZIP_ENTRY     | "value"  | 'someKey=a value with bad escape sequence \\uxxxx'
        SnapshotContext.ZIP_ENTRY     | "key"    | 'keyWithBadEscapeSequence\\uxxxx=some value'
        SnapshotContext.FILE_SNAPSHOT | "value"  | 'someKey=a value with bad escape sequence \\uxxxx'
        SnapshotContext.FILE_SNAPSHOT | "key"    | 'keyWithBadEscapeSequence\\uxxxx=some value'
    }

    def "delegate configuration is added to hasher"() {
        def configurationHasher = Mock(Hasher)
        delegate = Mock(ResourceHasher)

        when:
        filteredHasher.appendConfigurationToHasher(configurationHasher)

        then:
        1 * delegate.appendConfigurationToHasher(configurationHasher)
    }

    def "hashes original context with delegate for files that do not match a resource filter (filename: #filename)"() {
        delegate = Mock(ResourceHasher)
        def hasher = new PropertiesFileAwareClasspathResourceHasher(delegate, PropertiesFileFilter.FILTER_NOTHING)
        def notProperties = unsafeContextFor(filename, "foo".bytes)

        when:
        hasher.hash(notProperties)

        then:
        1 * delegate.hash(notProperties)

        where:
        filename << ['foo.txt', 'foo.properties']
    }

    def "always hashes directories with delegate"() {
        delegate = Mock(ResourceHasher)
        def directory = directoryContextfor("foo.properties")

        when:
        filteredHasher.hash(directory)

        then:
        1 * delegate.hash(directory)
    }

    enum SnapshotContext {
        ZIP_ENTRY, FILE_SNAPSHOT
    }

    def contextFor(SnapshotContext context, String path, Map<String, String> attributes, String comments = "") {
        switch(context) {
            case SnapshotContext.ZIP_ENTRY:
                return zipEntry(path, attributes, comments)
            case SnapshotContext.FILE_SNAPSHOT:
                return fileSnapshot(path, attributes, comments)
            default:
                throw new IllegalArgumentException()
        }
    }

    def contextFor(SnapshotContext context, String path, byte[] bytes) {
        switch(context) {
            case SnapshotContext.ZIP_ENTRY:
                return zipEntry(path, bytes)
            case SnapshotContext.FILE_SNAPSHOT:
                return fileSnapshot(path, bytes)
            default:
                throw new IllegalArgumentException()
        }
    }

    def contextFor(SnapshotContext context, Map<String, String> attributes, String comments = "") {
        contextFor(context, "META-INF/build-info.properties", attributes, comments)
    }

    static unsafeContextFor(String path, byte[] bytes) {
        return zipEntry(path, bytes, true)
    }

    static directoryContextfor(String path) {
        return zipEntry(path, [] as byte[], true, true)
    }

    static filter(String... properties) {
        return new IgnoringResourceEntryFilter(ImmutableSet.copyOf(properties))
    }

    static ZipEntryContext zipEntry(String path, Map<String, String> attributes, String comments = "") {
        Properties properties = new Properties()
        properties.putAll(attributes)
        ByteArrayOutputStream bos = new ByteArrayOutputStream()
        PropertiesUtils.store(properties, bos, comments, Charset.defaultCharset(), SystemProperties.getInstance().lineSeparator)
        return zipEntry(path, bos.toByteArray())
    }

    static ZipEntryContext zipEntry(String path, byte[] bytes, boolean unsafe = false, boolean directory = false) {
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
                return bytes
            }

            @Override
            <T> T withInputStream(IoFunction<InputStream, T> action) throws IOException {
                action.apply(new ByteArrayInputStream(bytes))
            }

            @Override
            int size() {
                return bytes.length
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

    RegularFileSnapshotContext fileSnapshot(String path, Map<String, String> attributes, String comments = "") {
        Properties properties = new Properties()
        properties.putAll(attributes)
        ByteArrayOutputStream bos = new ByteArrayOutputStream()
        PropertiesUtils.store(properties, bos, comments, Charset.defaultCharset(), SystemProperties.getInstance().lineSeparator)
        return fileSnapshot(path, bos.toByteArray())
    }

    RegularFileSnapshotContext fileSnapshot(String path, byte[] bytes) {
        def dir = Files.createTempDirectory(tmpdir.toPath(), null).toFile()
        def file = new File(dir, path)
        file.parentFile.mkdirs()
        file << bytes
        return new RegularFileSnapshotContext() {
            @Override
            Supplier<String[]> getRelativePathSegments() {
                return new Supplier<String[]>() {
                    @Override
                    String[] get() {
                        return path.split('/')
                    }
                }
            }

            @Override
            RegularFileSnapshot getSnapshot() {
                return new RegularFileSnapshot(file.absolutePath, file.name, Hashing.hashBytes(bytes), DefaultFileMetadata.file(0L, bytes.size(), FileMetadata.AccessType.DIRECT))
            }
        }
    }
}
