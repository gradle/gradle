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

import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.internal.artifacts.DefaultImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.component.external.descriptor.DefaultExclude
import org.gradle.internal.component.external.model.MutableComponentVariant
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.Exclude
import org.gradle.internal.resource.ExternalResource
import org.gradle.internal.resource.local.LocallyAvailableExternalResource
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

import java.util.stream.Collectors

import static org.gradle.util.AttributeTestUtil.attributes

class GradleModuleMetadataParserTest extends Specification {
    private static final String UNKNOWN_FILE_VALUES = '''
    {
        "formatVersion": "1.1",
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
'''
    private static final String UNKNOWN_DEPENDENCY_VALUES = '''
    {
        "formatVersion": "1.1",
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
'''
    private static final String UNKNOWN_VARIANT_VALUES = '''
    {
        "formatVersion": "1.1",
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
'''
    private static final String UNKNOWN_TOP_LEVEL = '''{
            "formatVersion": "1.1",
            "otherString": "string",
            "otherNumber": 123,
            "otherBoolean": true,
            "otherNull": null,
            "otherObject": { "a": 1, "b": "ignore-me", "c": [], "d": { } },
            "otherArray": [ "a", 123, false, [], null, { } ]
        }'''

    private static final Map<String, String> UNKOWN_DATASET = [
        UNKNOWN_TOP_LEVEL: UNKNOWN_TOP_LEVEL,
        UNKNOWN_DEPENDENCY_VALUES: UNKNOWN_DEPENDENCY_VALUES,
        UNKNOWN_VARIANT_VALUES: UNKNOWN_VARIANT_VALUES,
        UNKNOWN_FILE_VALUES: UNKNOWN_FILE_VALUES
    ]

    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def identifierFactory = new DefaultImmutableModuleIdentifierFactory()
    def parser = new GradleModuleMetadataParser(AttributeTestUtil.attributesFactory(), identifierFactory, TestUtil.objectInstantiator())

    VersionConstraint emptyConstraint() {
        DefaultImmutableVersionConstraint.of()
    }

    VersionConstraint version(String version) {
        DefaultImmutableVersionConstraint.of(version)
    }

    VersionConstraint requires(String version) {
        DefaultImmutableVersionConstraint.of(version)
    }

    VersionConstraint prefers(String version) {
        DefaultImmutableVersionConstraint.of(version, '', '', [])
    }

    VersionConstraint strictly(String version) {
        DefaultImmutableVersionConstraint.of('', '', version, [])
    }

    VersionConstraint prefersAndStrictly(String prefers, String strictly) {
        DefaultImmutableVersionConstraint.of(prefers, '', strictly, [])
    }

    VersionConstraint prefersAndRejects(String version, List<String> rejects) {
        DefaultImmutableVersionConstraint.of(version, version, "", rejects)
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
        parser.parse(resource('{ "formatVersion": "1.1" }'), metadata)

        then:
        1 * metadata.getMutableVariants()
        0 * metadata._
    }

    def "parses minimal metadata with identity and producer information"() {
        def metadata = Mock(MutableModuleComponentResolveMetadata)

        when:
        parser.parse(resource('''
    {
        "formatVersion": "1.1",
        "component": { "url": "elsewhere", "group": "g", "module": "m", "version": "v" },
        "builtBy": { "gradle": { "version": "123", "buildId": "abc" } }
    }
'''), metadata)

        then:
        1 * metadata.getMutableVariants()
        0 * _
    }

    def "parses component metadata attributes"() {
        def metadata = Mock(MutableModuleComponentResolveMetadata)

        when:
        parser.parse(resource('''
    {
        "formatVersion": "1.1",
        "component": { "url": "elsewhere", "group": "g", "module": "m", "version": "v", "attributes": {"foo": "bar", "org.gradle.status": "release" } },
        "builtBy": { "gradle": { "version": "123", "buildId": "abc" } }
    }
'''), metadata)

        then:
        1 * metadata.setAttributes(attributes(foo: 'bar', 'org.gradle.status': 'release'))
        1 * metadata.getMutableVariants()
        0 * _
    }

