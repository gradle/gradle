/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest

class PublishedDependencyConstraintsIntegrationTest extends AbstractModuleDependencyResolveTest {

    boolean featureAvailable() {
        gradleMetadataPublished
    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value="false")
    void "published dependency constraint is ignored when Gradle module metadata is not available"() {
        given:
        repository {
            'org:foo:1.0'()
            'org:foo:1.1'()
            'org:first-level:1.0' {
                constraint 'org:foo:1.1'
            }
        }

        buildFile << """
            dependencies {
                conf 'org:first-level:1.0'
                conf 'org:foo:1.0'
            }
        """

        repositoryInteractions {
            'org:foo:1.0' {
                allowAll()
                expectGetArtifact()
            }
            'org:first-level:1.0' {
                allowAll()
                expectGetArtifact()
            }
        }

        when:
        run 'checkDeps'

        then:
        def expectedVariant = useMaven() ? 'runtime' : 'default'
        resolve.expectDefaultConfiguration(expectedVariant).expectGraph {
            root(":", ":test:") {
                module("org:first-level:1.0")
                module("org:foo:1.0")
            }
        }
    }

    void "dependency constraint is not included in resolution without a hard dependency"() {
        given:
        def available = featureAvailable()
        repository {
            'org:foo:1.0'()
            'org:first-level:1.0' {
                constraint 'org:foo:1.0'
            }
        }

        buildFile << """
            dependencies {
                conf 'org:first-level:1.0'
            }
        """

        repositoryInteractions {
            'org:first-level:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }

        when:
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:first-level:1.0")
            }
        }
    }

    void "dependency constraint is included into the result of resolution when a hard dependency is also added"() {
        given:
        def available = featureAvailable()
        repository {
            'org:foo:1.0'()
            'org:foo:1.1'()
            'org:first-level:1.0' {
                constraint(group:'org', artifact:'foo', version:'1.1', reason:'published dependency constraint')
            }
        }

        buildFile << """
            dependencies {
                conf 'org:first-level:1.0'
                conf 'org:foo:1.0'
            }
        """

        repositoryInteractions {
            'org:foo:1.0' {
                expectGetMetadata()
                if (!available) {
                    expectGetArtifact()
                }
            }
            'org:foo:1.1' {
                if (available) {
                    expectGetMetadata()
                    expectGetArtifact()
                }
            }
            'org:first-level:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }

        when:
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:first-level:1.0") {
                    if (available) {
                        if (GradleMetadataResolveRunner.gradleMetadataPublished) {
                            constraint("org:foo:1.1", "org:foo:1.1").byConstraint('published dependency constraint')
                        } else {
                            constraint("org:foo:1.1", "org:foo:1.1")
                        }
                    }
                }
                if (available) {
                    edge("org:foo:1.0","org:foo:1.1").byConflictResolution("between versions 1.1 and 1.0")
                } else {
                    module("org:foo:1.0")
                }
            }
        }
    }

    void "dependency constraint is included into the result of resolution when a hard dependency is also added transitively"() {
        given:
        def available = featureAvailable()
        repository {
            'org:foo:1.0'()
            'org:foo:1.1'()
            'org:first-level1:1.0' {
                constraint 'org:foo:1.1'
            }
            'org:first-level2:1.0' {
                dependsOn 'org:foo:1.0'
            }
        }

        buildFile << """
            dependencies {
                conf 'org:first-level1:1.0'
                conf 'org:first-level2:1.0'
            }
        """

        repositoryInteractions {
            'org:foo:1.0' {
                if (!available) {
                    expectGetMetadata()
                    expectGetArtifact()
                }
            }
            'org:foo:1.1' {
                if (available) {
                    expectGetMetadata()
                    expectGetArtifact()
                }
            }
            'org:first-level1:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:first-level2:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }

        when:
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:first-level1:1.0") {
                    if (available) {
                        constraint("org:foo:1.1", "org:foo:1.1")
                    }
                }
                module("org:first-level2:1.0") {
                    if (available) {
                        edge("org:foo:1.0","org:foo:1.1").byConflictResolution("between versions 1.1 and 1.0")
                    } else {
                        module("org:foo:1.0")
                    }
                }
            }
        }
    }

    void "range resolution kicks in with dependency constraints"() {
        given:
        def available = featureAvailable()
        repository {
            'org:foo:1.0'()
            'org:foo:1.1'()
            'org:foo:1.2'()
            'org:bar:1.0' {
                dependsOn 'org:foo:[1.1,1.2]'
            }
            'org:first-level:1.0' {
                constraint 'org:foo:[1.0,1.1]'
            }
        }

        buildFile << """
            dependencies {
                conf('org:first-level:1.0')
                conf 'org:bar:1.0'
            }
        """

        repositoryInteractions {
            'org:foo' {
                expectGetMetadata()
            }
            'org:foo:1.2' {
                expectGetMetadata()
                if (!available) {
                    expectGetArtifact()
                }
            }
            'org:foo:1.1' {
                if (available) {
                    expectGetMetadata()
                    expectGetArtifact()
                }
            }
            'org:bar:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:first-level:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }

        when:
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:bar:1.0") {
                    if (available) {
                        edge("org:foo:[1.1,1.2]", "org:foo:1.1")
                    } else {
                        edge("org:foo:[1.1,1.2]", "org:foo:1.2")
                    }
                }
                module("org:first-level:1.0") {
                    if (available) {
                        constraint("org:foo:[1.0,1.1]", "org:foo:1.1")
                    }
                }
            }
        }
    }

    void "transitive dependencies of a dependency constraint do not participate in conflict resolution if it is not included elsewhere"() {
        given:
        def available = featureAvailable()
        repository {
            'org:foo:1.0' {
                dependsOn 'org:bar:1.1'
            }
            'org:bar:1.0'()
            'org:bar:1.1'()
            'org:first-level:1.0' {
                constraint 'org:foo:1.0'
            }
        }

        buildFile << """
            dependencies {
                conf 'org:first-level:1.0'
                conf 'org:bar:1.0'
            }
        """

        repositoryInteractions {
            'org:bar:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:first-level:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }

        when:
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:bar:1.0")
                module("org:first-level:1.0")
            }
        }
    }

    void "dependency constraint on substituted module is recognized properly"() {
        given:
        def available = featureAvailable()
        repository {
            'org:foo:1.0'()
            'org:foo:1.1'()
            'org:bar:1.0'()
            'org:bar:1.1'()
            'org:first-level:1.0' {
                dependsOn 'org:foo:1.0'
                constraint 'org:bar:1.1'
            }
        }

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
                conf 'org:first-level:1.0'
            }
        """

        repositoryInteractions {
            "org:foo:${available? '1.1' : '1.0'}" {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:first-level:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }

        when:
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:first-level:1.0") {
                    if (available) {
                        constraint("org:bar:1.1", "org:foo:1.1").selectedByRule()
                        edge("org:foo:1.0", "org:foo:1.1").byConflictResolution("between versions 1.1 and 1.0")
                    } else {
                        module("org:foo:1.0")
                    }
                }
            }
        }
    }


    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value="true")
    void "deferred selector still resolved when constraint disappears"() {
        repository {
            'org:bar:1.0'()
            'org:bar:1.1'()

            'org:other:1.0' {
                dependsOn 'org:bar:1.0'
                dependsOn 'org:weird:1.1'
            }

            // Version 1.0 has a constraint, 1.1 does not
            'org:weird:1.0' {
                constraint 'org:bar:1.1'
            }
            'org:weird:1.1'()
        }

        buildFile << """
