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
import spock.lang.Specification

class PropertiesFileZipEntryHasherTest extends Specification {
    ResourceEntryFilter propertyResourceFilter = new IgnoringResourceEntryFilter(ImmutableSet.copyOf("created-by", "पशुपतिरपि"))
    ZipEntryHasher filteredHasher = new PropertiesFileZipEntryHasher(propertyResourceFilter)
    ZipEntryHasher unfilteredHasher = new PropertiesFileZipEntryHasher(ResourceEntryFilter.FILTER_NOTHING)

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

        and:
        hash1 == hash3
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

    def "comments are always filtered out"() {
        def propertiesEntry1 = zipEntry(["foo": "true"], "Build information 1.0")
        def propertiesEntry2 = zipEntry(["foo": "true"], "Build information 1.1")

        def hash1 = unfilteredHasher.hash(propertiesEntry1)
        def hash2 = unfilteredHasher.hash(propertiesEntry2)
        def hash3 = filteredHasher.hash(propertiesEntry1)
        def hash4 = filteredHasher.hash(propertiesEntry2)

        expect:
        hash1 == hash2
        hash1 == hash3
        hash3 == hash4
    }

    ZipEntryContext zipEntry(Map<String, String> attributes, String comments = "") {
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
                return "META-INF/build-info.properties"
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
        return new ZipEntryContext(zipEntry, "META-INF/build-info.properties", "foo.zip")
    }
}
