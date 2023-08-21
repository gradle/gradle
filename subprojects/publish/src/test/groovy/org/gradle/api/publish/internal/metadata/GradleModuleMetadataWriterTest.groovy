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

package org.gradle.api.publish.internal.metadata

import org.gradle.api.InvalidUserCodeException
import org.gradle.api.Named
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.attributes.Attribute
import org.gradle.api.capabilities.Capability
import org.gradle.api.component.ComponentWithVariants
import org.gradle.api.internal.artifacts.DefaultExcludeRule
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultDependencyArtifact
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradleModuleMetadataParser
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyPublicationResolver
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.component.UsageContext
import org.gradle.api.internal.provider.Providers
import org.gradle.api.publish.internal.PublicationInternal
import org.gradle.api.publish.internal.versionmapping.VariantVersionMappingStrategyInternal
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal
import org.gradle.internal.component.external.model.DefaultImmutableCapability
import org.gradle.internal.id.UniqueId
import org.gradle.internal.scopeids.id.BuildInvocationScopeId
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.GradleVersion
import org.gradle.util.Path
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Specification

import static org.gradle.util.AttributeTestUtil.attributes
import static org.gradle.util.AttributeTestUtil.attributesTyped

class GradleModuleMetadataWriterTest extends Specification {

    static VersionConstraint requires(String version) {
        DefaultImmutableVersionConstraint.of(DefaultMutableVersionConstraint.withVersion(version))
    }

    static VersionConstraint strictly(String version) {
        DefaultImmutableVersionConstraint.of(DefaultMutableVersionConstraint.withStrictVersion(version))
    }

    static VersionConstraint prefersAndRejects(String version, List<String> rejects) {
        DefaultImmutableVersionConstraint.of(version, "", "", rejects)
    }

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def buildId = UniqueId.generate()
    def id = DefaultModuleVersionIdentifier.newId("group", "module", "1.2")
    def projectDependencyResolver = Mock(ProjectDependencyPublicationResolver)

    private writeTo(Writer writer, PublicationInternal publication, List<PublicationInternal> publications) {
        new GradleModuleMetadataWriter(
            new BuildInvocationScopeId(buildId), projectDependencyResolver, TestUtil.checksumService, ':task', []
        ).writeTo(
            writer, publication, publications
        )
    }

    def "fails to write file for component with no variants"() {
        def writer = new StringWriter()
        def component = Stub(TestComponent)
        def publication = publication(component, id)

        when:
        writeTo(writer, publication, [publication])

        then:
        InvalidUserCodeException ex = thrown()
        ex.message.contains("This publication must publish at least one variant")
    }

    def "writes file for component with attributes (with build id: #withBuildId)"() {
        def writer = new StringWriter()
        def component = Stub(TestComponent)
        def publication = publication(component, id, null, withBuildId)
        def v1 = Stub(UsageContext)
        v1.name >> "v1"
        v1.attributes >> attributes(usage: "compile")
        v1.dependencies >> []

        component.usages >> [v1]

        when:
        publication.attributes >> attributes(status: 'release', 'test': 'value')
        writeTo(writer, publication, [publication])
        def buildIdContent = ""
        if (withBuildId) {
            buildIdContent = """,
      "buildId": "${buildId}\""""
        }

        then:
        writer.toString() == """{
  "formatVersion": "${GradleModuleMetadataParser.FORMAT_VERSION}",
  "component": {
    "group": "group",
    "module": "module",
    "version": "1.2",
    "attributes": {
      "status": "release",
      "test": "value"
    }
  },
  "createdBy": {
    "gradle": {
      "version": "${GradleVersion.current().version}"$buildIdContent
    }
  },
  "variants": [
    {
      "name": "v1",
      "attributes": {
        "usage": "compile"
      }
    }
  ]
}
"""
        where:
        withBuildId << [true, false]
    }

