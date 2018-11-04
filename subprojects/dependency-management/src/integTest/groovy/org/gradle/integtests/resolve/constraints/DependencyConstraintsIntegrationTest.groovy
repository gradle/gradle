/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.integtests.resolve.constraints

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import spock.lang.Issue

/**
 * This is a variation of {@link PublishedDependencyConstraintsIntegrationTest} that tests dependency constraints
 * declared in the build script (instead of published)
 */
class DependencyConstraintsIntegrationTest extends AbstractIntegrationSpec {
    private final ResolveTestFixture resolve = new ResolveTestFixture(buildFile, "conf").expectDefaultConfiguration("runtime")

    def setup() {
        settingsFile << "rootProject.name = 'test'"
        resolve.prepare()
        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
        """
        resolve.addDefaultVariantDerivationStrategy()
    }

    void "dependency constraint is not included in resolution without a hard dependency"() {
        given:
        mavenRepo.module("org", "foo", '1.0').publish()

        buildFile << """
            dependencies {
                constraints {
                    conf 'org:foo:1.0'
                }
            }
        """

        when:
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") { }
        }
    }

    void "dependency constraint is included into the result of resolution when a hard dependency is also added"() {
        given:
        mavenRepo.module("org", "foo", '1.1').publish()

        buildFile << """
            dependencies {
                conf 'org:foo'
                constraints {
                    conf 'org:foo:1.1'
                }
            }
        """

        when:
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:foo","org:foo:1.1")
                constraint("org:foo:1.1", "org:foo:1.1")
            }
        }
    }

    void "dependency constraint can be used to declare incompatibility"() {
        given:
        mavenRepo.module("org", "foo", '1.1').publish()
        mavenRepo.module("org", "bar", '1.0')
            .dependsOn('org', 'foo', '1.1')
            .publish()

        buildFile << """
            dependencies {
                conf 'org:bar:1.0'
                constraints {
                    conf('org:foo') {
                        version { rejectAll() }
                    }
                }
            }
        """

        when:
        fails 'checkDeps'

        then:
        failure.assertHasCause("""Module 'org:foo' has been rejected:
   Dependency path ':test:unspecified' --> 'org:bar:1.0' --> 'org:foo:1.1'
   Constraint path ':test:unspecified' --> 'org:foo:{reject +}'""")
    }

    void "dependency constraint is included into the result of resolution when a hard dependency is also added transitively"() {
        given:
        mavenRepo.module("org", "foo", '1.0').publish()
        mavenRepo.module("org", "foo", '1.1').publish()
        mavenRepo.module("org", "bar", "1.0").dependsOn("org", "foo", "1.0").publish()

        buildFile << """
            dependencies {
                conf 'org:bar:1.0'
                constraints {
                    conf 'org:foo:1.1'
                }
            }
        """

        when:
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:bar:1.0") {
                    edge("org:foo:1.0", "org:foo:1.1").byConflictResolution("between versions 1.0 and 1.1")
                }
                constraint("org:foo:1.1", "org:foo:1.1")
            }
        }
    }

    /**
     * Test demonstrates a bug in resolution of constraints, when real dependency is evicted via conflict resolution.
     */
    @Issue("gradle/gradle#4610")
    void "dependency constraint should not preserve hard dependency for evicted dependency"() {
        given:
        // "org:foo:1.0" -> "org:baz:1.0" -> "org:baz-transitive:1.0"
        mavenRepo.module("org", "foo", '1.0')
            .dependsOn("org", "baz", '1.0').publish()
        mavenRepo.module("org", "baz", '1.0')
            .dependsOn("org", "baz-transitive", "1.0").publish()
        mavenRepo.module("org", "baz-transitive", "1.0").publish()

        // "org:bar:1.0" -> "org:foo:1.1" (no further transitive deps)
        mavenRepo.module("org", "bar", "1.0")
            .dependsOn("org", "foo", '1.1').publish()
        mavenRepo.module("org", "foo", '1.1').publish()

        buildFile << """
            dependencies {
                conf 'org:foo:1.0' // Would bring in 'baz' and 'baz-transitive' (but will be evicted)
                conf 'org:bar:1.0' // Brings in 'foo:1.1'
                
                constraints {
                    conf 'org:baz:1.0' // Should not bring in 'baz' when 'foo:1.0' is evicted
                }
            }
            task resolve(type: Sync) {
                from configurations.conf
                into 'lib'
            }
        """

        when:
        run ':resolve'

        then:
        file('lib').assertHasDescendants("bar-1.0.jar", "foo-1.1.jar")

        /*
         * Cannot use ResolveTestFixture because the end graph cannot be handled
         *   - The edge ":test:" -> "org:baz:1.0" is included in graph
         *   - But "org:baz:1.0" is NOT in the "first level dependencies"
        when:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:foo:1.0","org:foo:1.1").byConflictResolution()
                module("org:bar:1.0") {
                    module("org:foo:1.1")
                }
                // BUG: This module should not be included, but it is
                module("org:baz:1.0") {
                    module("org:baz-transitive:1.0")
                }
            }
        }
         */
    }

    void "range resolution kicks in with dependency constraints"() {
        given:
        mavenRepo.module("org", "foo", '1.0').publish()
        mavenRepo.module("org", "foo", '1.1').publish()
        mavenRepo.module("org", "foo", '1.2').publish()
        mavenRepo.module("org", "bar", "1.0").dependsOn("org", "foo", "[1.0,1.2]").publish()


        buildFile << """
            dependencies {
                conf 'org:bar:1.0'
                constraints {
                    conf('org:foo:[1.0,1.1]') {
                        because 'tested versions'
                    }
                }
            }
        """

        when:
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:bar:1.0") {
                    edge("org:foo:[1.0,1.2]", "org:foo:1.1").byConstraint('didn\'t match version 1.2 because tested versions')
                }
                constraint("org:foo:[1.0,1.1]", "org:foo:1.1").byConstraint('didn\'t match version 1.2 because tested versions')
            }
        }
    }

    void "transitive dependencies of an dependency constraint do not participate in conflict resolution if it is not included elsewhere"() {
        given:
        mavenRepo.module("org", "foo", '1.0').dependsOn('org', 'bar', '1.1').publish()
        mavenRepo.module("org", "bar", '1.0').publish()
        mavenRepo.module("org", "bar", '1.1').publish()

        buildFile << """
            dependencies {
                conf 'org:bar:1.0'
                constraints {
                    conf 'org:foo:1.0'
                }
            }
        """

        when:
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:bar:1.0")
            }
        }
    }

    void "dependency constraints on substituted module is recognized properly"() {
        given:
        mavenRepo.module("org", "foo", '1.0').publish()
        mavenRepo.module("org", "foo", '1.1').publish()
        mavenRepo.module("org", "bar", '1.1').publish()

        buildFile << """
            configurations {
                conf {
                   resolutionStrategy.dependencySubstitution {
                      all { DependencySubstitution dependency ->
                         if (dependency.requested.module == 'bar') {
                            dependency.useTarget dependency.requested.group + ':foo:' + dependency.requested.version
                         }
                      }
                   }
                }
            }
            dependencies {
                conf 'org:foo:1.0'
                constraints {
                    conf 'org:bar:1.1'
                }
            }
        """

        when:
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                constraint("org:bar:1.1", "org:foo:1.1").selectedByRule()
                edge("org:foo:1.0", "org:foo:1.1").byConflictResolution("between versions 1.0 and 1.1")
            }
        }
    }

    void "dependency constraints are inherited"() {
        given:
        mavenRepo.module("org", "foo", '1.0').publish()
        mavenRepo.module("org", "foo", '1.1').publish()

        buildFile << """
            configurations {
                confSuper
                conf { extendsFrom confSuper }
            }
            dependencies {
                conf 'org:foo:1.0'

                constraints {
                    confSuper 'org:foo:1.1'
                }
            }
        """

        when:
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:foo:1.0", "org:foo:1.1").byConflictResolution("between versions 1.0 and 1.1")
                constraint("org:foo:1.1", "org:foo:1.1")
            }
        }
    }

    void "dependency constraints defined for a configuration are applied when resolving that configuration as part of a project dependency"() {
        given:
        mavenRepo.module("org", "foo", '1.0').publish()
        mavenRepo.module("org", "foo", '1.1').publish()

        settingsFile << """
            include 'b'
        """
        buildFile << """
            dependencies {
                conf project(path: ':b', configuration: 'conf')
                conf 'org:foo:1.0'
            }
            
            project(':b') {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                configurations {
                    conf
                }
                dependencies {
                    constraints {
                        conf('org:foo:1.1') {
                            because 'transitive dependency constraint'
                        }
                    }
                }
            }
        """

        when:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:foo:1.0", "org:foo:1.1").byConflictResolution("between versions 1.0 and 1.1").byConstraint('transitive dependency constraint')
                project(":b", "test:b:") {
                    configuration = "conf"
                    noArtifacts()
                    constraint("org:foo:1.1", "org:foo:1.1").byConstraint('transitive dependency constraint')
                }
            }
        }
    }

    void "dependency constraints defined for a build are applied when resolving a configuration that uses that build as an included build"() {
        given:
        resolve.expectDefaultConfiguration('default')
        mavenRepo.module("org", "foo", '1.0').publish()
        mavenRepo.module("org", "foo", '1.1').publish()

        file('includeBuild/settings.gradle') << "rootProject.name = 'included'"
        file('includeBuild/build.gradle') << """
            group "org"
            version "1.0"
            
            configurations {
                conf
                'default' { extendsFrom conf }
            }
            dependencies {
                constraints {
                    conf 'org:foo:1.1'
                }
            }
        """

        settingsFile << """
            includeBuild 'includeBuild'
        """
        buildFile << """
            dependencies {
                conf 'org:included:1.0'
                conf 'org:foo:1.0'
            }
        """

        when:
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:foo:1.0", "org:foo:1.1:runtime").byConflictResolution("between versions 1.0 and 1.1")
                edge("org:included:1.0", "project :included", "org:included:1.0") {
                    noArtifacts()
                    constraint("org:foo:1.1", "org:foo:1.1")
                }.compositeSubstitute()
            }
        }
    }

    @Issue("gradle/gradle#4609")
    def "dependency constraint does not invalidate excludes defined on hard dependency"() {
        given:
        mavenRepo.module("org", "baz", "1.0").publish()
        mavenRepo.module("org", "foo", '1.0').dependsOn("org", "baz", '1.0').publish()

        buildFile << """
            dependencies {
                conf('org:foo') {
                    exclude group: 'org', module: 'baz'
                }
                
                constraints {
                    conf 'org:foo:1.0'
                }
            }
        """

        when:
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:foo", "org:foo:1.0")
                constraint("org:foo:1.0")
            }
        }
    }

    void "dependency constraints should not pull in additional artifacts"() {
        given:
        mavenRepo.module("org", "foo", '1.0').artifact(classifier: 'shaded').publish()
        mavenRepo.module("org", "foo", '1.1').artifact(classifier: 'shaded').publish()

        buildFile << """
            dependencies {
                conf 'org:foo:1.0:shaded'
                constraints {
                    conf 'org:foo:1.1'
                }
            }
        """

        when:
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:foo:1.0","org:foo:1.1")
                constraint("org:foo:1.1", "org:foo:1.1") {
                    artifact(classifier: 'shaded')
                }
            }
        }
    }

    void "dependency constraints should not pull in additional artifacts for transitive dependencies"() {
        given:
        def foo11 = mavenRepo.module("org", "foo", '1.0').artifact(classifier: 'shaded').publish()
        mavenRepo.module("org", "foo", '1.1').artifact(classifier: 'shaded').publish()
        mavenRepo.module("org", "bar", '1.0').dependsOn(classifier: 'shaded', foo11).publish()

        buildFile << """
            dependencies {
                conf 'org:bar:1.0'
                constraints {
                    conf 'org:foo:1.1'
                }
            }
        """

        when:
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                constraint("org:foo:1.1", "org:foo:1.1") {
                    artifact(classifier: 'shaded')
                }
                module("org:bar:1.0") {
                    edge("org:foo:1.0","org:foo:1.1")
                }
            }
        }
    }

    void 'dependency updated through constraints has its transitive dependencies'() {
        given:
        def foo10 = mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'bar', '1.0').publish()
        mavenRepo.module('org', 'bar', '1.1').dependsOn(foo10).publish()

        buildFile << """
            dependencies {
                conf 'org:bar:1.0'
                constraints {
                    conf 'org:bar:1.1'
                }
            }
        """

        when:
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                constraint("org:bar:1.1", "org:bar:1.1") {
                    edge('org:foo:1.0', 'org:foo:1.0')
                }
                edge('org:bar:1.0', 'org:bar:1.1')
            }
        }
    }

    void 'dependency without version updated through constraints has its transitive dependencies'() {
        given:
        def foo10 = mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'bar', '1.0').publish()
        mavenRepo.module('org', 'bar', '1.1').dependsOn(foo10).publish()

        buildFile << """
            dependencies {
                constraints {
                    conf 'org:bar:1.1'
                }
                conf 'org:bar'
            }
        """

        when:
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                constraint("org:bar:1.1", "org:bar:1.1") {
                    edge('org:foo:1.0', 'org:foo:1.0')
                }
                edge('org:bar', 'org:bar:1.1')
            }
        }
    }
}
