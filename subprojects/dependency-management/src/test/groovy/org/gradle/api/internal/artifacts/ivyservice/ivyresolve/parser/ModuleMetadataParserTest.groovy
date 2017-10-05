/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser

import org.gradle.api.Transformer
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.model.NamedObjectInstantiator
import org.gradle.internal.component.external.model.MutableComponentVariant
import org.gradle.internal.component.external.model.MutableComponentVariantResolveMetadata
import org.gradle.internal.resource.local.LocallyAvailableExternalResource
import org.gradle.util.TestUtil
import spock.lang.Specification

class ModuleMetadataParserTest extends Specification {
    def parser = new ModuleMetadataParser(TestUtil.attributesFactory(), NamedObjectInstantiator.INSTANCE)

    def "parses minimal metadata resource"() {
        def metadata = Mock(MutableComponentVariantResolveMetadata)

        when:
        parser.parse(resource('{ "formatVersion": "0.1" }'), metadata)

        then:
        0 * metadata._
    }

    def "parses content with variant"() {
        def metadata = Mock(MutableComponentVariantResolveMetadata)
        def variant = Mock(MutableComponentVariant)

        when:
        parser.parse(resource('''
    { 
        "formatVersion": "0.1", 
        "builtBy": { "gradle": { "version": "123", "buildId": "abc" } },
        "variants": [
            {
                "name": "api",
                "attributes": { "usage": "compile" },
                "files": [ { "name": "a.zip", "url": "a.zop" } ]
            }
        ]
    }
'''), metadata)

        then:
        1 * metadata.addVariant("api", attributes(usage: "compile")) >> variant
        1 * variant.addFile("a.zip", "a.zop")
        0 * _
    }

    def "parses content with variants"() {
        def metadata = Mock(MutableComponentVariantResolveMetadata)
        def variant1 = Mock(MutableComponentVariant)
        def variant2 = Mock(MutableComponentVariant)

        when:
        parser.parse(resource('''
    { 
        "formatVersion": "0.1", 
        "variants": [
            {
                "name": "api",
                "attributes": { "usage": "compile" }
            },
            {
                "attributes": { "usage": "runtime", "packaging": "zip" },
                "name": "runtime"
            }
        ]
    }
'''), metadata)

        then:
        1 * metadata.addVariant("api", attributes(usage: "compile")) >> variant1
        1 * metadata.addVariant("runtime", attributes(usage: "runtime", packaging: "zip")) >> variant2
        0 * _
    }

    def "parses content with files"() {
        def metadata = Mock(MutableComponentVariantResolveMetadata)
        def variant1 = Mock(MutableComponentVariant)
        def variant2 = Mock(MutableComponentVariant)

        when:
        parser.parse(resource('''
    { 
        "formatVersion": "0.1", 
        "variants": [
            {
                "name": "api",
                "files": [ 
                    { "name": "api.zip", "url": "api.zop" },
                    { "name": "api-2.zip", "url": "api-2.zop" }
                ],
                "attributes": { "usage": "compile" }
            },
            {
                "attributes": { "usage": "runtime", "packaging": "zip" },
                "files": [ 
                    { "name": "api.zip", "url": "api.zop" },
                    { "name": "runtime.zip", "url": "runtime.zop" }
                ],
                "name": "runtime"
            }
        ]
    }
'''), metadata)

        then:
        1 * metadata.addVariant("api", attributes(usage: "compile")) >> variant1
        1 * variant1.addFile("api.zip", "api.zop")
        1 * variant1.addFile("api-2.zip", "api-2.zop")
        1 * metadata.addVariant("runtime", attributes(usage: "runtime", packaging: "zip")) >> variant2
        1 * variant2.addFile("api.zip", "api.zop")
        1 * variant2.addFile("runtime.zip", "runtime.zop")
        0 * _
    }

    def "parses minimal variant"() {
        def metadata = Mock(MutableComponentVariantResolveMetadata)
        def variant1 = Mock(MutableComponentVariant)
        def variant2 = Mock(MutableComponentVariant)

        when:
        parser.parse(resource('''
    { 
        "formatVersion": "0.1", 
        "variants": [
            {
                "name": "api"
            },
            {
                "attributes": { },
                "files": [],
                "name": "runtime"
            }
        ]
    }
'''), metadata)

        then:
        1 * metadata.addVariant("api", attributes()) >> variant1
        1 * metadata.addVariant("runtime", attributes()) >> variant2
        0 * _
    }

    def "fails on badly formed content"() {
        def metadata = Mock(MutableComponentVariantResolveMetadata)

        when:
        parser.parse(resource('not-json'), metadata)

        then:
        def e = thrown(MetaDataParseException)
        e.message == 'Could not parse module metadata <resource>'
    }

