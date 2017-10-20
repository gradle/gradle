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
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.attributes.Attribute
import org.gradle.api.component.ComponentWithVariants
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.component.UsageContext
import org.gradle.internal.id.UniqueId
import org.gradle.internal.scopeids.id.BuildInvocationScopeId
import org.gradle.util.GradleVersion
import org.gradle.util.TestUtil
import spock.lang.Specification

class ModuleMetadataFileGeneratorTest extends Specification {
    def buildId = UniqueId.generate()
    def id = DefaultModuleVersionIdentifier.newId("group", "module", "1.2")
    def generator = new ModuleMetadataFileGenerator(new BuildInvocationScopeId(buildId))

    def "writes file for component with no variants"() {
        def writer = new StringWriter()
        def component = Stub(TestComponent)
        def publication = publication(component, id)

        when:
        generator.generateTo(publication, [publication], writer)

        then:
        writer.toString() == """{
  "formatVersion": "0.2",
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

        def a1 = Stub(PublishArtifact)
        a1.file >> new File("artifact-1")
        a1.extension >> "zip"
        a1.classifier >> ""
        def a2 = Stub(PublishArtifact)
        a2.file >> new File("thing.dll")
        a2.extension >> "dll"
        a2.classifier >> "windows"

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
          "name": "artifact-1",
          "url": "module-1.2.zip"
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
          "name": "thing.dll",
          "url": "module-1.2-windows.dll"
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

        def d2 = Stub(ModuleDependency)
        d2.group >> "g2"
        d2.name >> "m2"
        d2.version >> "v2"

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
          "version": "v1"
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
          "version": "v2"
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

        def id1 = DefaultModuleVersionIdentifier.newId("g1", "other-1", "1")
        def comp1 = Stub(TestComponent)
        def publication1 = publication(comp1, id1)
        def id2 = DefaultModuleVersionIdentifier.newId("g2", "other-2", "2")
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
          "module": "other-1",
          "version": "1"
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
          "module": "other-2",
          "version": "2"
        }
      ]
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
}
