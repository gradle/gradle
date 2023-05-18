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

package org.gradle.integtests.resolve.override

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest

/**
 * There is more test coverage for 'dependency artifacts' in ArtifactDependenciesIntegrationTest (old test setup).
 */
// This test bypasses all metadata using 'artifact()' metadata sources. It is sufficient to test with one metadata setup.
@RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
class ComponentOverrideMetadataResolveIntegrationTest extends AbstractModuleDependencyResolveTest {

    def "can combine artifact notation and constraints"() {
        resolve.expectDefaultConfiguration(useMaven() ? 'runtime' : 'default')

        given:
        repository {
            'org:foo:1.0' {
                withModule {
                    undeclaredArtifact(type: 'distribution-tgz')
                }
            }
        }

        buildFile << """
            repositories.all {
                metadataSources { artifact() }
            }
            dependencies {
                conf('org:foo@distribution-tgz')

                constraints {
                   conf('org:foo:1.0')
                }
            }
        """

        when:
        repositoryInteractions {
            'org:foo:1.0' {
                // expectHeadArtifact(type: 'distribution-tgz') <- head request can happen once or twice depending on timing
                maybeHeadOrGetArtifact(type: 'distribution-tgz')
            }
        }
        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge('org:foo', 'org:foo:1.0') {
                    byConstraint()
                    artifact(type: 'distribution-tgz')
                }
                constraint('org:foo:1.0')
            }
        }
    }

    def "The first artifact is used as replacement for metadata if multiple artifacts are declared using #declaration"() {
        resolve.expectDefaultConfiguration(useMaven() ? 'runtime' : 'default')

        given:
        repository {
            'org:foo:1.0' {
                withModule {
                    undeclaredArtifact(name: artifactName, type: 'distribution-tgz')
                    undeclaredArtifact(name: artifactName, type: 'zip')
                }
            }
        }

        buildFile << """
            repositories.all {
                metadataSources { artifact() }
            }
            dependencies {
                $declaration
                constraints {
                   conf('org:foo:1.0')
                }
            }
        """

        when:
        repositoryInteractions {
            'org:foo:1.0' {
                // HEAD requests happen in parallel when Gradle downloads metadata.
                // In this cases "downloading metadata" means testing for the artifact.
                // Each declaration is treated separately with it's own "consumer provided" metadata
                // Depending on the timing, this can lead to multiple parallel requests (one for each declaration)
                maybeHeadOrGetArtifact(name: artifactName, type: 'distribution-tgz')
                expectGetArtifact(name: artifactName, type: 'zip')
            }
        }
        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge('org:foo', 'org:foo:1.0') {
                    byConstraint()
                    artifact(name: artifactName, type: 'distribution-tgz')
                    artifact(name: artifactName, type: 'zip')
                }
                constraint('org:foo:1.0')
            }
        }

        where:
        notation                                               | artifactName | declaration
        'multiple dependency declarations (AT notation)'       | 'foo'        | "conf('org:foo@distribution-tgz'); conf('org:foo@zip')"
        'multiple dependency declarations (artifact notation)' | 'bar'        | "conf('org:foo') { artifact { name = 'bar'; type = 'distribution-tgz' } }; conf('org:foo') { artifact { name = 'bar'; type = 'zip' } }"
        'multiple artifact declarations'                       | 'bar'        | "conf('org:foo') { artifact { name = 'bar'; type = 'distribution-tgz' }; artifact { name = 'bar'; type = 'zip' } }"
    }

    def "client module for version 1.0 is selected over other dependency with version #otherVersion"() {
        resolve.expectDefaultConfiguration('default')

        given:
        repository {
            'org:foo:0.9' {
                dependsOn 'org:bar:1.0'
            }
            'org:foo:1.0' {
                dependsOn 'org:bar:1.0'
            }
            'org:bar:1.0'()
        }

        buildFile << """
            dependencies {
                conf 'org:foo:$otherVersion'
                conf module('org:foo:1.0') {
                    // no dependencies
                }
            }

        """

        when:
        repositoryInteractions {
            'org:foo:1.0' {
                expectResolve()
            }
        }
        succeeds 'checkDeps'

        then:
        def notSelectedModule = "org:foo:$otherVersion"
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:foo:1.0') {
                    if (notSelectedModule != 'org:foo:1.0') {
                        byConflictResolution('between versions 1.0 and 0.9')
                    }
                }
                if (notSelectedModule != 'org:foo:1.0') {
                    edge(notSelectedModule, 'org:foo:1.0')
                }
            }
        }

        where:
        otherVersion << ['0.9', '1.0']
    }

    def "client module for a not selected version or range is ignored"() {
        given:
        repository {
            'org:foo:1.1' {
                dependsOn 'org:bar:1.0'
            }
            'org:foo:1.0' {
                dependsOn 'org:bar:1.0'
            }
            'org:bar:1.0'()
        }

        buildFile << """
            dependencies {
                conf 'org:foo:1.1'
                conf module('org:foo:[1.0,1.1]') {
                    // no dependencies
                }
                conf module('org:foo:1.0') {
                    // no dependencies
                }
            }
        """

        when:
        repositoryInteractions {
            'org:foo:1.1' {
                expectResolve()
            }
            'org:bar:1.0' {
                expectResolve()
            }
        }
        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:foo:1.1') {
                    byConflictResolution('between versions 1.1 and 1.0')
                    module('org:bar:1.0')
                }
                edge('org:foo:[1.0,1.1]', 'org:foo:1.1')
                edge('org:foo:1.0', 'org:foo:1.1')
            }
        }
    }

    def "clashing client modules fail the build"() {
        given:
        buildFile << """
            configurations {
                conf
            }
            dependencies {
                conf module('org:foo:1.0') {
                }
                conf module('org:foo:1.0') {
                    dependency("org:baz:1.0")
                }
            }
        """

        when:
        fails 'checkDeps'

        then:
        failure.assertHasCause("org:foo:1.0 has more than one client module definitions.")
    }
}
