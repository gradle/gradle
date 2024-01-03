/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.integtests.resolve.reproducibility

import groovy.transform.CompileStatic
import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest

class FailOnDynamicVersionsResolveIntegrationTest extends AbstractModuleDependencyResolveTest {

    String getNotation() {
        "failOnDynamicVersions"
    }

    def setup() {
        buildFile << """
            configurations.all {
                resolutionStrategy.$notation()
            }
        """
    }

    def "does not fail with a project and direct dependency with non dynamic version"() {
        createDirs("other")
        settingsFile << """
            include("other")
"""
        buildFile << """
            dependencies {
                conf(project(':other'))
                conf 'org:test:1.0'
            }

            project(':other') {
                configurations.create('default')
            }
"""
        repository {
            'org:test:1.0'()
        }

        when:
        repositoryInteractions {
            'org:test:1.0' {
                expectResolve()
            }
        }
        succeeds ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                project(":other", "test:other:unspecified") {
                    configuration('default')
                    noArtifacts()
                }
                module('org:test:1.0')
            }
        }
    }

    def "fails to resolve a direct dependency using a dynamic version"() {
        buildFile << """
            dependencies {
                conf 'org:test:1.+'
            }
        """

        repository {
            'org:test:1.0'()
        }

        when:
        repositoryInteractions {
            'org:test' {
                expectVersionListing()
            }
            'org:test:1.0' {
                expectGetMetadata()
            }
        }
        fails ':checkDeps'

        then:
        failure.assertHasCause("Could not resolve org:test:1.+: Resolution strategy disallows usage of dynamic versions")
    }

    def "fails to resolve a transitive dependency which uses a dynamic version"() {
        buildFile << """
            dependencies {
                conf 'org:test:1.0'
            }
        """

        repository {
            'org:test:1.0' {
                dependsOn 'org:transitive:1.+'
            }
            'org:transitive:1.0'()
        }

        when:
        repositoryInteractions {
            'org:test:1.0' {
                expectGetMetadata()
            }
            'org:transitive' {
                expectVersionListing()
                '1.0' {
                    expectGetMetadata()
                }
            }

        }
        fails ':checkDeps'

        then:
        failure.assertHasCause("Could not resolve org:transitive:1.+: Resolution strategy disallows usage of dynamic versions")
    }

    def "fails if a transitive dynamic selector participates in selection"() {
        buildFile << """
            dependencies {
                conf 'org:test:1.0'
                conf 'org:testB:1.0'
            }
        """

        repository {
            'org:test:1.0' {
                dependsOn 'org:transitive:1.+'
            }
            'org:testB:1.0' {
                dependsOn 'org:transitive:1.0'
            }
            'org:transitive:1.0'()
        }

        when:
        repositoryInteractions {
            'org:test:1.0' {
                expectGetMetadata()
            }
            'org:testB:1.0' {
                expectGetMetadata()
            }
            'org:transitive' {
                expectVersionListing()
                '1.0' {
                    expectGetMetadata()
                }
            }
        }
        fails ':checkDeps'

        then:
        failure.assertHasCause("Could not resolve org:transitive:1.+: Resolution strategy disallows usage of dynamic versions")
    }

    def "passes if a transitive dynamic selector doesn't participate in selection"() {
        buildFile << """
            dependencies {
                conf 'org:test:1.0'
                conf 'org:testB:1.0'
                conf 'org:testC:1.0'
            }

        """

        repository {
            'org:test:1.0' {
                dependsOn 'org:transitive:1.+'
            }
            'org:test:1.1' {
                dependsOn 'org:transitive:1.0'
            }
            'org:testB:1.0' {
                dependsOn 'org:transitive:1.0'
            }
            'org:testC:1.0' {
                dependsOn 'org:test:1.1'
            }
            'org:transitive:1.0'()
        }

        when:
        repositoryInteractions {
            'org:test' {
                '1.0' {
                    expectGetMetadata()
                }
                '1.1' {
                    expectResolve()
                }
            }
            'org:testB:1.0' {
                expectResolve()
            }
            'org:testC:1.0' {
                expectResolve()
            }
            'org:transitive' {
                expectVersionListing()
                '1.0' {
                    expectResolve()
                }
            }
        }
        succeeds ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge('org:test:1.0', 'org:test:1.1') {
                    byConflictResolution("between versions 1.1 and 1.0")
                    module('org:transitive:1.0')
                }
                module('org:testB:1.0') {
                    module('org:transitive:1.0')
                }
                module('org:testC:1.0') {
                    module('org:test:1.1')
                }
            }
        }
    }

    def "doesn't fail if selection within range"() {
        buildFile << """
            dependencies {
               conf 'org:test:[1.0, 2.0['
               conf 'org:testB:1.0'
            }
        """
        repository {
            'org:test' {
                '1.0'()
                '1.1'()
                '1.2'()
            }
            'org:testB:1.0' {
                dependsOn 'org:test:1.1' // solution within range
            }
        }
        when:
        repositoryInteractions {
            'org:test' {
                expectVersionListing()
                '1.2' {
                    expectGetMetadata()
                }
                '1.1' {
                    expectResolve()
                }
            }
            'org:testB:1.0' {
                expectResolve()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge('org:test:[1.0, 2.0[', 'org:test:1.1')
                module('org:testB:1.0') {
                    module('org:test:1.1')
                }
            }
        }
    }

    def "fails if exact selector is below the range"() {
        buildFile << """
            dependencies {
               conf 'org:test:[1.2, 2.0['
               conf 'org:test:1.0'
            }
        """
        repository {
            'org:test' {
                '1.0'()
                '1.1'()
                '1.2'()
            }
        }
        when:
        repositoryInteractions {
            'org:test' {
                expectVersionListing()
                '1.2' {
                    expectGetMetadata()
                }
            }
        }
        fails ':checkDeps'

        then:
        failure.assertHasCause("Could not resolve org:test:[1.2, 2.0[: Resolution strategy disallows usage of dynamic versions")
    }

    def "fails with combination of selectors (#selector1 and #selector2)"() {
        buildFile << """
            dependencies {
               conf 'org:test:$selector1'
               conf 'org:testB:1.0'
            }
        """
        repository {
            'org:test' {
                '1.0'()
                '1.1'()
                '1.2'()
            }
            'org:testB:1.0' {
                dependsOn "org:test:$selector2"
            }
        }
        when:
        repositoryInteractions {
            'org:test' {
                expectVersionListing()
                '1.2' {
                    expectGetMetadata()
                }
                '1.0' {
                    allowAll()
                }
                '1.1' {
                    allowAll()
                }
            }
            'org:testB:1.0' {
                expectGetMetadata()
            }
        }
        fails ':checkDeps'

        then:
        failure.assertHasCause("Could not resolve org:test:$selector1: Resolution strategy disallows usage of dynamic versions")
        failure.assertHasCause("Could not resolve org:test:$selector2: Resolution strategy disallows usage of dynamic versions")

        where:
        selector1        | selector2
        '[1.0, 2.0['     | '[1.0, 1.5['
        latestNotation() | '1.1'
        '1.+'            | '[1.0, 1.5'
        '[1.0, 1.5['     | '[1.0, 2.0['
        '1.1'            | latestNotation()
        '[1.0, 1.5['     | '1.+'
        latestNotation() | latestNotation()
        '1.+'            | '+'
    }

    @CompileStatic
    static Closure<String> latestNotation() {
        return new Closure(this) {
            @Override
            Object call() {
                return GradleMetadataResolveRunner.useIvy() ? "latest.integration" : "latest.release"
            }

            @Override
            String toString() {
                return GradleMetadataResolveRunner.useIvy() ? "latest.integration" : "latest.release"
            }
        }
    }
}