    def "writes file for component with variants with files"() {
        def writer = new StringWriter()
        def component = Stub(TestComponent)
        def publication = publication(component, id)

        def file1 = tmpDir.file("artifact-1")
        file1.text = "123"
        def file2 = tmpDir.file("thing.dll")
        file2.text = "abcd"

        def a1 = Stub(PublishArtifact)
        a1.file >> file1
        a1.extension >> "zip"
        a1.classifier >> ""
        publication.getPublishedFile(a1) >> new SimplePublishedFile("a1.name", "a1.url")

        def a2 = Stub(PublishArtifact)
        a2.file >> file2
        a2.extension >> "dll"
        a2.classifier >> "windows"
        publication.getPublishedFile(a2) >> new SimplePublishedFile("a2.name", "a2.url")

        def v1 = Stub(UsageContext)
        v1.name >> "v1"
        v1.attributes >> attributes(usage: "compile")
        v1.artifacts >> [a1]
        def v2 = Stub(UsageContext)
        v2.name >> "v2"
        v2.attributes >> attributes(usage: "runtime")
        v2.artifacts >> [a2]

        component.usages >> [v1, v2]

        when:
        writeTo(writer, publication, [publication])

        then:
        writer.toString() == """{
  "formatVersion": "${GradleModuleMetadataParser.FORMAT_VERSION}",
  "component": {
    "group": "group",
    "module": "module",
    "version": "1.2",
    "attributes": {}
  },
  "createdBy": {
    "gradle": {
      "version": "${GradleVersion.current().version}"
    }
  },
  "variants": [
    {
      "name": "v1",
      "attributes": {
        "usage": "compile"
      },
      "files": [
        {
          "name": "a1.name",
          "url": "a1.url",
          "size": 3,
          "sha512": "3c9909afec25354d551dae21590bb26e38d53f2173b8d3dc3eee4c047e7ab1c1eb8b85103e3be7ba613b31bb5c9c36214dc9f14a42fd7a2fdb84856bca5c44c2",
          "sha256": "a665a45920422f9d417e4867efdc4fb8a04a1f3fff1fa07e998e86f7f7a27ae3",
          "sha1": "40bd001563085fc35165329ea1ff5c5ecbdbbeef",
          "md5": "202cb962ac59075b964b07152d234b70"
        }
      ]
    },
    {
      "name": "v2",
      "attributes": {
        "usage": "runtime"
      },
      "files": [
        {
          "name": "a2.name",
          "url": "a2.url",
          "size": 4,
          "sha512": "d8022f2060ad6efd297ab73dcc5355c9b214054b0d1776a136a669d26a7d3b14f73aa0d0ebff19ee333368f0164b6419a96da49e3e481753e7e96b716bdccb6f",
          "sha256": "88d4266fd4e6338d13b845fcf289579d209c897823b9217da3e161936f031589",
          "sha1": "81fe8bfe87576c3ecb22426f8e57847382917acf",
          "md5": "e2fc714c4727ee9395f324cd2e7f331f"
        }
      ]
    }
  ]
}
"""
    }

