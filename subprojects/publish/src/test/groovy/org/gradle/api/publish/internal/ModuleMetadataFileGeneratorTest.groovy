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
        def component = Stub(ComponentWithVariants)

        when:
        generator.generateTo(id, component, writer)

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
        generator.generateTo(id, component, writer)

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
        generator.generateTo(id, component, writer)

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

        def platform = TestUtil.objectFactory().named(Named, "windows")

        def v1 = Stub(UsageContext)
        v1.name >> "v1"
        v1.attributes >> attributes(usage: "compile", debuggable: true, platform: platform)
        def v2 = Stub(UsageContext)
        v2.name >> "v2"
        v2.attributes >> attributes()

        component.usages >> [v1, v2]

        when:
        generator.generateTo(id, component, writer)

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
      "name": "v2",
      "attributes": {}
    }
  ]
}
"""
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