    def "ignores unknown top-level values"() {
        def metadata = Mock(MutableComponentVariantResolveMetadata)

        when:
        parser.parse(resource('''{ 
            "formatVersion": "0.1",
            "otherString": "string",
            "otherNumber": 123,
            "otherBoolean": true,
            "otherNull": null,
            "otherObject": { "a": 1, "b": "ignore-me", "c": [], "d": { } },
            "otherArray": [ "a", 123, false, [], null, { } ]
        }'''), metadata)

        then:
        0 * metadata._
    }

    def "ignores unknown variant values"() {
        def metadata = Mock(MutableComponentVariantResolveMetadata)

        when:
        parser.parse(resource('''
    { 
        "formatVersion": "0.1", 
        "variants": [
            {
                "name": "api",
                "otherString": "string",
                "otherNumber": 123,
                "otherBoolean": true,
                "otherNull": null,
                "otherObject": { "a": 1, "b": "ignore-me", "c": [], "d": { } },
                "otherArray": [ "a", 123, false, [], null, { } ]
            }
        ]
    }
'''), metadata)

        then:
        1 * metadata.addVariant("api", attributes())
        0 * metadata._
    }

    def "ignores unknown file values"() {
        def metadata = Mock(MutableComponentVariantResolveMetadata)
        def variant = Mock(MutableComponentVariant)

        when:
        parser.parse(resource('''
    { 
        "formatVersion": "0.1", 
        "variants": [
            {
                "name": "api",
                "files": [{
                    "name": "file",
                    "url": "file",
                    "otherString": "string",
                    "otherNumber": 123,
                    "otherBoolean": true,
                    "otherNull": null,
                    "otherObject": { "a": 1, "b": "ignore-me", "c": [], "d": { } },
                    "otherArray": [ "a", 123, false, [], null, { } ]
                }]
            }
        ]
    }
'''), metadata)

        then:
        1 * metadata.addVariant("api", attributes()) >> variant
        0 * metadata._
    }

    def "fails when content does not contain a json object"() {
        def metadata = Mock(MutableComponentVariantResolveMetadata)

        when:
        parser.parse(resource('["abc"]'), metadata)

        then:
        def e = thrown(MetaDataParseException)
        e.message == 'Could not parse module metadata <resource>'
        e.cause.message == 'Expected BEGIN_OBJECT but was BEGIN_ARRAY at line 1 column 2 path $'
    }

    def "fails on missing format version"() {
        def metadata = Mock(MutableComponentVariantResolveMetadata)

        when:
        parser.parse(resource('{ }'), metadata)

        then:
        def e = thrown(MetaDataParseException)
        e.message == 'Could not parse module metadata <resource>'
        e.cause.message == "Module metadata should contain a 'formatVersion' value."

        when:
        parser.parse(resource('{ "other": 1.2 }'), metadata)

        then:
        def e2 = thrown(MetaDataParseException)
        e2.message == 'Could not parse module metadata <resource>'
        e2.cause.message == "The 'formatVersion' value should be the first value in a module metadata. Found 'other' instead."
    }

    def "fails when format version does not have string value"() {
        def metadata = Mock(MutableComponentVariantResolveMetadata)

        when:
        parser.parse(resource('{ "formatVersion": 1.2 }'), metadata)

        then:
        def e = thrown(MetaDataParseException)
        e.message == 'Could not parse module metadata <resource>'
        e.cause.message == "The 'formatVersion' value should have a string value."
    }

    def "fails on unsupported format version"() {
        def metadata = Mock(MutableComponentVariantResolveMetadata)

        when:
        parser.parse(resource('{ "formatVersion": "123.4" }'), metadata)

        then:
        def e = thrown(MetaDataParseException)
        e.message == "Could not parse module metadata <resource>"
        e.cause.message == "Unsupported format version '123.4' specified in module metadata. This version of Gradle supports only format version 0.1."
    }

    def attributes(Map<String, String> values) {
        def attrs = ImmutableAttributes.EMPTY
        if (values) {
            values.each { String key, String value ->
                attrs = TestUtil.attributesFactory().concat(attrs, Attribute.of(key, String), value)
            }
        }
        return attrs
    }

    def resource(String content) {
        def resource = Stub(LocallyAvailableExternalResource)
        _ * resource.displayName >> "<resource>"
        _ * resource.withContent(_) >> { Transformer transformer -> return transformer.transform(new ByteArrayInputStream(content.getBytes("utf-8"))) }
        resource
    }
}
