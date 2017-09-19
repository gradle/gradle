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

import org.gradle.api.component.ComponentWithVariants
import org.gradle.internal.id.UniqueId
import org.gradle.internal.scopeids.id.BuildInvocationScopeId
import org.gradle.util.GradleVersion
import spock.lang.Specification


class MetadataFileGeneratorTest extends Specification {
    def buildId = UniqueId.generate()
    def generator = new MetadataFileGenerator(new BuildInvocationScopeId(buildId))

    def "writes file for component with no variants"() {
        def writer = new StringWriter()
        def component = Stub(ComponentWithVariants)

        when:
        generator.generateTo(component, writer)

        then:
        writer.toString() == """{
  "formatVersion": "0.1",
  "createdBy": {
    "gradle": {
      "version": "${GradleVersion.current().version}",
      "buildId": "${buildId}"
    }
  }
}"""
    }
}
