/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.integtests.resolve.maven

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.fixtures.RequiredFeatures
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest
import spock.lang.Issue

@RequiredFeatures([
    @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "maven"),
    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "false")
] )
class MavenPomExcludeResolveIntegrationTest extends AbstractModuleDependencyResolveTest {

    @Issue("https://issues.gradle.org/browse/GRADLE-3243")
    def "wildcard exclude of group and module results in non-transitive dependency"() {
        given:
        repository {
            'g1:excluded1:2.0'()
            'g1:m1:1.5' {
                dependsOn('g1:excluded1:2.0')
            }
            'g1:m2:1.2' {
                dependsOn(group:'g1', artifact:'m1', version:'1.5', exclusions: [[group: '*', module: '*']])
            }
        }

        and:
        buildFile << """
dependencies {
    conf "g1:m2:1.2"
}
"""

        and:
        repositoryInteractions {
            'g1:m2:1.2' { expectResolve() }
            'g1:m1:1.5' { expectResolve() }
        }

        when:
        succeeds "checkDep"

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("g1:m2:1.2") {
                    module("g1:m1:1.5")
                }
            }
        }
    }

    def "can exclude transitive dependencies"() {
        given:
        repository {
            'g1:excluded1:2.0'()
            'g1:excluded2:2.0'()
            'g1:m1:1.5'()
            'g1:m2:1.5' {
                dependsOn('g1:excluded1:2.0')
                dependsOn('g1:excluded2:2.0')
                dependsOn('g1:m1:1.5')
            }
            'g1:m3:1.2' {
                dependsOn(group:'g1', artifact:'m2', version:'1.5',
                    exclusions: [[group: 'g1', module: 'excluded1'],
                                 [group: 'g1', module: 'excluded2']])
            }
        }

        and:
        buildFile << """
dependencies { conf 'g1:m3:1.2' }
"""

        and:
        repositoryInteractions {
            'g1:m3:1.2' { expectResolve() }
            'g1:m2:1.5' { expectResolve() }
            'g1:m1:1.5' { expectResolve() }
        }

        when:
        run "checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("g1:m3:1.2") {
                    module("g1:m2:1.5") {
                        module("g1:m1:1.5")
                    }
                }
            }
        }
    }
}