    def "writes file for component with variants with dependencies"() {
        def writer = new StringWriter()
        def component = Stub(TestComponent)
        def publication = publication(component, id)

        def d1 = Stub(ModuleDependency)
        d1.group >> "g1"
        d1.name >> "m1"
        d1.version >> "v1"
        d1.transitive >> true
        d1.attributes >> ImmutableAttributes.EMPTY

        def d2 = Stub(ExternalDependency)
        d2.group >> "g2"
        d2.name >> "m2"
        d2.versionConstraint >> prefersAndRejects("v2", ["v3", "v4"])
        d2.transitive >> false
        d2.attributes >> ImmutableAttributes.EMPTY

        def d3 = Stub(ExternalDependency)
        d3.group >> "g3"
        d3.name >> "m3"
        d3.versionConstraint >> requires("v3")
        d3.transitive >> true
        d3.excludeRules >> [new DefaultExcludeRule("g4", "m4"), new DefaultExcludeRule(null, "m5"), new DefaultExcludeRule("g5", null)]
        d3.attributes >> ImmutableAttributes.EMPTY

        def d4 = Stub(ExternalDependency)
        d4.group >> "g4"
        d4.name >> "m4"
        d4.versionConstraint >> requires('')
        d4.transitive >> true
        d4.attributes >> ImmutableAttributes.EMPTY
        d4.artifacts >> [new DefaultDependencyArtifact("foo", "bar", "baz", "claz", null)]

        def d5 = Stub(ExternalDependency)
        d5.group >> "g5"
        d5.name >> "m5"
        d5.versionConstraint >> prefersAndRejects('', ['1.0'])
        d5.transitive >> true
        d5.attributes >> ImmutableAttributes.EMPTY

        def d6 = Stub(ExternalDependency)
        d6.group >> "g6"
        d6.name >> "m6"
        d6.versionConstraint >> strictly('1.0')
        d6.transitive >> true
        d6.reason >> 'custom reason'
        d6.attributes >> ImmutableAttributes.EMPTY

        def d7 = Stub(ExternalDependency)
        d7.group >> "g7"
        d7.name >> "m7"
        d7.versionConstraint >> requires('1.0')
        d7.transitive >> true
        d7.attributes >> attributes(foo: 'foo', bar: 'baz')

        def d8 = Stub(ModuleDependency)
        d8.group >> "g1"
        d8.name >> "m1"
        d8.version >> "v1"
        d8.transitive >> true
        d8.attributes >> ImmutableAttributes.EMPTY
        d8.requestedCapabilities >> [new DefaultImmutableCapability("org", "test", "1.0")]

        def v1 = Stub(UsageContext)
        v1.name >> "v1"
        v1.attributes >> attributes(usage: "compile")
        v1.dependencies >> [d1, d8]

        def v2 = Stub(UsageContext)
        v2.name >> "v2"
        v2.attributes >> attributes(usage: "runtime")
        v2.dependencies >> [d2, d3, d4, d5, d6, d7]

        component.usages >> [v1, v2]

        when:
        writeTo(writer, publication, [publication])

        then:
        writer.toString() == """{
  "formatVersion": "${GradleModuleMetadataParser.FORMAT_VERSION}",
  "component": {
    "group": "group",
    "module": "module",
    "version": "1.2",
    "attributes": {}
  },
  "createdBy": {
    "gradle": {
      "version": "${GradleVersion.current().version}"
    }
  },
  "variants": [
    {
      "name": "v1",
      "attributes": {
        "usage": "compile"
      },
      "dependencies": [
        {
          "group": "g1",
          "module": "m1",
          "version": {
            "requires": "v1"
          }
        },
        {
          "group": "g1",
          "module": "m1",
          "version": {
            "requires": "v1"
          },
          "requestedCapabilities": [
            {
              "group": "org",
              "name": "test",
              "version": "1.0"
            }
          ]
        }
      ]
    },
    {
      "name": "v2",
      "attributes": {
        "usage": "runtime"
      },
      "dependencies": [
        {
          "group": "g2",
          "module": "m2",
          "version": {
            "prefers": "v2",
            "rejects": [
              "v3",
              "v4"
            ]
          },
          "excludes": [
            {
              "group": "*",
              "module": "*"
            }
          ]
        },
        {
          "group": "g3",
          "module": "m3",
          "version": {
            "requires": "v3"
          },
          "excludes": [
            {
              "group": "g4",
              "module": "m4"
            },
            {
              "group": "*",
              "module": "m5"
            },
            {
              "group": "g5",
              "module": "*"
            }
          ]
        },
        {
          "group": "g4",
          "module": "m4",
          "thirdPartyCompatibility": {
            "artifactSelector": {
              "name": "foo",
              "type": "bar",
              "extension": "baz",
              "classifier": "claz"
            }
          }
        },
        {
          "group": "g5",
          "module": "m5",
          "version": {
            "rejects": [
              "1.0"
            ]
          }
        },
        {
          "group": "g6",
          "module": "m6",
          "version": {
            "strictly": "1.0",
            "requires": "1.0"
          },
          "reason": "custom reason"
        },
        {
          "group": "g7",
          "module": "m7",
          "version": {
            "requires": "1.0"
          },
          "attributes": {
            "bar": "baz",
            "foo": "foo"
          }
        }
      ]
    }
  ]
}
"""
    }

