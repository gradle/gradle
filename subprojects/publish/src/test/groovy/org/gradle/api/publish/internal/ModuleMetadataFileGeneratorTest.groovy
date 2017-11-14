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

package org.gradle.api.publish.internal

import org.gradle.api.Named
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.attributes.Attribute
import org.gradle.api.component.ComponentWithVariants
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.component.UsageContext
import org.gradle.internal.id.UniqueId
import org.gradle.internal.scopeids.id.BuildInvocationScopeId
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.GradleVersion
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class ModuleMetadataFileGeneratorTest extends Specification {

    VersionConstraint prefers(String version) {
        Mock(VersionConstraint) {
            getPreferredVersion() >> version
            getRejectedVersions() >> []
        }
    }

    VersionConstraint prefersAndRejects(String version, List<String> rejects) {
        Mock(VersionConstraint) {
            getPreferredVersion() >> version
            getRejectedVersions() >> rejects
        }
    }

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def buildId = UniqueId.generate()
    def id = DefaultModuleVersionIdentifier.newId("group", "module", "1.2")
    def generator = new ModuleMetadataFileGenerator(new BuildInvocationScopeId(buildId), new ProjectDependencyPublicationResolver())

    def "writes file for component with no variants"() {
        def writer = new StringWriter()
        def component = Stub(TestComponent)
        def publication = publication(component, id)

        when:
        generator.generateTo(publication, [publication], writer)

        then:
        writer.toString() == """{
  "formatVersion": "0.2",
  "component": {
    "group": "group",
    "module": "module",
    "version": "1.2"
  },
  "createdBy": {
    "gradle": {
      "version": "${GradleVersion.current().version}",
      "buildId": "${buildId}"
    }
  }
}
"""
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
        generator.generateTo(publication, [publication], writer)

        then:
        writer.toString() == """{
  "formatVersion": "0.2",
  "component": {
    "group": "group",
    "module": "module",
    "version": "1.2"
  },
  "createdBy": {
    "gradle": {
      "version": "${GradleVersion.current().version}",
      "buildId": "${buildId}"
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

        def d2 = Stub(ExternalDependency)
        d2.group >> "g2"
        d2.name >> "m2"
        d2.versionConstraint >> prefersAndRejects("v2", ["v3", "v4"])

        def v1 = Stub(UsageContext)
        v1.name >> "v1"
        v1.attributes >> attributes(usage: "compile")
        v1.dependencies >> [d1]
        def v2 = Stub(UsageContext)
        v2.name >> "v2"
        v2.attributes >> attributes(usage: "runtime")
        v2.dependencies >> [d2]

        component.usages >> [v1, v2]

        when:
        generator.generateTo(publication, [publication], writer)

        then:
        writer.toString() == """{
  "formatVersion": "0.2",
  "component": {
    "group": "group",
    "module": "module",
    "version": "1.2"
  },
  "createdBy": {
    "gradle": {
      "version": "${GradleVersion.current().version}",
      "buildId": "${buildId}"
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
            "prefers": "v1",
            "rejects": []
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
            "prefers": "v2",
            "rejects": [
              "v3",
              "v4"
            ]
          }
        }
      ]
    }
  ]
}
"""
    }

    def "writes file for component with variants with attributes"() {
        def writer = new StringWriter()
        def component = Stub(TestComponent)
        def publication = publication(component, id)

        def platform = TestUtil.objectFactory().named(Named, "windows")

        def v1 = Stub(UsageContext)
        v1.name >> "v1"
        v1.attributes >> attributes(usage: "compile", debuggable: true, platform: platform)
        def v2 = Stub(UsageContext)
        v2.name >> "v2"
        v2.attributes >> attributes()

        component.usages >> [v1, v2]

        when:
        generator.generateTo(publication, [publication], writer)

        then:
        writer.toString() == """{
  "formatVersion": "0.2",
  "component": {
    "group": "group",
    "module": "module",
    "version": "1.2"
  },
  "createdBy": {
    "gradle": {
      "version": "${GradleVersion.current().version}",
      "buildId": "${buildId}"
    }
  },
  "variants": [
    {
      "name": "v1",
      "attributes": {
        "debuggable": true,
        "platform": "windows",
        "usage": "compile"
      }
    },
    {
      "name": "v2"
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

        def v1 = Stub(UsageContext)
        v1.name >> "v1"
        v1.attributes >> attributes(usage: "compile")
        def v2 = Stub(UsageContext)
        v2.name >> "v2"
        v2.attributes >> attributes(usage: "runtime")

        rootComponent.variants >> [comp1, comp2]
        rootComponent.usages >> []
        comp1.usages >> [v1]
        comp2.usages >> [v2]

        when:
        generator.generateTo(rootPublication, [rootPublication, publication1, publication2], writer)

        then:
        writer.toString() == """{
  "formatVersion": "0.2",
  "component": {
    "group": "group",
    "module": "module",
    "version": "1.2"
  },
  "createdBy": {
    "gradle": {
      "version": "${GradleVersion.current().version}",
      "buildId": "${buildId}"
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
      }
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
        generator.generateTo(childPublication, [rootPublication, childPublication], writer)

        then:
        writer.toString() == """{
  "formatVersion": "0.2",
  "component": {
    "url": "../../module/1.2/module-1.2.module",
    "group": "group",
    "module": "module",
    "version": "1.2"
  },
  "createdBy": {
    "gradle": {
      "version": "${GradleVersion.current().version}",
      "buildId": "${buildId}"
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

    def publication(SoftwareComponentInternal component, ModuleVersionIdentifier coords) {
        def publication = Stub(PublicationInternal)
        publication.component >> component
        publication.coordinates >> coords
        return publication
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

    interface TestComponent extends ComponentWithVariants, SoftwareComponentInternal {

    }

    class SimplePublishedFile implements PublicationInternal.PublishedFile {
        SimplePublishedFile(String name, String uri) {
            this.name = name
            this.uri = uri
        }
        String name;
        String uri;
    }
}
