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

import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.attributes.Usage
import org.gradle.api.component.ComponentWithVariants
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.component.UsageContext
import org.gradle.internal.id.UniqueId
import org.gradle.internal.scopeids.id.BuildInvocationScopeId
import org.gradle.util.GradleVersion
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
  "formatVersion": "0.1",
  "createdBy": {
    "gradle": {
      "version": "${GradleVersion.current().version}",
      "buildId": "${buildId}"
    }
  }
}
"""
    }

    def "writes file for component with variants"() {
        def writer = new StringWriter()
        def component = Stub(TestComponent)
        def usage1 = Stub(Usage)
        usage1.name >> "api"
        def usage2 = Stub(Usage)
        usage2.name >> "runtime"

        def a1 = Stub(PublishArtifact)
        a1.file >> new File("artifact-1")
        a1.extension >> "zip"
        a1.classifier >> ""
        def a2 = Stub(PublishArtifact)
        a2.file >> new File("thing.dll")
        a2.extension >> "dll"
        a2.classifier >> "windows"

        def v1 = Stub(UsageContext)
        v1.usage >> usage1
        v1.artifacts >> [a1]
        def v2 = Stub(UsageContext)
        v2.usage >> usage2
        v2.artifacts >> [a2]

        component.usages >> [v1, v2]

        when:
        generator.generateTo(id, component, writer)

        then:
        writer.toString() == """{
  "formatVersion": "0.1",
  "createdBy": {
    "gradle": {
      "version": "${GradleVersion.current().version}",
      "buildId": "${buildId}"
    }
  },
  "variants": [
    {
      "name": "api",
      "attributes": {
        "org.gradle.api.attributes.Usage": "api"
      },
      "files": [
        {
          "name": "artifact-1",
          "url": "module-1.2.zip"
        }
      ]
    },
    {
      "name": "runtime",
      "attributes": {
        "org.gradle.api.attributes.Usage": "runtime"
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

    interface TestComponent extends ComponentWithVariants, SoftwareComponentInternal {

    }
}