dependencies {
    conf 'org:weird:1.0'
    conf 'org:other:1.0'
}
"""

        repositoryInteractions {
            'org:weird:1.0' {
                expectGetMetadata()
            }
            'org:weird:1.1' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:other:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:bar:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }

        when:
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                edge('org:weird:1.0', 'org:weird:1.1')
                module('org:other:1.0') {
                    module('org:bar:1.0')
                    module('org:weird:1.1')
                }
            }
        }
    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "maven")
    def "platform with constraint on lower version does not cause invalid edge to remain in graph"() {
        given:
        repository {
            'org:client:4.1.0'()
            'org:client:4.1.1'()

            'org:platform:1.0' {
                constraint 'org:client:4.1.0'
                asPlatform()
            }

            'org:first:1.0' {
                dependsOn 'org:client:4.1.1'
            }
            'org:first:2.0'()

            'org:second:1.0' {
                dependsOn 'org:intermediate:1.0'
            }

            'org:intermediate:1.0' {
                dependsOn 'org:first:2.0'
            }
        }

        buildFile << """
            dependencies {
                conf(platform('org:platform:1.0'))
                conf 'org:first:1.0'
                conf 'org:second:1.0'
            }
"""
        when:
        repositoryInteractions {
            'org:client:4.1.0' {
                // In the error case, this version will be resolved
                allowAll()
            }
            'org:client:4.1.1' {
                expectGetMetadata()
            }

            'org:platform:1.0' {
                expectGetMetadata()
            }

            'org:first:1.0' {
                expectGetMetadata()
            }
            'org:first:2.0' {
                expectResolve()
            }
            'org:second:1.0' {
                expectResolve()
            }
            'org:intermediate:1.0' {
                expectResolve()
            }
        }
        run 'checkDeps'

        then:
        def platformConfiguration = isGradleMetadataPublished() ? 'runtimeElements' : 'platform-runtime'
        resolve.expectGraph {
            root(':', ':test:') {
                module('org:platform:1.0') {
                    noArtifacts()
                    configuration(platformConfiguration)
                }
                edge('org:first:1.0', 'org:first:2.0')
                module('org:second:1.0') {
                    module('org:intermediate:1.0') {
                        module('org:first:2.0')
                    }
                }
            }
        }
    }

}
