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
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture

/**
 * A strict version applies to every node that all paths from the root reach through its declarer, or through a
 * node that endorses the declarer. The result must not depend on the order in which dependencies are declared,
 * nor on cycles below the declarer.
 */
class StrictVersionsPropagationIntegrationTest extends AbstractIntegrationSpec {
    def resolve = new ResolveTestFixture(testDirectory)

    def setup() {
        settingsFile << """
            rootProject.name = "test"
        """
        buildFile << """
            ${mavenTestRepository()}
            configurations {
                conf
            }
            ${resolve.configureProject("conf")}
        """
    }

    def "an endorsed platform applies to a library declared #order it"() {
        given:
        graph(
            'lib:1': ['x:2'],
            'platform:1': ['~x:!1'],
        )
        declare(platformFirst ? ['^platform:1', 'lib:1'] : ['lib:1', '^platform:1'])

        when:
        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:lib:1") {
                    edge("org:x:2", "org:x:1") {
                        byAncestor()
                        byConstraint()
                        notRequested()
                    }
                }
                module("org:platform:1") {
                    constraint("org:x:{strictly 1}", "org:x:1")
                }
            }
        }

        where:
        platformFirst << [true, false]
        order = platformFirst ? "after" : "before"
    }

    def "an endorsed platform applies to a transitive library declared #order it"() {
        given:
        graph(
            'lib:1': ['mid:1'],
            'mid:1': ['x:2'],
            'platform:1': ['~x:!1'],
        )
        declare(platformFirst ? ['^platform:1', 'lib:1'] : ['lib:1', '^platform:1'])

        when:
        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:lib:1") {
                    module("org:mid:1") {
                        edge("org:x:2", "org:x:1") {
                            byAncestor()
                            byConstraint()
                            notRequested()
                        }
                    }
                }
                module("org:platform:1") {
                    constraint("org:x:{strictly 1}", "org:x:1")
                }
            }
        }

        where:
        platformFirst << [true, false]
        order = platformFirst ? "after" : "before"
    }

    def "an endorsed platform applies to a library in a cycle with its BOM through a #kind, platform #order"() {
        given:
        graph(
            'lib:1': ['x:2', 'bom:1'],
            'bom:1': [back],
            'platform:1': ['~x:!1'],
        )
        declare(platformFirst ? ['^platform:1', 'lib:1'] : ['lib:1', '^platform:1'])

        when:
        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:lib:1") {
                    if (kind == "constraint") {
                        byConstraint()
                    }
                    edge("org:x:2", "org:x:1") {
                        byAncestor()
                        byConstraint()
                        notRequested()
                    }
                    module("org:bom:1") {
                        if (kind == "constraint") {
                            constraint("org:lib:1")
                        } else {
                            module("org:lib:1")
                        }
                    }
                }
                module("org:platform:1") {
                    constraint("org:x:{strictly 1}", "org:x:1")
                }
            }
        }

        where:
        kind         | back     | platformFirst
        "constraint" | '~lib:1' | true
        "constraint" | '~lib:1' | false
        "dependency" | 'lib:1'  | true
        "dependency" | 'lib:1'  | false
        order = platformFirst ? "first" : "last"
    }

    def "an endorsed platform applies to a library with a BOM that only constrains x, platform #order"() {
        given:
        graph(
            'lib:1': ['x:2', 'bom:1'],
            'bom:1': ['~x:2'],
            'platform:1': ['~x:!1'],
        )
        declare(platformFirst ? ['^platform:1', 'lib:1'] : ['lib:1', '^platform:1'])

        when:
        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:lib:1") {
                    edge("org:x:2", "org:x:1") {
                        byAncestor()
                        byConstraint()
                        notRequested()
                    }
                    module("org:bom:1") {
                        constraint("org:x:2", "org:x:1")
                    }
                }
                module("org:platform:1") {
                    constraint("org:x:{strictly 1}", "org:x:1")
                }
            }
        }

        where:
        platformFirst << [true, false]
        order = platformFirst ? "first" : "last"
    }

    def "an endorsed platform applies to a library endorsing its BOM, which constrains the library back, platform #order"() {
        given:
        graph(
            'lib:1': ['x:2', '^bom:1'],
            'bom:1': ['~lib:1', '~x:2'],
            'platform:1': ['~x:!1'],
        )
        declare(platformFirst ? ['^platform:1', 'lib:1'] : ['lib:1', '^platform:1'])

        when:
        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:lib:1") {
                    byConstraint()
                    edge("org:x:2", "org:x:1") {
                        byAncestor()
                        byConstraint()
                        notRequested()
                    }
                    module("org:bom:1") {
                        constraint("org:lib:1")
                        constraint("org:x:2", "org:x:1")
                    }
                }
                module("org:platform:1") {
                    constraint("org:x:{strictly 1}", "org:x:1")
                }
            }
        }

        where:
        platformFirst << [true, false]
        order = platformFirst ? "first" : "last"
    }

    def "an endorsed platform applies to a three-node cycle, platform #order"() {
        given:
        graph(
            'c1:1': ['c2:1'],
            'c2:1': ['c3:1'],
            'c3:1': ['c1:1', 'x:2'],
            'platform:1': ['~x:!1'],
        )
        declare(platformFirst ? ['^platform:1', 'c1:1'] : ['c1:1', '^platform:1'])

        when:
        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:c1:1") {
                    module("org:c2:1") {
                        module("org:c3:1") {
                            module("org:c1:1")
                            edge("org:x:2", "org:x:1") {
                                byAncestor()
                                byConstraint()
                                notRequested()
                            }
                        }
                    }
                }
                module("org:platform:1") {
                    constraint("org:x:{strictly 1}", "org:x:1")
                }
            }
        }

        where:
        platformFirst << [true, false]
        order = platformFirst ? "first" : "last"
    }

    def "an endorsed platform applies to a self-loop, platform #order"() {
        given:
        graph(
            's:1': ['s:1', 'x:2'],
            'platform:1': ['~x:!1'],
        )
        declare(platformFirst ? ['^platform:1', 's:1'] : ['s:1', '^platform:1'])

        when:
        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:s:1") {
                    module("org:s:1")
                    edge("org:x:2", "org:x:1") {
                        byAncestor()
                        byConstraint()
                        notRequested()
                    }
                }
                module("org:platform:1") {
                    constraint("org:x:{strictly 1}", "org:x:1")
                }
            }
        }

        where:
        platformFirst << [true, false]
        order = platformFirst ? "first" : "last"
    }

    def "an endorsed platform applies to a cycle entered at both of its nodes, platform #order"() {
        given:
        graph(
            'c1:1': ['c2:1'],
            'c2:1': ['c1:1', 'x:2'],
            'platform:1': ['~x:!1'],
        )
        declare(platformFirst ? ['^platform:1', 'c1:1', 'c2:1'] : ['c1:1', 'c2:1', '^platform:1'])

        when:
        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:c1:1") {
                    module("org:c2:1") {
                        module("org:c1:1")
                        edge("org:x:2", "org:x:1") {
                            byAncestor()
                            byConstraint()
                            notRequested()
                        }
                    }
                }
                module("org:c2:1")
                module("org:platform:1") {
                    constraint("org:x:{strictly 1}", "org:x:1")
                }
            }
        }

        where:
        platformFirst << [true, false]
        order = platformFirst ? "first" : "last"
    }

    def "a strict version applies to a cycle below its declarer"() {
        given:
        graph(
            'a:1': ['p:1', 'x:!1'],
            'p:1': ['q:1'],
            'q:1': ['p:1', 'x:2'],
        )
        declare(['a:1'])

        when:
        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:a:1") {
                    module("org:p:1") {
                        module("org:q:1") {
                            module("org:p:1")
                            edge("org:x:2", "org:x:1") {
                                byAncestor()
                            }
                        }
                    }
                    edge("org:x:{strictly 1}", "org:x:1")
                }
            }
        }
    }

    def "a strict version applies to a cycle through its declarer, entered at the declarer"() {
        given:
        graph(
            'a:1': ['x:!1', 'r:1'],
            'r:1': ['a:1', 'x:2'],
        )
        declare(['a:1'])

        when:
        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:a:1") {
                    edge("org:x:{strictly 1}", "org:x:1")
                    module("org:r:1") {
                        module("org:a:1")
                        edge("org:x:2", "org:x:1") {
                            byAncestor()
                        }
                    }
                }
            }
        }
    }

    def "fails on a conflict with #description"() {
        given:
        graph(modules)
        declare(entries)

        when:
        fails(":checkDeps")

        then:
        failure.assertHasCause("Cannot find a version of 'org:x' that satisfies the version constraints")

        where:
        description                                                | modules                                                                       | entries
        // The strict version of the platform only applies below it, and the library is not below it
        "a platform that is not endorsed"                          | ['lib:1': ['x:2'], 'platform:1': ['~x:!1']]                                    | ['lib:1', 'platform:1']
        // The requester is reached without going through the declarer
        "a two-node cycle entered at the requester"                | ['a:1': ['c:1', 'x:2'], 'c:1': ['a:1', 'x:!1']]                                | ['a:1']
        "a cycle below the declarer, also reached from outside it" | ['a:1': ['p:1', 'x:!1'], 'p:1': ['q:1'], 'q:1': ['p:1', 'x:2'], 'o:1': ['q:1']] | ['a:1', 'o:1']
    }

    /**
     * Publishes each module version with what it depends on. "~" marks a constraint, "^" an endorsing dependency,
     * and "!" a strict version. Versions 1 and 2 of x always exist.
     */
    private void graph(Map<String, List<String>> modules) {
        (['x:1': [], 'x:2': []] + modules).each { String key, List<String> dependencies ->
            def (String name, String version) = key.tokenize(":")
            def module = mavenRepo.module("org", name, version).withModuleMetadata()
            dependencies.each { String dependency ->
                boolean constraint = dependency.startsWith("~")
                boolean endorsing = dependency.startsWith("^")
                def (String target, String targetVersion) = dependency.replaceAll("^[~^]", "").tokenize(":")
                Map<String, Object> attributes = [version: targetVersion.replace("!", "")]
                if (targetVersion.startsWith("!")) {
                    attributes.strictly = targetVersion.substring(1)
                }
                if (endorsing) {
                    attributes.endorseStrictVersions = true
                }
                def targetModule = mavenRepo.module("org", target, attributes.version as String)
                if (constraint) {
                    module.dependencyConstraint(attributes, targetModule)
                } else {
                    module.dependsOn(attributes, targetModule)
                }
            }
            module.publish()
        }
    }

    /**
     * Declares the given dependencies of the root, in order. "^" marks one that endorses the strict versions of its target.
     */
    private void declare(List<String> dependencies) {
        dependencies.each { String dependency ->
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
    }
}