    def "writes file for component with variants with dependency constraints"() {
        def writer = new StringWriter()
        def component = Stub(TestComponent)
        def publication = publication(component, id)

        def dc1 = Stub(DependencyConstraint)
        dc1.group >> "g1"
        dc1.name >> "m1"
        dc1.versionConstraint >> requires("v1")
        dc1.attributes >> ImmutableAttributes.EMPTY

        def dc2 = Stub(DependencyConstraint)
        dc2.group >> "g2"
        dc2.name >> "m2"
        dc2.versionConstraint >> prefersAndRejects("v2", ["v3", "v4"])
        dc2.attributes >> ImmutableAttributes.EMPTY

        def dc3 = Stub(DependencyConstraint)
        dc3.group >> "g3"
        dc3.name >> "m3"
        dc3.versionConstraint >> requires("v3")
        dc3.attributes >> ImmutableAttributes.EMPTY

        def dc4 = Stub(DependencyConstraint)
        dc4.group >> "g4"
        dc4.name >> "m4"
        dc4.versionConstraint >> strictly("v4")
        dc4.attributes >> attributes(quality: 'awesome', channel: 'canary')

        def v1 = Stub(UsageContext)
        v1.name >> "v1"
        v1.attributes >> attributes(usage: "compile")
        v1.dependencyConstraints >> [dc1]
        def v2 = Stub(UsageContext)
        v2.name >> "v2"
        v2.attributes >> attributes(usage: "runtime")
        v2.dependencyConstraints >> [dc2, dc3, dc4]

        component.usages >> [v1, v2]

        when:
        writeTo(writer, publication, [publication])

        then:
        writer.toString() == """{
  "formatVersion": "${GradleModuleMetadataParser.FORMAT_VERSION}",
  "component": {
    "group": "group",
    "module": "module",
    "version": "1.2",
    "attributes": {}
  },
  "createdBy": {
    "gradle": {
      "version": "${GradleVersion.current().version}"
    }
  },
  "variants": [
    {
      "name": "v1",
      "attributes": {
        "usage": "compile"
      },
      "dependencyConstraints": [
        {
          "group": "g1",
          "module": "m1",
          "version": {
            "requires": "v1"
          }
        }
      ]
    },
    {
      "name": "v2",
      "attributes": {
        "usage": "runtime"
      },
      "dependencyConstraints": [
        {
          "group": "g2",
          "module": "m2",
          "version": {
            "prefers": "v2",
            "rejects": [
              "v3",
              "v4"
            ]
          }
        },
        {
          "group": "g3",
          "module": "m3",
          "version": {
            "requires": "v3"
          }
        },
        {
          "group": "g4",
          "module": "m4",
          "version": {
            "strictly": "v4",
            "requires": "v4"
          },
          "attributes": {
            "channel": "canary",
            "quality": "awesome"
          }
        }
      ]
    }
  ]
}
"""
    }

    enum SomeEnum {
        VALUE_1, VALUE_2
    }

    def "writes file for component with variants with attributes"() {
        def writer = new StringWriter()
        def component = Stub(TestComponent)
        def publication = publication(component, id)

        def platform = TestUtil.objectFactory().named(Named, "windows")

        def v1 = Stub(UsageContext)
        v1.name >> "v1"
        v1.attributes >> attributesTyped(
                (Attribute.of("usage", String)): "compile",
                (Attribute.of("debuggable", Boolean)): true,
                (Attribute.of("platform", Named)): platform,
                (Attribute.of("linkage", SomeEnum)): SomeEnum.VALUE_1)
        def v2 = Stub(UsageContext)
        v2.name >> "v2"
        v2.attributes >> attributesTyped(
                (Attribute.of("usage", String)): "runtime",
                (Attribute.of("debuggable", Boolean)): true,
                (Attribute.of("platform", Named)): platform,
                (Attribute.of("linkage", SomeEnum)): SomeEnum.VALUE_2)

        component.usages >> [v1, v2]

        when:
        writeTo(writer, publication, [publication])

        then:
        writer.toString() == """{
  "formatVersion": "${GradleModuleMetadataParser.FORMAT_VERSION}",
  "component": {
    "group": "group",
    "module": "module",
    "version": "1.2",
    "attributes": {}
  },
  "createdBy": {
    "gradle": {
      "version": "${GradleVersion.current().version}"
    }
  },
  "variants": [
    {
      "name": "v1",
      "attributes": {
        "debuggable": true,
        "linkage": "VALUE_1",
        "platform": "windows",
        "usage": "compile"
      }
    },
    {
      "name": "v2",
      "attributes": {
        "debuggable": true,
        "linkage": "VALUE_2",
        "platform": "windows",
        "usage": "runtime"
      }
    }
  ]
}
"""
    }

