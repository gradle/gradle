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

import com.google.common.collect.ImmutableList
import org.gradle.api.Transformer
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.artifacts.DefaultImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.model.NamedObjectInstantiator
import org.gradle.internal.component.external.descriptor.DefaultExclude
import org.gradle.internal.component.external.model.Capability
import org.gradle.internal.component.external.model.DefaultImmutableCapability
import org.gradle.internal.component.external.model.MutableComponentVariant
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata
import org.gradle.internal.component.model.Exclude
import org.gradle.internal.resource.local.LocallyAvailableExternalResource
import org.gradle.util.TestUtil
import spock.lang.Specification

class ModuleMetadataParserTest extends Specification {
    def identifierFactory = new DefaultImmutableModuleIdentifierFactory()
    def parser = new ModuleMetadataParser(TestUtil.attributesFactory(), identifierFactory, NamedObjectInstantiator.INSTANCE)

    VersionConstraint emptyConstraint() {
        DefaultImmutableVersionConstraint.of()
    }

    VersionConstraint prefers(String version) {
        DefaultImmutableVersionConstraint.of((String) version)
    }

    VersionConstraint prefersAndRejects(String version, List<String> rejects) {
        DefaultImmutableVersionConstraint.of(version, rejects)
    }

    List<Exclude> excludes(String... input) {
        return input.collect {
            String[] parts = it.split(":")
            new DefaultExclude(identifierFactory.module(parts[0], parts[1]))
        }
    }

    def "parses minimal metadata resource"() {
        def metadata = Mock(MutableModuleComponentResolveMetadata)

        when:
        parser.parse(resource('{ "formatVersion": "0.3" }'), metadata)

        then:
        0 * metadata._
    }

    def "parses minimal metadata with identity and producer information"() {
        def metadata = Mock(MutableModuleComponentResolveMetadata)

        when:
        parser.parse(resource('''
    { 
        "formatVersion": "0.3", 
        "component": { "url": "elsewhere", "group": "g", "module": "m", "version": "v" },
        "builtBy": { "gradle": { "version": "123", "buildId": "abc" } }
    }
'''), metadata)

        then:
        0 * _
    }

    def "parses component metadata attributes"() {
        def metadata = Mock(MutableModuleComponentResolveMetadata)

        when:
        parser.parse(resource('''
    { 
        "formatVersion": "0.3", 
        "component": { "url": "elsewhere", "group": "g", "module": "m", "version": "v", "attributes": {"foo": "bar", "org.gradle.status": "release" } },
        "builtBy": { "gradle": { "version": "123", "buildId": "abc" } }
    }
'''), metadata)

        then:
        1 * metadata.setAttributes(attributes(foo: 'bar', 'org.gradle.status': 'release'))
        0 * _
    }

    def "parses component metadata capabilities"() {
        def metadata = Mock(MutableModuleComponentResolveMetadata)
        def capabilities = ImmutableList.<Capability>of(
            new DefaultImmutableCapability("slf4j binding", ImmutableList.of("foo", "bar"), "foo"),
            new DefaultImmutableCapability("cap", ImmutableList.of("blah"), null)
        )

        when:
        parser.parse(resource('''
    { 
        "formatVersion": "0.3", 
        "component": { "url": "elsewhere", "group": "g", "module": "m", "version": "v", "capabilities": [
            {"name": "slf4j binding", "providedBy": ["foo", "bar"], "prefer": "foo" },
            {"name": "cap", "providedBy": ["blah"] }] },
        "builtBy": { "gradle": { "version": "123", "buildId": "abc" } }
    }
'''), metadata)

        then:
        1 * metadata.setCapabilities(capabilities)
        0 * _
    }