    def "parses content with variant"() {
        def metadata = Mock(MutableModuleComponentResolveMetadata)
        def variant = Mock(MutableComponentVariant)

        when:
        parser.parse(resource('''
    {
        "formatVersion": "1.1",
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
        1 * variant.addDependency("g1", "m1", prefers("v1"), [], null, ImmutableAttributes.EMPTY, [], false, null)
        1 * metadata.getMutableVariants()
        1 * variant.setAvailableExternally(false)
        0 * _
    }

    def "parses content with variants"() {
        def metadata = Mock(MutableModuleComponentResolveMetadata)
        def variant1 = Mock(MutableComponentVariant)
        def variant2 = Mock(MutableComponentVariant)

        when:
        parser.parse(resource('''
    {
        "formatVersion": "1.1",
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
        1 * variant1.setAvailableExternally(false)
        1 * variant2.setAvailableExternally(false)
        1 * metadata.getMutableVariants()
        0 * _
    }

    def "parses content with files"() {
        def metadata = Mock(MutableModuleComponentResolveMetadata)
        def variant1 = Mock(MutableComponentVariant)
        def variant2 = Mock(MutableComponentVariant)

        when:
        parser.parse(resource('''
    {
        "formatVersion": "1.1",
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
        1 * variant1.setAvailableExternally(false)
        1 * metadata.addVariant("runtime", attributes(usage: "runtime", packaging: "zip")) >> variant2
        1 * variant2.addFile("api.zip", "api.zop")
        1 * variant2.addFile("runtime.zip", "runtime.zop")
        1 * variant2.setAvailableExternally(false)
        1 * metadata.getMutableVariants()
        0 * _
    }

    def "parses content with dependencies"() {
        def metadata = Mock(MutableModuleComponentResolveMetadata)
        def variant1 = Mock(MutableComponentVariant)
        def variant2 = Mock(MutableComponentVariant)

        when:
        parser.parse(resource('''
    {
        "formatVersion": "1.1",
        "variants": [
            {
                "name": "api",
                "dependencies": [
                    { "group": "g0", "module": "m0" },
                    { "group": "g1", "module": "m1", "version": { "requires": "v1" } },
                    { "version": { "prefers": "v2" }, "group": "g2", "module": "m2" },
                    {
                        "group": "g3",
                        "module": "m3",
                        "version": { "requires": "v3"},
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
                    { "module": "m3", "group": "g3", "version": { "prefers": "v3" }, "requestedCapabilities":[{"group":"org", "name":"foo", "version":"1.0"}]},
                    { "module": "m4", "endorseStrictVersions": true, "version": { "strictly": "v5" }, "group": "g4"},
                    { "module": "m5", "version": { "prefers": "v5", "requires": "v5", "rejects": ["v6", "v7"] }, "group": "g5"},
                    { "module": "m6", "group": "g6", "version": { "strictly": "v6" }, "reason": "v5 is buggy"}
                ],
                "name": "runtime"
            }
        ]
    }
'''), metadata)

        then:
        1 * metadata.addVariant("api", attributes(usage: "compile")) >> variant1
        1 * variant1.addDependency("g0", "m0", emptyConstraint(), [], null, ImmutableAttributes.EMPTY, [], false, null)
        1 * variant1.addDependency("g1", "m1", requires("v1"), [], null, ImmutableAttributes.EMPTY, [], false, null)
        1 * variant1.addDependency("g2", "m2", prefers("v2"), [], null, ImmutableAttributes.EMPTY, [], false, null)
        1 * variant1.addDependency("g3", "m3", requires("v3"), excludes("gx:mx", "*:*"), null, ImmutableAttributes.EMPTY, [], false, null)
        1 * variant1.setAvailableExternally(false)
        1 * metadata.addVariant("runtime", attributes(usage: "runtime", packaging: "zip")) >> variant2
        1 * variant2.addDependency("g3", "m3", prefers("v3"), [], null, ImmutableAttributes.EMPTY, { it[0].group == 'org' && it[0].name == 'foo' && it[0].version == '1.0' }, false, null)
        1 * variant2.addDependency("g4", "m4", strictly("v5"), [], null, ImmutableAttributes.EMPTY, [], true, null)
        1 * variant2.addDependency("g5", "m5", prefersAndRejects("v5", ["v6", "v7"]), [], null, ImmutableAttributes.EMPTY, [], false, null)
        1 * variant2.addDependency("g6", "m6", strictly("v6"), [], "v5 is buggy", ImmutableAttributes.EMPTY, [], false, null)
        1 * variant2.setAvailableExternally(false)
        1 * metadata.getMutableVariants()
        0 * _
    }

    def "parses content with dependency constraints"() {
        def metadata = Mock(MutableModuleComponentResolveMetadata)
        def variant1 = Mock(MutableComponentVariant)
        def variant2 = Mock(MutableComponentVariant)

        when:
        parser.parse(resource('''
    {
        "formatVersion": "1.1",
        "variants": [
            {
                "name": "api",
                "dependencyConstraints": [
                    { "group": "g1", "module": "m1", "version": { "requires": "v1" } },
                    { "version": { "prefers": "v2" }, "group": "g2", "module": "m2" },
                    {
                        "group": "g3",
                        "module": "m3",
                        "version": { "requires": "v3" }
                    },
                    { "group": "g4", "module": "m4" }
                ],
                "attributes": { "usage": "compile" }
            },
            {
                "attributes": { "usage": "runtime", "packaging": "zip" },
                "dependencyConstraints": [
                    { "module": "m3", "group": "g3", "version": { "prefers": "v3" }},
                    { "module": "m4", "version": { "prefers": "v4", "requires": "v4", "rejects": ["v5"] }, "group": "g4"},
                    { "module": "m5", "version": { "requires": "v5", "prefers": "v5", "rejects": ["v6", "v7"] }, "group": "g5"},
                    { "module": "m6", "group": "g6", "version": { "prefers": "v6" }, "reason": "v5 is buggy"}
                ],
                "name": "runtime"
            }
        ]
    }
'''), metadata)

        then:
        1 * metadata.addVariant("api", attributes(usage: "compile")) >> variant1
        1 * variant1.addDependencyConstraint("g1", "m1", requires("v1"), null, ImmutableAttributes.EMPTY)
        1 * variant1.addDependencyConstraint("g2", "m2", prefers("v2"), null, ImmutableAttributes.EMPTY)
        1 * variant1.addDependencyConstraint("g3", "m3", requires("v3"), null, ImmutableAttributes.EMPTY)
        1 * variant1.addDependencyConstraint("g4", "m4", emptyConstraint(), null, ImmutableAttributes.EMPTY)
        1 * variant1.setAvailableExternally(false)
        1 * metadata.addVariant("runtime", attributes(usage: "runtime", packaging: "zip")) >> variant2
        1 * variant2.addDependencyConstraint("g3", "m3", prefers("v3"), null, ImmutableAttributes.EMPTY)
        1 * variant2.addDependencyConstraint("g4", "m4", prefersAndRejects("v4", ["v5"]), null, ImmutableAttributes.EMPTY)
        1 * variant2.addDependencyConstraint("g5", "m5", prefersAndRejects("v5", ["v6", "v7"]), null, ImmutableAttributes.EMPTY)
        1 * variant2.addDependencyConstraint("g6", "m6", prefers("v6"), "v5 is buggy", ImmutableAttributes.EMPTY)
        1 * variant2.setAvailableExternally(false)
        1 * metadata.getMutableVariants()
        0 * _
    }

    def "parses content with dependency attributes"() {
        def metadata = Mock(MutableModuleComponentResolveMetadata)
        def variant1 = Mock(MutableComponentVariant)
        def variant2 = Mock(MutableComponentVariant)

        when:
        parser.parse(resource('''
    {
        "formatVersion": "1.1",
        "variants": [
            {
                "name": "api",
                "dependencies": [
                    { "group": "g1", "module": "m1", "version": { "requires": "v1" }, "attributes": {"custom": "foo"} },
                    { "version": { "prefers": "v2" }, "group": "g2", "module": "m2", "attributes": {"custom": "foo", "other": "bar"} }
                ],
                "attributes": { "usage": "compile" }
            },
            {
                "attributes": { "usage": "runtime", "packaging": "zip" },
                "dependencyConstraints": [
                    { "endorseStrictVersions": true, "group": "g1", "module": "m1", "version": { "prefers": "v1" }, "attributes": {"custom": "foo"} },
                    { "version": { "requires": "v2" }, "group": "g2", "module": "m2", "attributes": {"custom": "foo", "other": "bar"} }
                ],
                "name": "runtime"
            }
        ]
    }
'''), metadata)

        then:
        1 * metadata.addVariant("api", attributes(usage: "compile")) >> variant1
        1 * variant1.addDependency("g1", "m1", requires("v1"), [], null, attributes(custom: 'foo'), [], false, null)
        1 * variant1.addDependency("g2", "m2", prefers("v2"), [], null, attributes(custom: 'foo', other: 'bar'), [], false, null)
        1 * variant1.setAvailableExternally(false)
        1 * metadata.addVariant("runtime", attributes(usage: "runtime", packaging: "zip")) >> variant2
        1 * variant2.addDependencyConstraint("g1", "m1", prefers("v1"), null, attributes(custom: 'foo'))
        1 * variant2.addDependencyConstraint("g2", "m2", requires("v2"), null, attributes(custom: 'foo', other: 'bar'))
        1 * variant2.setAvailableExternally(false)
        1 * metadata.getMutableVariants()
        0 * _
    }

    def "parses content with capabilities"() {
        def metadata = Mock(MutableModuleComponentResolveMetadata)
        def variant1 = Mock(MutableComponentVariant)
        def variant2 = Mock(MutableComponentVariant)

        when:
        parser.parse(resource('''
    {
        "formatVersion": "1.1",
        "variants": [
            {
                "name": "api",
                "capabilities": [
                    { "group": "g1", "name": "m1", "version": "1" },
                    { "group": "g2", "name": "m2", "version": "2" }
                ],
                "attributes": { "usage": "compile" }
            },
            {
                "attributes": { "usage": "runtime", "packaging": "zip" },
                "capabilities": [
                    { "group": "g3", "name": "m3", "version": "3" },
                    { "group": "g4", "name": "m4", "version": "4" }
                ],
                "name": "runtime"
            }
        ]
    }
'''), metadata)

        then:
        1 * metadata.addVariant("api", attributes(usage: "compile")) >> variant1
        1 * variant1.addCapability("g1", "m1", "1")
        1 * variant1.addCapability("g2", "m2", "2")
        1 * variant1.setAvailableExternally(false)
        1 * metadata.addVariant("runtime", attributes(usage: "runtime", packaging: "zip")) >> variant2
        1 * variant2.addCapability("g3", "m3", "3")
        1 * variant2.addCapability("g4", "m4", "4")
        1 * variant2.setAvailableExternally(false)
        1 * metadata.getMutableVariants()
        0 * _
    }

    def "parses requested capabilities without version"() {
        def metadata = Mock(MutableModuleComponentResolveMetadata)
        def variant1 = Mock(MutableComponentVariant)
        def variant2 = Mock(MutableComponentVariant)

        when:
        parser.parse(resource('''
    {
        "formatVersion": "1.1",
        "variants": [
            {
                "attributes": { "usage": "runtime"},
                "dependencies": [
                    { "module": "m3", "group": "g3", "requestedCapabilities":[{"group":"org", "name":"foo"}]}
                ],
                "name": "runtime"
            },
            {
                "attributes": { "usage": "api"},
                "dependencies": [
                    { "module": "m3", "group": "g3", "requestedCapabilities":[{"group":"org", "name":"foo", "version": null}]}
                ],
                "name": "api"
            }
        ]
    }
'''), metadata)

        then:
        1 * metadata.addVariant("runtime", attributes(usage: "runtime")) >> variant1
        1 * variant1.addDependency("g3", "m3", emptyConstraint(), [], null, ImmutableAttributes.EMPTY, { it[0].group == 'org' && it[0].name == 'foo' && it[0].version == null }, false, null)
        1 * variant1.setAvailableExternally(false)
        1 * metadata.addVariant("api", attributes(usage: "api")) >> variant2
        1 * variant2.addDependency("g3", "m3", emptyConstraint(), [], null, ImmutableAttributes.EMPTY, { it[0].group == 'org' && it[0].name == 'foo' && it[0].version == null }, false, null)
        1 * variant2.setAvailableExternally(false)
        1 * metadata.getMutableVariants()
        0 * _
    }

    def "parses content with boolean attributes"() {
        def metadata = Mock(MutableModuleComponentResolveMetadata)
        def variant = Mock(MutableComponentVariant)

        when:
        parser.parse(resource('''
    {
        "formatVersion": "1.1",
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
        1 * variant.setAvailableExternally(false)
        1 * metadata.getMutableVariants()
        0 * _
    }

    def "parses minimal variant"() {
        def metadata = Mock(MutableModuleComponentResolveMetadata)
        def variant1 = Mock(MutableComponentVariant)
        def variant2 = Mock(MutableComponentVariant)

        when:
        parser.parse(resource('''
    {
        "formatVersion": "1.1",
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
        1 * metadata.addVariant("api", attributes([:])) >> variant1
        1 * metadata.addVariant("runtime", attributes([:])) >> variant2
        1 * variant1.setAvailableExternally(false)
        1 * variant2.setAvailableExternally(false)
        1 * metadata.getMutableVariants()
        0 * _
    }

    def "parses content with variants in another module"() {
        def metadata = Mock(MutableModuleComponentResolveMetadata)
        def variant1 = Mock(MutableComponentVariant)
        def variant2 = Mock(MutableComponentVariant)

        when:
        parser.parse(resource('''
    {
        "formatVersion": "1.1",
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
        1 * variant1.addDependency("g1", "m1", version("v1"), [], null, ImmutableAttributes.EMPTY, [], false, null)
        1 * variant1.setAvailableExternally(true)
        1 * metadata.addVariant("runtime", attributes(usage: "runtime", packaging: "zip")) >> variant2
        1 * variant2.addDependency("g2", "m2", version("v2"), [], null, ImmutableAttributes.EMPTY, [], false, null)
        1 * variant2.setAvailableExternally(true)
        1 * metadata.getMutableVariants()
        0 * _
    }

    def "parses legacy artifact selector"() {
        def metadata = Mock(MutableModuleComponentResolveMetadata)
        def variant1 = Mock(MutableComponentVariant)

        when:
        parser.parse(resource('''
    {
        "formatVersion": "1.1",
        "variants": [
            {
                "name": "api",
                "dependencies": [
                    {
                        "group": "g1",
                        "module": "m1",
                        "version": {
                            "requires": "v1"
                        },
                        "thirdPartyCompatibility": {
                            "artifactSelector": {
                                "name": "foo",
                                "type": "bar",
                                "extension": "baz",
                                "classifier": "claz"
                            }
                        }
                    }
                ]
            }
        ]
    }
'''), metadata)

        then:
        1 * metadata.addVariant("api", attributes([:])) >> variant1
        1 * variant1.addDependency("g1", "m1", version("v1"), [], null, ImmutableAttributes.EMPTY, [], false, new DefaultIvyArtifactName("foo", "bar", "baz", "claz"))
        1 * variant1.setAvailableExternally(false)
        1 * metadata.getMutableVariants()
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
        parser.parse(resource(UNKNOWN_TOP_LEVEL), metadata)

        then:
        1 * metadata.getMutableVariants()
        0 * metadata._
    }

    def "ignores unknown variant values"() {
        def metadata = Mock(MutableModuleComponentResolveMetadata)

        when:
        parser.parse(resource(UNKNOWN_VARIANT_VALUES), metadata)

        then:
        1 * metadata.addVariant("api", attributes([:])) >> Stub(MutableComponentVariant)
        1 * metadata.getMutableVariants()
        0 * metadata._
    }

    def "ignores unknown file values"() {
        def metadata = Mock(MutableModuleComponentResolveMetadata)
        def variant = Mock(MutableComponentVariant)

        when:
        parser.parse(resource(UNKNOWN_FILE_VALUES), metadata)

        then:
        1 * metadata.addVariant("api", attributes([:])) >> variant
        1 * metadata.getMutableVariants()
        0 * metadata._
    }

    def "ignores unknown dependency values"() {
        def metadata = Mock(MutableModuleComponentResolveMetadata)
        def variant = Mock(MutableComponentVariant)

        when:
        parser.parse(resource(UNKNOWN_DEPENDENCY_VALUES), metadata)

        then:
        1 * metadata.addVariant("api", attributes([:])) >> variant
        1 * variant.addDependency("g", "m", prefers("v"), excludes("g:*"), null, ImmutableAttributes.EMPTY, [], false, null)
        1 * metadata.getMutableVariants()
        0 * metadata._
    }

    def "fails for missing #label"() {
        def metadata = Mock(MutableModuleComponentResolveMetadata)

        when:
        parser.parse(resource("""
        {
            "formatVersion": "1.1",
            "variants": [
                {
                    $variantDefinition
                }
            ]
        }"""), metadata)

        then:
        0 * _
        def e = thrown(MetaDataParseException)
        e.cause.message == "missing '$attribute' at $path"

        where:
        label                          | path                                                                    | attribute | variantDefinition
        'variant name'                 | '/variants[0]'                                                          | 'name'    | ''

        'available-at url'             | '/variants[0]/available-at'                                             | 'url'     | '"name": "v", "available-at": { "group": "g", "module": "c", "version": "1.0" }'
        'available-at group'           | '/variants[0]/available-at'                                             | 'group'   | '"name": "v", "available-at": { "url": "path", "module": "c", "version": "1.0" }'
        'available-at module'          | '/variants[0]/available-at'                                             | 'module'  | '"name": "v", "available-at": { "url": "path", "group": "g", "version": "1.0" }'
        'available-at version'         | '/variants[0]/available-at'                                             | 'version' | '"name": "v", "available-at": { "url": "path", "group": "g", "module": "c" }'

        'dependency group'             | '/variants[0]/dependencies[0]'                                          | 'group'   | '"name": "v", "dependencies": [{ "module": "c" }]'
        'dependency module'            | '/variants[0]/dependencies[0]'                                          | 'module'  | '"name": "v", "dependencies": [{ "group": "g" }]'

        'capability group'             | '/variants[0]/capabilities[0]'                                          | 'group'   | '"name": "v", "capabilities": [{ "name": "c", "version": "1.0" }]'
        'capability name'              | '/variants[0]/capabilities[0]'                                          | 'name'    | '"name": "v", "capabilities": [{ "group": "g", "version": "1.0" }]'
        'capability version'           | '/variants[0]/capabilities[0]'                                          | 'version' | '"name": "v", "capabilities": [{ "group": "g", "name": "c" }]'

        'req capability group'         | '/variants[0]/dependencies[0]/requestedCapabilities[0]'                 | 'group'   | '"name": "v", "dependencies": [{ "group": "g", "name": "c", "requestedCapabilities": [{ "name": "c", "version": "1.0" }] }]'
        'req capability name'          | '/variants[0]/dependencies[0]/requestedCapabilities[0]'                 | 'name'    | '"name": "v", "dependencies": [{ "group": "g", "name": "c", "requestedCapabilities": [{ "group": "g", "version": "1.0" }] }]'

        'dependency constraint group'  | '/variants[0]/dependencyConstraints[0]'                                 | 'group'   | '"name": "v", "dependencyConstraints": [{ "module": "c" }]'
        'dependency constraint module' | '/variants[0]/dependencyConstraints[0]'                                 | 'module'  | '"name": "v", "dependencyConstraints": [{ "group": "g" }]'

        'file name'                    | '/variants[0]/files[0]'                                                 | 'name'    | '"name": "v", "files": [{ "url": "g/c/c.jar" }]'
        'file url'                     | '/variants[0]/files[0]'                                                 | 'url'     | '"name": "v", "files": [{ "name": "c.jar" }]'

        'artifact name'                | '/variants[0]/dependencies[0]/thirdPartyCompatibility/artifactSelector' | 'name'    | '"name": "v", "dependencies": [{ "group": "g", "module": "c", "thirdPartyCompatibility": { "artifactSelector": { "type": "bar" } }}]'
        'artifact type'                | '/variants[0]/dependencies[0]/thirdPartyCompatibility/artifactSelector' | 'type'    | '"name": "v", "dependencies": [{ "group": "g", "module": "c", "thirdPartyCompatibility": { "artifactSelector": { "name": "foo" } }}]'
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

    def "fails on unsupported format version if json parsing fails and metadata format is not the expected one"() {
        def metadata = Mock(MutableModuleComponentResolveMetadata)

        when:
        parser.parse(resource('{ "formatVersion": "123.4", "variants": {} }'), metadata)

        then:
        def e = thrown(MetaDataParseException)
        e.message == "Could not parse module metadata <resource>: unsupported format version '123.4' specified in module metadata. This version of Gradle supports format version 1.1."
        e.cause.message == "Expected BEGIN_ARRAY but was BEGIN_OBJECT at line 1 column 42 path \$.variants"
    }

    def "is lenient with version checks if we manage to parse content (#label, version = #version)"() {
        def metadata = Mock(MutableModuleComponentResolveMetadata) {
            addVariant(_, _) >> Stub(MutableComponentVariant)
        }

        when:
        parser.parse(resource(replaceMetadataVersion(json, version)), metadata)

        then:
        1 * metadata.getMutableVariants()

        where:
        [json, version] << [UNKOWN_DATASET.values(), ['0.4', '1.1', '1.5', '123.4']].combinations()
        label = UNKOWN_DATASET.entrySet().find { it.value == json }.key
    }

    String replaceMetadataVersion(String json, String metadataVersion) {
        json.replace('"formatVersion": "1.1"', '"formatVersion": "' + metadataVersion + '"')
    }

    def "new hierarchy in ModuleDependency is added to equals and hashcode"() {
        when:
        // If this test fails, you added a type hierarchy to GradleModuleMetadataParser.ModuleDependency, update this test _after_ making sure it is considered by hashcode and equals
        def modDepClass = GradleModuleMetadataParser.ModuleDependency.class

        then:
        modDepClass.getSuperclass() == Object.class
    }

    def "new hierarchy in ModuleDependencyConstraint is added to equals and hashcode"() {
        when:
        // If this test fails, you added a type hierarchy to GradleModuleMetadataParser.ModuleDependency, update this test _after_ making sure it is considered by hashcode and equals
        def modDepClass = GradleModuleMetadataParser.ModuleDependencyConstraint.class

        then:
        modDepClass.getSuperclass() == Object.class
    }

    def "new fields in ModuleDependency are added to equals and hashcode"() {
        when:
        // If this test fails, you added a field to GradleModuleMetadataParser.ModuleDependency, add it here _after_ making sure it is considered by hashcode and equals
        def knownFields = ["group", "module", "versionConstraint", "excludes", "reason", "attributes", "requestedCapabilities", "endorsing", "artifact"]
        def modDepClass = GradleModuleMetadataParser.ModuleDependency.class
        def newFields = Arrays.stream(modDepClass.getDeclaredFields()).filter { !knownFields.contains(it.getName()) }
            .map { it.getName() }
            .collect(Collectors.toList())

        then:
        newFields == []
    }

    def "new fields in ModuleDependencyConstraint are added to equals and hashcode"() {
        when:
        // If this test fails, you added a field to GradleModuleMetadataParser.ModuleDependencyConstraint, add it here _after_ making sure it is considered by hashcode and equals
        def knownFields = ["group", "module", "versionConstraint", "reason", "attributes"]
        def modDepClass = GradleModuleMetadataParser.ModuleDependencyConstraint.class
        def newFields = Arrays.stream(modDepClass.getDeclaredFields()).filter { !knownFields.contains(it.getName()) }
            .map { it.getName() }
            .collect(Collectors.toList())

        then:
        newFields == []
    }

    def resource(String content) {
        def resource = Stub(LocallyAvailableExternalResource)
        _ * resource.displayName >> "<resource>"
        _ * resource.withContent(_) >> { ExternalResource.ContentAction action -> return action.execute(new ByteArrayInputStream(content.getBytes("utf-8"))) }
        _ * resource.getFile() >> {
            def file = temporaryFolder.createFile("module${UUID.randomUUID().toString()}.module")
            file.write(content, "UTF-8")
            file
        }
        resource
    }
}
