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

import java.util.jar.Attributes
import java.util.jar.Manifest


class ManifestFileZipEntryHasherTest extends Specification {
    ResourceEntryFilter manifestResourceFilter = new IgnoringResourceEntryFilter(ImmutableSet.copyOf("created-by"))
    def filteredHasher = new ManifestFileZipEntryHasher(manifestResourceFilter)
    def unfilteredHasher = new ManifestFileZipEntryHasher(ResourceEntryFilter.FILTER_NOTHING)

    def "changing unfiltered manifest attributes changes the hashcode"() {
        given:
        def manifestEntry1 = zipEntry(["Implementation-Version": "1.0.0"])
        def manifestEntry2 = zipEntry(["Implementation-Version": "1.0.1"])

        def hash1 = unfilteredHasher.hash(manifestEntry1)
        def hash2 = unfilteredHasher.hash(manifestEntry2)
        def hash3 = filteredHasher.hash(manifestEntry1)
        def hash4 = filteredHasher.hash(manifestEntry2)

        expect:
        hash1 != hash2
        hash3 != hash4

        and:
        hash1 == hash3
        hash2 == hash4
    }

    def "manifest attributes can be filtered out"() {
        given:
        def manifestEntry1 = zipEntry(["Created-By": "1.8.0_232-b18 (Azul Systems, Inc.)"])
        def manifestEntry2 = zipEntry(["Created-By": "1.8.0_232-b19 (Azul Systems, Inc.)"])

        def hash1 = unfilteredHasher.hash(manifestEntry1)
        def hash2 = unfilteredHasher.hash(manifestEntry2)
        def hash3 = filteredHasher.hash(manifestEntry1)
        def hash4 = filteredHasher.hash(manifestEntry2)

        expect:
        hash1 != hash2
        hash1 != hash3
        hash2 != hash4

        and:
        hash3 == hash4
    }

    def "manifest attributes are case insensitive"() {
        given:
        def manifestEntry1 = zipEntry(["Created-By": "1.8.0_232-b18 (Azul Systems, Inc.)"])
        def manifestEntry2 = zipEntry(["created-by": "1.8.0_232-b18 (Azul Systems, Inc.)"])

        def hash1 = unfilteredHasher.hash(manifestEntry1)
        def hash2 = unfilteredHasher.hash(manifestEntry2)
        def hash3 = filteredHasher.hash(manifestEntry1)
        def hash4 = filteredHasher.hash(manifestEntry2)

        expect:
        hash1 != hash3
        hash2 != hash4

        and:
        hash1 == hash2
        hash3 == hash4
    }

    def "manifest attributes are section order insensitive"() {
        given:
        def manifestEntry1 = zipEntry([
            "${Attributes.Name.MANIFEST_VERSION}": "1.0",
            "Created-By": "1.8.0_232-b18 (Azul Systems, Inc.)",
            "${Attributes.Name.IMPLEMENTATION_VERSION}": "1.0",

            "org/gradle/api": [
                "Sealed": "true"
            ],
            "org/gradle/base": [
                "Sealed": "true"
            ]
        ])
        def manifestEntry2 = zipEntry([
            "${Attributes.Name.MANIFEST_VERSION}": "1.0",
            "Created-By": "1.8.0_232-b18 (Azul Systems, Inc.)",
            "${Attributes.Name.IMPLEMENTATION_VERSION}": "1.0",

            "org/gradle/base": [
                "Sealed": "true"
            ],
            "org/gradle/api": [
                "Sealed": "true"
            ]
        ])

        def hash1 = unfilteredHasher.hash(manifestEntry1)
        def hash2 = unfilteredHasher.hash(manifestEntry2)
        def hash3 = filteredHasher.hash(manifestEntry1)
        def hash4 = filteredHasher.hash(manifestEntry2)

        expect:
        hash1 != hash3
        hash2 != hash4

        and:
        hash1 == hash2
        hash3 == hash4
    }

    def "manifest attributes are filtered in sub-sections"() {
        given:
        def manifestEntry1 = zipEntry([
            "${Attributes.Name.MANIFEST_VERSION}": "1.0",
            "Created-By": "1.8.0_232-b18 (Azul Systems, Inc.)",
            "${Attributes.Name.IMPLEMENTATION_VERSION}": "1.0",

            "org/gradle/api": [
                "Created-By": "1.8.0_232-b18 (Azul Systems, Inc.)",
            ],
            "org/gradle/base": [
                "Created-By": "1.8.0_232-b18 (Azul Systems, Inc.)",
            ]
        ])
        def manifestEntry2 = zipEntry([
            "${Attributes.Name.MANIFEST_VERSION}": "1.0",
            "Created-By": "1.8.0_232-b19 (Azul Systems, Inc.)",
            "${Attributes.Name.IMPLEMENTATION_VERSION}": "1.0",

            "org/gradle/base": [
                "Created-By": "1.8.0_232-b19 (Azul Systems, Inc.)",
            ],
            "org/gradle/api": [
                "Created-By": "1.8.0_232-b19 (Azul Systems, Inc.)",
            ]
        ])

        def hash1 = unfilteredHasher.hash(manifestEntry1)
        def hash2 = unfilteredHasher.hash(manifestEntry2)
        def hash3 = filteredHasher.hash(manifestEntry1)
        def hash4 = filteredHasher.hash(manifestEntry2)

        expect:
        hash1 != hash2
        hash1 != hash3
        hash2 != hash4

        and:
        hash3 == hash4
    }

    def "manifest attributes in sub-sections are ignored when all attributes are ignored"() {
        given:
        def manifestEntry1 = zipEntry([
            "${Attributes.Name.MANIFEST_VERSION}": "1.0",
            "Created-By": "1.8.0_232-b18 (Azul Systems, Inc.)",
            "${Attributes.Name.IMPLEMENTATION_VERSION}": "1.0",

            "org/gradle/api": [
                "Created-By": "1.8.0_232-b18 (Azul Systems, Inc.)",
            ],
            "org/gradle/base": [
                "Created-By": "1.8.0_232-b18 (Azul Systems, Inc.)",
            ]
        ])
        def manifestEntry2 = zipEntry([
            "${Attributes.Name.MANIFEST_VERSION}": "1.0",
            "Created-By": "1.8.0_232-b19 (Azul Systems, Inc.)",
            "${Attributes.Name.IMPLEMENTATION_VERSION}": "1.0",
        ])

        def hash1 = unfilteredHasher.hash(manifestEntry1)
        def hash2 = unfilteredHasher.hash(manifestEntry2)
        def hash3 = filteredHasher.hash(manifestEntry1)
        def hash4 = filteredHasher.hash(manifestEntry2)

        expect:
        hash1 != hash2
        hash1 != hash3
        hash2 != hash4

        and:
        hash3 == hash4
    }

    void populateAttributes(Attributes attributes, Map<String, Object> attributesMap) {
        attributesMap.each { String name, Object value ->
            if (value instanceof String) {
                attributes.put(new Attributes.Name(name), value)
            }
        }
    }

    def zipEntry(Map<String, Object> attributesMap) {
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
        def zipEntry = new ZipEntry() {
            @Override
            boolean isDirectory() {
                return false
            }

            @Override
            String getName() {
                return "META-INF/MANIFEST.MF"
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
        return new ZipEntryContext(zipEntry, "META-INF/MANIFEST.MF", "foo.zip")
    }
}
