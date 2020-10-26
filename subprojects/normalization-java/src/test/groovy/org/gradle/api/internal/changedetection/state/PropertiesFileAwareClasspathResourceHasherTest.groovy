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
import spock.lang.Specification
import spock.lang.Unroll

class PropertiesFileAwareClasspathResourceHasherTest extends Specification {
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

    def "properties are case sensitive"() {
        given:
        def propertiesEntry1 = zipEntry(["Created-By": "1.8.0_232-b18 (Azul Systems, Inc.)"])
        def propertiesEntry2 = zipEntry(["created-by": "1.8.0_232-b18 (Azul Systems, Inc.)"])

        def hash1 = unfilteredHasher.hash(propertiesEntry1)
        def hash2 = unfilteredHasher.hash(propertiesEntry2)
        def hash3 = filteredHasher.hash(propertiesEntry1)
        def hash4 = filteredHasher.hash(propertiesEntry2)

        expect:
        hash1 != hash2
        hash2 != hash4
        hash3 != hash4
        hash1 != hash3
    }

    def "properties are normalized and filtered out"() {
        given:
        def propertiesEntry1 = zipEntry(["created-by": "1.8.0_232-b18 (Azul Systems, Inc.)", "foo": "true"])
        def propertiesEntry2 = zipEntry(["created-by": "1.8.0_232-b15 (Azul Systems, Inc.)", "foo": "true"])

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
    }

    def "properties can have UTF-8 encoding"() {
        def propertiesEntry1 = zipEntry(["Created-By": "1.8.0_232-b18 (Azul Systems, Inc.)", "पशुपतिरपि": "some sanskrit", "तान्यहानि": "more sanskrit"])
        def propertiesEntry2 = zipEntry(["Created-By": "1.8.0_232-b18 (Azul Systems, Inc.)", "पशुपतिरपि": "changed sanskrit", "तान्यहानि": "more sanskrit"])

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
    }

    def "properties are order insensitive"() {
        given:
        def propertiesEntry1 = zipEntry(["created-by": "1.8.0_232-b18 (Azul Systems, Inc.)", "foo": "true"])
        def propertiesEntry2 = zipEntry(["foo": "true", "created-by": "1.8.0_232-b15 (Azul Systems, Inc.)"])

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
    }

    def "comments are always filtered out when filters are applied"() {
        def propertiesEntry1 = zipEntry(["foo": "true"], "Build information 1.0")
        def propertiesEntry2 = zipEntry(["foo": "true"], "Build information 1.1")

        def hash1 = unfilteredHasher.hash(propertiesEntry1)
        def hash2 = unfilteredHasher.hash(propertiesEntry2)
        def hash3 = filteredHasher.hash(propertiesEntry1)
        def hash4 = filteredHasher.hash(propertiesEntry2)

        expect:
        hash3 == hash4

        and:
        hash1 != hash2
        hash1 != hash3
    }

    @Unroll
    def "can filter files selectively based on pattern (pattern: #fooPattern)"() {
        given:
        filters = [
            '**/*.properties': ResourceEntryFilter.FILTER_NOTHING,
            (fooPattern): filter("created-by", "पशुपतिरपि")
        ]

        def propertiesEntry1 = zipEntry('some/path/to/foo.properties', ["created-by": "1.8.0_232-b18 (Azul Systems, Inc.)"])
        def propertiesEntry2 = zipEntry('some/path/to/bar.properties', ["created-by": "1.8.0_232-b18 (Azul Systems, Inc.)"])

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
        fooPattern << ['**/foo.properties', '**/f*.properties', 'some/**/f*.properties', 'some/path/to/foo.properties']
    }

    @Unroll
    def "can filter multiple files selectively based on pattern (pattern: #fPattern)"() {
        given:
        filters = [
            '**/*.properties': ResourceEntryFilter.FILTER_NOTHING,
            (fPattern.toString()): filter("created-by", "पशुपतिरपि")
        ]

        def propertiesEntry1 = zipEntry('some/path/to/foo.properties', ["created-by": "1.8.0_232-b18 (Azul Systems, Inc.)"])
        def propertiesEntry2 = zipEntry('some/path/to/bar.properties', ["created-by": "1.8.0_232-b18 (Azul Systems, Inc.)"])
        def propertiesEntry3 = zipEntry('some/other/path/to/fuzz.properties', ["created-by": "1.8.0_232-b18 (Azul Systems, Inc.)"])

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
        fPattern << ['**/f*.properties', 'some/**/f*.properties']
    }

    def "multiple filters can be applied to the same file"() {
        given:
        filters = [
            '**/*.properties': filter("created-by"),
            '**/foo.properties': filter("पशुपतिरपि")
        ]

        def propertiesEntry1 = zipEntry('some/path/to/foo.properties', ["created-by": "1.8.0_232-b18 (Azul Systems, Inc.)", "पशुपतिरपि": "some sanskrit"])
        def propertiesEntry2 = zipEntry('some/path/to/bar.properties', ["created-by": "1.8.0_232-b18 (Azul Systems, Inc.)"])
        def propertiesEntry3 = zipEntry('some/path/to/foo.properties', ["created-by": "1.8.0_232-b18 (Azul Systems, Inc.)"])

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
    }

    static filter(String... properties) {
        return new IgnoringResourceEntryFilter(ImmutableSet.copyOf(properties))
    }

    ZipEntryContext zipEntry(Map<String, String> attributes, String comments = "") {
        zipEntry("META-INF/build-info.properties", attributes, comments)
    }

    ZipEntryContext zipEntry(String path, Map<String, String> attributes, String comments = "") {
        Properties properties = new Properties()
        properties.putAll(attributes)
        ByteArrayOutputStream bos = new ByteArrayOutputStream()
        properties.store(bos, comments)
        def zipEntry = new ZipEntry() {
            @Override
            boolean isDirectory() {
                return false
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
            InputStream getInputStream() {
                return new ByteArrayInputStream(bos.toByteArray())
            }

            @Override
            int size() {
                return bos.size()
            }
        }
        return new ZipEntryContext(zipEntry, path, "foo.zip")
    }
}