    def "writes file with component that has children published in other modules"() {
        def writer = new StringWriter()
        def rootComponent = Stub(TestComponent)
        def rootPublication = publication(rootComponent, id)

        def id1 = DefaultModuleVersionIdentifier.newId("group", "other-1", "1")
        def comp1 = Stub(TestComponent)
        def publication1 = publication(comp1, id1)
        def id2 = DefaultModuleVersionIdentifier.newId("group", "other-2", "2")
        def comp2 = Stub(TestComponent)
        def publication2 = publication(comp2, id2)

        def c1 = Stub(Capability) {
            getGroup() >> 'org.test'
            getName() >> 'foo'
            getVersion() >> '1'
        }

        def v1 = Stub(UsageContext)
        v1.name >> "v1"
        v1.attributes >> attributes(usage: "compile")
        v1.capabilities >> [c1]
        def v2 = Stub(UsageContext)
        v2.name >> "v2"
        v2.attributes >> attributes(usage: "runtime")

        rootComponent.variants >> [comp1, comp2]
        rootComponent.usages >> []
        comp1.usages >> [v1]
        comp2.usages >> [v2]

        when:
        writeTo(writer, rootPublication, [rootPublication, publication1, publication2])

        then:
        writer.toString() == """{
  "formatVersion": "${GradleModuleMetadataParser.FORMAT_VERSION}",
  "component": {
    "group": "group",
    "module": "module",
    "version": "1.2",
    "attributes": {}
  },
  "createdBy": {
    "gradle": {
      "version": "${GradleVersion.current().version}"
    }
  },
  "variants": [
    {
      "name": "v1",
      "attributes": {
        "usage": "compile"
      },
      "available-at": {
        "url": "../../other-1/1/other-1-1.module",
        "group": "group",
        "module": "other-1",
        "version": "1"
      },
      "capabilities": [
        {
          "group": "org.test",
          "name": "foo",
          "version": "1"
        }
      ]
    },
    {
      "name": "v2",
      "attributes": {
        "usage": "runtime"
      },
      "available-at": {
        "url": "../../other-2/2/other-2-2.module",
        "group": "group",
        "module": "other-2",
        "version": "2"
      }
    }
  ]
}
"""
    }

    def "fails to write component with child at the same coordinates"() {
        def writer = new StringWriter()
        def rootComponent = Stub(TestComponent)
        def rootPublication = publication(rootComponent, id)

        def id1 = DefaultModuleVersionIdentifier.newId("group", "module", "1")
        def comp1 = Stub(TestComponent)
        def publication1 = publication(comp1, id1)

        def c1 = Stub(Capability) {
            getGroup() >> 'org.test'
            getName() >> 'foo'
            getVersion() >> '1'
        }

        def v1 = Stub(UsageContext)
        v1.name >> "v1"
        v1.attributes >> attributes(usage: "compile")
        v1.capabilities >> [c1]

        rootComponent.variants >> [comp1]
        rootComponent.usages >> []
        comp1.usages >> [v1]

        when:
        writeTo(writer, rootPublication, [rootPublication, publication1])

        then:
        InvalidUserCodeException ex = thrown()
        ex.message.contains("Cannot have a remote variant with coordinates 'group:module' that are the same as the module itself")
    }

    def "writes file for module that is a child of another component"() {
        def writer = new StringWriter()
        def rootComponent = Stub(TestComponent)
        def rootPublication = publication(rootComponent, id)

        def child1 = DefaultModuleVersionIdentifier.newId("group", "child", "1")
        def childComponent = Stub(TestComponent)
        def childPublication = publication(childComponent, child1)

        def variant = Stub(UsageContext)
        variant.name >> "v1"
        variant.attributes >> attributes(usage: "compile")

        rootComponent.variants >> [childComponent]
        rootComponent.usages >> []
        childComponent.usages >> [variant]

        when:
        writeTo(writer, childPublication, [rootPublication, childPublication])

        then:
        writer.toString() == """{
  "formatVersion": "${GradleModuleMetadataParser.FORMAT_VERSION}",
  "component": {
    "url": "../../module/1.2/module-1.2.module",
    "group": "group",
    "module": "module",
    "version": "1.2",
    "attributes": {}
  },
  "createdBy": {
    "gradle": {
      "version": "${GradleVersion.current().version}"
    }
  },
  "variants": [
    {
      "name": "v1",
      "attributes": {
        "usage": "compile"
      }
    }
  ]
}
"""
    }

