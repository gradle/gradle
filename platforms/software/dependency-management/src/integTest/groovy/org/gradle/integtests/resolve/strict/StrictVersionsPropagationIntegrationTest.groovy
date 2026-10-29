/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.integtests.resolve.strict

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * A strict version applies to every node that all paths from the root reach through its declarer, or through a
 * node that endorses the declarer. The result must not depend on the order in which dependencies are declared,
 * nor on cycles below the declarer.
 */
class StrictVersionsPropagationIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        // Each module version and what it depends on: "~" for a constraint, "^" for an endorsing dependency,
        // and "!" for a strict version.
        publish(
            // An endorsed platform pins x:1 below a library requesting x:2, directly or one level down
            'ql:1': ['x:2'], 'qm:1': ['qn:1'], 'qn:1': ['x:2'], 'qp:1': ['~x:!1'],
            // A library in a cycle with its BOM, through a constraint or a dependency
            'jh:1': ['x:2', 'jbh:1'], 'jbh:1': ['~jh:1'],
            'jh3:1': ['x:2', 'jbh3:1'], 'jbh3:1': ['jh3:1'],
            // The Jackson shape: the library endorses its BOM, which constrains the library back
            'jd:1': ['x:2', '^jb:1'], 'jb:1': ['~jd:1', '~x:2'],
            // A library with a BOM that only constrains x: no cycle
            'jh2:1': ['x:2', 'jbh2:1'], 'jbh2:1': ['~x:2'],
            // A three-node cycle, a self-loop, and a cycle entered at both of its nodes
            'h1:1': ['h2:1'], 'h2:1': ['h3:1'], 'h3:1': ['h1:1', 'x:2'],
            's1:1': ['s1:1', 'x:2'],
            'e1:1': ['e2:1'], 'e2:1': ['e1:1', 'x:2'],
            // Cycles below the declarer of a strict version
            'a5:1': ['p:1', 'x:!1'], 'p:1': ['q:1'], 'q:1': ['p:1', 'x:2'],
            'a7:1': ['x:!1', 'r7:1'], 'r7:1': ['a7:1', 'x:2'],
            'ca:1': ['cc:1', 'x:2'], 'cc:1': ['ca:1', 'x:!1'],
            'c6:1': ['q:1'],
            'x:1': [], 'x:2': [],
        )
        buildFile << """
            ${mavenTestRepository()}
            configurations {
                conf
            }
            tasks.register("resolve") {
                def files = configurations.conf.incoming.files
                doLast {
                    println("x: " + files.files.collect { it.name }.findAll { it.startsWith("x-") }.sort())
                }
            }
        """
    }

    def "resolves #expected with #description"() {
        given:
        root.each { String dependency ->
            if (dependency.startsWith("^")) {
                buildFile << """
                    dependencies { conf("org:${dependency.substring(1)}") { endorseStrictVersions() } }
                """
            } else {
                buildFile << """
                    dependencies { conf("org:$dependency") }
                """
            }
        }

        when:
        succeeds("resolve")

        then:
        outputContains("x: [$expected]")

        where:
        description                                                    | root                     | expected
        "a library declared before an endorsed platform"              | ['ql:1', '^qp:1']        | 'x-1.jar'
        "a library declared after an endorsed platform"               | ['^qp:1', 'ql:1']        | 'x-1.jar'
        "a transitive library declared before an endorsed platform"   | ['qm:1', '^qp:1']        | 'x-1.jar'
        "a transitive library declared after an endorsed platform"    | ['^qp:1', 'qm:1']        | 'x-1.jar'
        "a library in a cycle through a constraint, platform last"    | ['jh:1', '^qp:1']        | 'x-1.jar'
        "a library in a cycle through a constraint, platform first"   | ['^qp:1', 'jh:1']        | 'x-1.jar'
        "a library in a cycle through a dependency, platform last"    | ['jh3:1', '^qp:1']       | 'x-1.jar'
        "a library in a cycle through a dependency, platform first"   | ['^qp:1', 'jh3:1']       | 'x-1.jar'
        "a library endorsing its BOM, platform last"                  | ['jd:1', '^qp:1']        | 'x-1.jar'
        "a library endorsing its BOM, platform first"                 | ['^qp:1', 'jd:1']        | 'x-1.jar'
        "a library with a BOM and no cycle, platform last"            | ['jh2:1', '^qp:1']       | 'x-1.jar'
        "a three-node cycle, platform last"                           | ['h1:1', '^qp:1']        | 'x-1.jar'
        "a three-node cycle, platform first"                          | ['^qp:1', 'h1:1']        | 'x-1.jar'
        "a self-loop, platform last"                                  | ['s1:1', '^qp:1']        | 'x-1.jar'
        "a self-loop, platform first"                                 | ['^qp:1', 's1:1']        | 'x-1.jar'
        "a cycle entered at both nodes, platform last"                | ['e1:1', 'e2:1', '^qp:1'] | 'x-1.jar'
        "a cycle entered at both nodes, platform first"               | ['^qp:1', 'e1:1', 'e2:1'] | 'x-1.jar'
        "a cycle below the declarer"                                  | ['a5:1']                 | 'x-1.jar'
        "a cycle through the declarer"                                | ['a7:1']                 | 'x-1.jar'
        "a two-node cycle entered at the declarer"                    | ['cc:1']                 | 'x-1.jar'
    }

    def "fails on a conflict with #description"() {
        given:
        root.each { String dependency ->
            buildFile << """
                dependencies { conf("org:$dependency") }
            """
        }

        when:
        fails("resolve")

        then:
        failure.assertHasCause("Cannot find a version of 'org:x' that satisfies the version constraints")

        where:
        description                                                    | root
        "a platform that is not endorsed"                             | ['ql:1', 'qp:1']
        "a two-node cycle entered at the requester"                   | ['ca:1']
        "a cycle below the declarer, also reached from outside"       | ['a5:1', 'c6:1']
    }

    private void publish(Map<String, List<String>> modules) {
        modules.each { String key, List<String> dependencies ->
            def (String name, String version) = key.tokenize(":")
            def module = mavenRepo.module("org", name, version).withModuleMetadata()
            dependencies.each { String dependency ->
                boolean constraint = dependency.startsWith("~")
                boolean endorsing = dependency.startsWith("^")
                def (String target, String targetVersion) = dependency.replaceAll("^[~^]", "").tokenize(":")
                Map<String, Object> attributes = [group: "org", artifact: target, version: targetVersion.replace("!", "")]
                if (targetVersion.startsWith("!")) {
                    attributes.strictly = targetVersion.substring(1)
                }
                if (endorsing) {
                    attributes.endorseStrictVersions = true
                }
                if (constraint) {
                    module.dependencyConstraint(attributes, mavenRepo.module("org", target, attributes.version as String))
                } else {
                    module.dependsOn(attributes, mavenRepo.module("org", target, attributes.version as String))
                }
            }
            module.publish()
        }
    }
}
