/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result

import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier.newId
import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector

/**
 * by Szczepan Faber, created at: 8/27/12
 */
class ResolutionResultBuilderSpec extends Specification {

    def builder = new ResolutionResultBuilder()

    def "builds basic graph"() {
        given:
        builder.start(confId("root"))
        resolvedConf("root", [dep("mid1"), dep("mid2")])

        resolvedConf("mid1", [dep("leaf1"), dep("leaf2")])
        resolvedConf("mid2", [dep("leaf3"), dep("leaf4")])

        resolvedConf("leaf1", [])
        resolvedConf("leaf2", [])
        resolvedConf("leaf3", [])
        resolvedConf("leaf4", [])

        when:
        def result = builder.getResult()

        then:
        result.print() == """a:root:1
  a:mid1:1
    a:leaf1:1
    a:leaf2:1
  a:mid2:1
    a:leaf3:1
    a:leaf4:1
"""
    }

    def "builds graph with cycles"() {
        given:
        builder.start(confId("a"))
        resolvedConf("a", [dep("b")])
        resolvedConf("b", [dep("c")])
        resolvedConf("c", [dep("a")])

        when:
        def result = builder.getResult()

        then:
        result.print() == """a:a:1
  a:b:1
    a:c:1
      a:a:1
"""
    }

    def "accumulates dependencies"() {
        given:
        builder.start(confId("root"))
        resolvedConf("root", [dep("mid1")])

        resolvedConf("mid1", [dep("leaf1")])
        resolvedConf("mid1", [dep("leaf2")])

        resolvedConf("leaf1", [])
        resolvedConf("leaf2", [])

        when:
        def result = builder.getResult()

        then:
        result.print() == """a:root:1
  a:mid1:1
    a:leaf1:1
    a:leaf2:1
"""
    }

    def "builds graph without unresolved deps"() {
        given:
        builder.start(confId("a"))
        resolvedConf("a", [dep("b"), dep("c"), dep("x", new RuntimeException("unresolved!"))])
        resolvedConf("b", [])
        resolvedConf("c", [])

        when:
        def result = builder.getResult()

        then:
        result.print() == """a:a:1
  a:b:1
  a:c:1
"""
    }


    private void resolvedConf(String module, List<InternalDependencyResult> deps) {
        builder.resolvedConfiguration(confId(module), deps)
    }

    private InternalDependencyResult dep(String requested, Exception failure = null, String selected = requested) {
        new InternalDependencyResult(newSelector("a", requested, "1"), newId("a", selected, "1"), failure)
    }

    private ResolvedConfigurationIdentifier confId(String module, String configuration='conf') {
        new ResolvedConfigurationIdentifier("a", module, "1", configuration)
    }
}