    def "writes file for component with variants with capabilities"() {
        def writer = new StringWriter()
        def component = Stub(TestComponent)
        def publication = publication(component, id)

        def c1 = Stub(Capability) {
            getGroup() >> 'org.test'
            getName() >> 'foo'
            getVersion() >> '1'
        }
        def c2 = Stub(Capability) {
            getGroup() >> 'org.test'
            getName() >> 'bar'
            getVersion() >> '2'
        }

        def v1 = Stub(UsageContext)
        v1.name >> "v1"
        v1.attributes >> attributes(usage: "compile")
        v1.dependencies >> []
        v1.capabilities >> [c1]

        def v2 = Stub(UsageContext)
        v2.name >> "v2"
        v2.attributes >> attributes(usage: "runtime")
        v2.dependencies >> []
        v2.capabilities >> [c1, c2]

        component.usages >> [v1, v2]

        when:
        writeTo(writer, publication, [publication])

        then:
        writer.toString() == """{
  "formatVersion": "${GradleModuleMetadataParser.FORMAT_VERSION}",
  "component": {
    "group": "group",
    "module": "module",
    "version": "1.2",
    "attributes": {}
  },
  "createdBy": {
    "gradle": {
      "version": "${GradleVersion.current().version}"
    }
  },
  "variants": [
    {
      "name": "v1",
      "attributes": {
        "usage": "compile"
      },
      "capabilities": [
        {
          "group": "org.test",
          "name": "foo",
          "version": "1"
        }
      ]
    },
    {
      "name": "v2",
      "attributes": {
        "usage": "runtime"
      },
      "capabilities": [
        {
          "group": "org.test",
          "name": "foo",
          "version": "1"
        },
        {
          "group": "org.test",
          "name": "bar",
          "version": "2"
        }
      ]
    }
  ]
}
"""
    }

    @Issue("https://github.com/gradle/gradle/issues/5035")
    def "writes file for component with configuration excludes"() {
        def writer = new StringWriter()
        def component = Stub(TestComponent)
        def publication = publication(component, id)

        def apiDependency = Stub(ExternalDependency)
        apiDependency.group >> "com.acme"
        apiDependency.name >> "api"
        apiDependency.versionConstraint >> requires("v1")
        apiDependency.transitive >> true
        apiDependency.excludeRules >> [new DefaultExcludeRule("com.example.bad", "api")]
        apiDependency.attributes >> ImmutableAttributes.EMPTY

        def runtimeDependency = Stub(ExternalDependency)
        runtimeDependency.group >> "com.acme"
        runtimeDependency.name >> "runtime"
        runtimeDependency.versionConstraint >> DefaultImmutableVersionConstraint.of()
        runtimeDependency.transitive >> true
        runtimeDependency.excludeRules >> [new DefaultExcludeRule("com.example.bad", "runtime")]
        runtimeDependency.attributes >> ImmutableAttributes.EMPTY

        def intransitiveDependency = Stub(ExternalDependency)
        intransitiveDependency.group >> "com.acme"
        intransitiveDependency.name >> "intransitive"
        intransitiveDependency.versionConstraint >> DefaultImmutableVersionConstraint.of()
        intransitiveDependency.transitive >> false
        intransitiveDependency.attributes >> ImmutableAttributes.EMPTY

        def v1 = Stub(UsageContext)
        v1.name >> "v1"
        v1.dependencies >> [apiDependency]
        v1.globalExcludes >> [new DefaultExcludeRule("org.example.api", null)]

        def v2 = Stub(UsageContext)
        v2.name >> "v2"
        v2.dependencies >> [runtimeDependency, intransitiveDependency]
        v2.globalExcludes >> [new DefaultExcludeRule("org.example.runtime", null)]

        component.usages >> [v1, v2]

        when:
        writeTo(writer, publication, [publication])

        then:
        writer.toString().contains """
  "variants": [
    {
      "name": "v1",
      "attributes": {},
      "dependencies": [
        {
          "group": "com.acme",
          "module": "api",
          "version": {
            "requires": "v1"
          },
          "excludes": [
            {
              "group": "org.example.api",
              "module": "*"
            },
            {
              "group": "com.example.bad",
              "module": "api"
            }
          ]
        }
      ]
    },
    {
      "name": "v2",
      "attributes": {},
      "dependencies": [
        {
          "group": "com.acme",
          "module": "runtime",
          "excludes": [
            {
              "group": "org.example.runtime",
              "module": "*"
            },
            {
              "group": "com.example.bad",
              "module": "runtime"
            }
          ]
        },
        {
          "group": "com.acme",
          "module": "intransitive",
          "excludes": [
            {
              "group": "*",
              "module": "*"
            }
          ]
        }
      ]
    }
  ]
"""
    }

