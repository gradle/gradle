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
package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner

class PublishedDependencyConstraintsIntegrationTest extends AbstractModuleDependencyResolveTest {

    boolean featureAvailable() {
        gradleMetadataEnabled
    }

    void "dependency constraint is ignored when feature is not enabled"() {
        given:
        // Do not enable feature
        settingsFile.text = "rootProject.name = '$rootProjectName'"

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
        def expectedVariant = useMaven()?'runtime':'default'
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:first-level:1.0:${expectedVariant}")
                module("org:foo:1.0:${expectedVariant}")
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
                        def module = module("org:foo:1.1")
                        if (GradleMetadataResolveRunner.gradleMetadataEnabled) {
                            module.byConstraint('published dependency constraint')
                        }
                    }
                }
                if (available) {
                    edge("org:foo:1.0","org:foo:1.1").byConflictResolution("between versions 1.0 and 1.1")
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
                        module("org:foo:1.1")
                    }
                }
                module("org:first-level2:1.0") {
                    if (available) {
                        edge("org:foo:1.0","org:foo:1.1").byConflictResolution("between versions 1.0 and 1.1")
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
                        edge("org:foo:[1.0,1.1]", "org:foo:1.1")
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
                        edge("org:bar:1.1", "org:foo:1.1").selectedByRule()
                        edge("org:foo:1.0", "org:foo:1.1").byConflictResolution("between versions 1.0 and 1.1")
                    } else {
                        module("org:foo:1.0")
                    }
                }
            }
        }
    }

}