    def "parses content with variant"() {
        def metadata = Mock(MutableModuleComponentResolveMetadata)
        def variant = Mock(MutableComponentVariant)

        when:
        parser.parse(resource('''
    { 
        "formatVersion": "0.3", 
        "variants": [
            {
                "name": "api",
                "attributes": { "usage": "compile" },
                "files": [ { "name": "a.zip", "url": "a.zop" } ],
                "dependencies": [ { "group": "g1", "module": "m1", "version": { "prefers": "v1" } } ]
            }
        ]
    }
'''), metadata)

        then:
        1 * metadata.addVariant("api", attributes(usage: "compile")) >> variant
        1 * variant.addFile("a.zip", "a.zop")
        1 * variant.addDependency("g1", "m1", prefers("v1"), [], null)
        0 * _
    }

    def "parses content with variants"() {
        def metadata = Mock(MutableModuleComponentResolveMetadata)
        def variant1 = Mock(MutableComponentVariant)
        def variant2 = Mock(MutableComponentVariant)

        when:
        parser.parse(resource('''
    { 
        "formatVersion": "0.3", 
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
        def metadata = Mock(MutableModuleComponentResolveMetadata)
        def variant1 = Mock(MutableComponentVariant)
        def variant2 = Mock(MutableComponentVariant)

        when:
        parser.parse(resource('''
    { 
        "formatVersion": "0.3", 
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

    def "parses content with dependencies"() {
        def metadata = Mock(MutableModuleComponentResolveMetadata)
        def variant1 = Mock(MutableComponentVariant)
        def variant2 = Mock(MutableComponentVariant)

        when:
        parser.parse(resource('''
    { 
        "formatVersion": "0.3", 
        "variants": [
            {
                "name": "api",
                "dependencies": [ 
                    { "group": "g0", "module": "m0" },
                    { "group": "g1", "module": "m1", "version": { "prefers": "v1" } },
                    { "version": { "prefers": "v2" }, "group": "g2", "module": "m2" },
                    { 
                        "group": "g3", 
                        "module": "m3", 
                        "version": { "prefers": "v3" },
                        "excludes": [
                            {"group": "gx", "module": "mx" },
                            {"group": "*", "module": "*" }
                        ]
                    }
                ],
                "attributes": { "usage": "compile" }
            },
            {
                "attributes": { "usage": "runtime", "packaging": "zip" },
                "dependencies": [ 
                    { "module": "m3", "group": "g3", "version": { "prefers": "v3" }},
                    { "module": "m4", "version": { "prefers": "v4", "rejects": ["v5"] }, "group": "g4"},
                    { "module": "m5", "version": { "prefers": "v5", "rejects": ["v6", "v7"] }, "group": "g5"},
                    { "module": "m6", "group": "g6", "version": { "prefers": "v6" }, "reason": "v5 is buggy"}
                ],
                "name": "runtime"
            }
        ]
    }
'''), metadata)

        then:
        1 * metadata.addVariant("api", attributes(usage: "compile")) >> variant1
        1 * variant1.addDependency("g0", "m0", emptyConstraint(), [], null)
        1 * variant1.addDependency("g1", "m1", prefers("v1"), [], null)
        1 * variant1.addDependency("g2", "m2", prefers("v2"), [], null)
        1 * variant1.addDependency("g3", "m3", prefers("v3"), excludes("gx:mx", "*:*"), null)
        1 * metadata.addVariant("runtime", attributes(usage: "runtime", packaging: "zip")) >> variant2
        1 * variant2.addDependency("g3", "m3", prefers("v3"), [], null)
        1 * variant2.addDependency("g4", "m4", prefersAndRejects("v4", ["v5"]), [], null)
        1 * variant2.addDependency("g5", "m5", prefersAndRejects("v5", ["v6", "v7"]), [], null)
        1 * variant2.addDependency("g6", "m6", prefers("v6"), [], "v5 is buggy")
        0 * _
    }

    def "parses content with dependency constraints"() {
        def metadata = Mock(MutableModuleComponentResolveMetadata)
        def variant1 = Mock(MutableComponentVariant)
        def variant2 = Mock(MutableComponentVariant)

        when:
        parser.parse(resource('''
    { 
        "formatVersion": "0.3", 
        "variants": [
            {
                "name": "api",
                "dependencyConstraints": [ 
                    { "group": "g1", "module": "m1", "version": { "prefers": "v1" } },
                    { "version": { "prefers": "v2" }, "group": "g2", "module": "m2" },
                    { 
                        "group": "g3", 
                        "module": "m3", 
                        "version": { "prefers": "v3" }
                    }
                ],
                "attributes": { "usage": "compile" }
            },
            {
                "attributes": { "usage": "runtime", "packaging": "zip" },
                "dependencyConstraints": [ 
                    { "module": "m3", "group": "g3", "version": { "prefers": "v3" }},
                    { "module": "m4", "version": { "prefers": "v4", "rejects": ["v5"] }, "group": "g4"},
                    { "module": "m5", "version": { "prefers": "v5", "rejects": ["v6", "v7"] }, "group": "g5"},
                    { "module": "m6", "group": "g6", "version": { "prefers": "v6" }, "reason": "v5 is buggy"}
                ],
                "name": "runtime"
            }
        ]
    }
'''), metadata)

        then:
        1 * metadata.addVariant("api", attributes(usage: "compile")) >> variant1
        1 * variant1.addDependencyConstraint("g1", "m1", prefers("v1"), null)
        1 * variant1.addDependencyConstraint("g2", "m2", prefers("v2"), null)
        1 * variant1.addDependencyConstraint("g3", "m3", prefers("v3"), null)
        1 * metadata.addVariant("runtime", attributes(usage: "runtime", packaging: "zip")) >> variant2
        1 * variant2.addDependencyConstraint("g3", "m3", prefers("v3"), null)
        1 * variant2.addDependencyConstraint("g4", "m4", prefersAndRejects("v4", ["v5"]), null)
        1 * variant2.addDependencyConstraint("g5", "m5", prefersAndRejects("v5", ["v6", "v7"]), null)
        1 * variant2.addDependencyConstraint("g6", "m6", prefers("v6"), "v5 is buggy")
        0 * _
    }

    def "parses content with boolean attributes"() {
        def metadata = Mock(MutableModuleComponentResolveMetadata)
        def variant = Mock(MutableComponentVariant)

        when:
        parser.parse(resource('''
    { 
        "formatVersion": "0.3", 
        "builtBy": { "gradle": { "version": "123", "buildId": "abc" } },
        "variants": [
            {
                "name": "api",
                "attributes": { "usage": "compile", "debuggable": true, "testable": false }
            }
        ]
    }
'''), metadata)

        then:
        1 * metadata.addVariant("api", attributes(usage: "compile", debuggable: true, testable: false)) >> variant
        0 * _
    }

    def "parses minimal variant"() {
        def metadata = Mock(MutableModuleComponentResolveMetadata)
        def variant1 = Mock(MutableComponentVariant)
        def variant2 = Mock(MutableComponentVariant)

        when:
        parser.parse(resource('''
    { 
        "formatVersion": "0.3", 
        "variants": [
            {
                "name": "api"
            },
            {
                "attributes": { },
                "files": [],
                "name": "runtime",
                "dependencies": []
            }
        ]
    }
'''), metadata)

        then:
        1 * metadata.addVariant("api", attributes()) >> variant1
        1 * metadata.addVariant("runtime", attributes()) >> variant2
        0 * _
    }

    def "parses content with variants in another module"() {
        def metadata = Mock(MutableModuleComponentResolveMetadata)
        def variant1 = Mock(MutableComponentVariant)
        def variant2 = Mock(MutableComponentVariant)

        when:
        parser.parse(resource('''
    { 
        "formatVersion": "0.3", 
        "variants": [
            {
                "name": "api",
                "attributes": { "usage": "compile" },
                "available-at": {
                    "url": "../elsewhere",
                    "group": "g1",
                    "module": "m1",
                    "version": "v1"
                }
            },
            {
                "attributes": { "usage": "runtime", "packaging": "zip" },
                "name": "runtime",
                "available-at": {
                    "url": "../elsewhere",
                    "group": "g2",
                    "module": "m2",
                    "version": "v2"
                }
            }
        ]
    }
'''), metadata)

        then:
        1 * metadata.addVariant("api", attributes(usage: "compile")) >> variant1
        1 * variant1.addDependency("g1", "m1", prefers("v1"), [], null)
        1 * metadata.addVariant("runtime", attributes(usage: "runtime", packaging: "zip")) >> variant2
        1 * variant2.addDependency("g2", "m2", prefers("v2"), [], null)
        0 * _
    }

    def "fails on badly formed content"() {
        def metadata = Mock(MutableModuleComponentResolveMetadata)

        when:
        parser.parse(resource('not-json'), metadata)

        then:
        def e = thrown(MetaDataParseException)
        e.message == 'Could not parse module metadata <resource>'
    }

    def "ignores unknown top-level values"() {
        def metadata = Mock(MutableModuleComponentResolveMetadata)

        when:
        parser.parse(resource('''{ 
            "formatVersion": "0.3",
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
        def metadata = Mock(MutableModuleComponentResolveMetadata)

        when:
        parser.parse(resource('''
    { 
        "formatVersion": "0.3", 
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
        def metadata = Mock(MutableModuleComponentResolveMetadata)
        def variant = Mock(MutableComponentVariant)

        when:
        parser.parse(resource('''
    { 
        "formatVersion": "0.3", 
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

    def "ignores unknown dependency values"() {
        def metadata = Mock(MutableModuleComponentResolveMetadata)
        def variant = Mock(MutableComponentVariant)

        when:
        parser.parse(resource('''
    { 
        "formatVersion": "0.3", 
        "variants": [
            {
                "name": "api",
                "dependencies": [{
                    "group": "g",
                    "module": "m",
                    "version": { "prefers": "v" },
                    "excludes": [
                        { "group": "g", "otherString": "string", "otherNumber": 123, "otherObject": { "a": 1 } }
                    ],
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
        1 * variant.addDependency("g", "m", prefers("v"), excludes("g:*"), null)
        0 * metadata._
    }

    def "fails when content does not contain a json object"() {
        def metadata = Mock(MutableModuleComponentResolveMetadata)

        when:
        parser.parse(resource('["abc"]'), metadata)

        then:
        def e = thrown(MetaDataParseException)
        e.message == 'Could not parse module metadata <resource>'
        e.cause.message == 'Expected BEGIN_OBJECT but was BEGIN_ARRAY at line 1 column 2 path $'
    }

    def "fails on missing format version"() {
        def metadata = Mock(MutableModuleComponentResolveMetadata)

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
        def metadata = Mock(MutableModuleComponentResolveMetadata)

        when:
        parser.parse(resource('{ "formatVersion": 1.2 }'), metadata)

        then:
        def e = thrown(MetaDataParseException)
        e.message == 'Could not parse module metadata <resource>'
        e.cause.message == "The 'formatVersion' value should have a string value."
    }

    def "fails on unsupported format version"() {
        def metadata = Mock(MutableModuleComponentResolveMetadata)

        when:
        parser.parse(resource('{ "formatVersion": "123.4" }'), metadata)

        then:
        def e = thrown(MetaDataParseException)
        e.message == "Could not parse module metadata <resource>"
        e.cause.message == "Unsupported format version '123.4' specified in module metadata. This version of Gradle supports format version 0.3 only."
    }

    def attributes(Map<String, ?> values) {
        def attrs = ImmutableAttributes.EMPTY
        if (values) {
            values.each { String key, Object value ->
                attrs = TestUtil.attributesFactory().concat(attrs, Attribute.of(key, value.class), value)
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