    def "fail to write file for component without any version"() {
        def writer = new StringWriter()
        def component = Stub(TestComponent)
        def publication = publication(component, id)

        def apiDependency = Stub(ExternalDependency)
        apiDependency.group >> "com.acme"
        apiDependency.name >> "api"
        apiDependency.versionConstraint >> DefaultImmutableVersionConstraint.of()
        apiDependency.transitive >> true
        apiDependency.excludeRules >> [new DefaultExcludeRule("com.example.bad", "api")]
        apiDependency.attributes >> ImmutableAttributes.EMPTY

        def v1 = Stub(UsageContext)
        v1.name >> "v1"
        v1.dependencies >> [apiDependency]

        component.usages >> [v1]

        when:
        writeTo(writer, publication, [publication])

        and:
        writer.toString()

        then:
        def failure = thrown(InvalidUserCodeException)
        failure.message.contains("- Publication only contains dependencies and/or constraints without a version.")
    }

    def "write file for resolved dependencies"() {
        def writer = new StringWriter()
        def component = Stub(TestComponent)
        def mappingStrategy = Mock(VersionMappingStrategyInternal)
        def variantMappingStrategy = Mock(VariantVersionMappingStrategyInternal)
        def publication = publication(component, id, mappingStrategy)

        mappingStrategy.findStrategyForVariant(_) >> variantMappingStrategy
        variantMappingStrategy.maybeResolveVersion(_ as String, _ as String, _) >> { String group, String name, Path identityPath ->
            DefaultModuleVersionIdentifier.newId(group, name, 'v99')
        }

        def d1 = Stub(ModuleDependency)
        d1.group >> "g1"
        d1.name >> "m1"
        d1.version >> "v1"
        d1.transitive >> true
        d1.attributes >> ImmutableAttributes.EMPTY

        def d2 = Stub(ExternalDependency)
        d2.group >> "g2"
        d2.name >> "m2"
        d2.versionConstraint >> prefersAndRejects("v2", ["v3", "v4"])
        d2.transitive >> true
        d2.attributes >> ImmutableAttributes.EMPTY

        def d3 = Stub(ExternalDependency)
        d3.group >> "g3"
        d3.name >> "m3"
        d3.versionConstraint >> requires("v3")
        d3.transitive >> true
        d3.attributes >> ImmutableAttributes.EMPTY

        def d4 = Stub(ExternalDependency)
        d4.group >> "g4"
        d4.name >> "m4"
        d4.versionConstraint >> requires('')
        d4.transitive >> true
        d4.attributes >> ImmutableAttributes.EMPTY

        def d5 = Stub(ExternalDependency)
        d5.group >> "g5"
        d5.name >> "m5"
        d5.versionConstraint >> strictly('v6')
        d5.transitive >> true
        d5.attributes >> ImmutableAttributes.EMPTY

        def d6 = Stub(ExternalDependency)
        d6.group >> "g6"
        d6.name >> "m6"
        d6.versionConstraint >> strictly('')
        d6.transitive >> true
        d6.attributes >> ImmutableAttributes.EMPTY

        def v1 = Stub(UsageContext)
        v1.name >> "v1"
        v1.attributes >> attributes(usage: "compile")
        v1.dependencies >> [d1, d6]

        def v2 = Stub(UsageContext)
        v2.name >> "v2"
        v2.attributes >> attributes(usage: "runtime")
        v2.dependencies >> [d2, d3, d4, d5]

        component.usages >> [v1, v2]

        when:
        writeTo(writer, publication, [publication])

        then:
        writer.toString() == """{
  "formatVersion": "${GradleModuleMetadataParser.FORMAT_VERSION}",
  "component": {
    "group": "group",
    "module": "module",
    "version": "1.2",
    "attributes": {}
  },
  "createdBy": {
    "gradle": {
      "version": "${GradleVersion.current().version}"
    }
  },
  "variants": [
    {
      "name": "v1",
      "attributes": {
        "usage": "compile"
      },
      "dependencies": [
        {
          "group": "g1",
          "module": "m1",
          "version": {
            "requires": "v99"
          }
        },
        {
          "group": "g6",
          "module": "m6",
          "version": {
            "requires": "v99"
          }
        }
      ]
    },
    {
      "name": "v2",
      "attributes": {
        "usage": "runtime"
      },
      "dependencies": [
        {
          "group": "g2",
          "module": "m2",
          "version": {
            "requires": "v99",
            "rejects": [
              "v3",
              "v4"
            ]
          }
        },
        {
          "group": "g3",
          "module": "m3",
          "version": {
            "requires": "v99"
          }
        },
        {
          "group": "g4",
          "module": "m4",
          "version": {
            "requires": "v99"
          }
        },
        {
          "group": "g5",
          "module": "m5",
          "version": {
            "strictly": "v99",
            "requires": "v99"
          }
        }
      ]
    }
  ]
}
"""
    }

    @Issue("https://github.com/gradle/gradle/issues/19769")
    def "writes different components for different publications"() {
        def writer = new StringWriter()

        def comp = Stub(TestComponent) //same component, intentional

        def id1 = DefaultModuleVersionIdentifier.newId("group", "other-1", "1")
        def publication1 = publication(comp, id1)
        def id2 = DefaultModuleVersionIdentifier.newId("group", "other-2", "2")
        def publication2 = publication(comp, id2)

        def v1 = Stub(UsageContext)
        v1.name >> "v1"
        v1.attributes >> attributes(usage: "compile")
        def v2 = Stub(UsageContext)
        v2.name >> "v2"
        v2.attributes >> attributes(usage: "runtime")

        comp.usages >> [v1, v2]

        when:
        writeTo(writer, publication1, [publication1, publication2])
        writeTo(writer, publication2, [publication1, publication2])

        then:
        writer.toString() == """{
  "formatVersion": "${GradleModuleMetadataParser.FORMAT_VERSION}",
  "component": {
    "group": "group",
    "module": "other-1",
    "version": "1",
    "attributes": {}
  },
  "createdBy": {
    "gradle": {
      "version": "${GradleVersion.current().version}"
    }
  },
  "variants": [
    {
      "name": "v1",
      "attributes": {
        "usage": "compile"
      }
    },
    {
      "name": "v2",
      "attributes": {
        "usage": "runtime"
      }
    }
  ]
}
{
  "formatVersion": "${GradleModuleMetadataParser.FORMAT_VERSION}",
  "component": {
    "group": "group",
    "module": "other-2",
    "version": "2",
    "attributes": {}
  },
  "createdBy": {
    "gradle": {
      "version": "${GradleVersion.current().version}"
    }
  },
  "variants": [
    {
      "name": "v1",
      "attributes": {
        "usage": "compile"
      }
    },
    {
      "name": "v2",
      "attributes": {
        "usage": "runtime"
      }
    }
  ]
}
"""
    }

    def publication(SoftwareComponentInternal component, ModuleVersionIdentifier coords, VersionMappingStrategyInternal mappingStrategyInternal = null, boolean withBuildId = false) {
        def publication = Stub(PublicationInternal)
        publication.component >> Providers.of(component)
        publication.coordinates >> coords
        publication.versionMappingStrategy >> mappingStrategyInternal
        publication.isPublishBuildId() >> withBuildId
        return publication
    }

    interface TestComponent extends ComponentWithVariants, SoftwareComponentInternal {

    }

    class SimplePublishedFile implements PublicationInternal.PublishedFile {
        SimplePublishedFile(String name, String uri) {
            this.name = name
            this.uri = uri
        }
        String name
        String uri
    }
}
