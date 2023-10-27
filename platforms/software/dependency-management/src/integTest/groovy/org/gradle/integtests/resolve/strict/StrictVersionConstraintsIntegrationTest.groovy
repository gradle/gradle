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
package org.gradle.integtests.resolve.strict

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest

class StrictVersionConstraintsIntegrationTest extends AbstractModuleDependencyResolveTest {
    def "can downgrade version"() {
        given:
        repository {
            'org:foo:1.0'()
            'org:foo:2.0'()
            'org:bar:1.0' {
                dependsOn 'org:foo:2.0'
            }
        }

        buildFile << """
            dependencies {
                conf('org:foo') {
                   version { strictly '1.0' }
                }
                conf('org:bar:1.0')
            }
        """

        when:
        repositoryInteractions {
            'org:foo:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:bar:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                edge('org:foo:{strictly 1.0}', 'org:foo:1.0')
                module('org:bar:1.0') {
                    edge('org:foo:2.0', 'org:foo:1.0').byAncestor()
                }
            }
        }
    }

    def "can use dependency constraint to downgrade version"() {
        given:
        repository {
            'org:foo:1.0'()
            'org:foo:2.0'()
            'org:bar:1.0' {
                dependsOn 'org:foo:2.0'
            }
        }

        buildFile << """
            dependencies {
                constraints {
                    conf('org:foo:1.0') {
                       version { strictly '1.0' }
                    }
                }
                conf('org:bar:1.0')
            }
        """

        when:
        repositoryInteractions {
            'org:foo:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:bar:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                constraint('org:foo:{strictly 1.0}', 'org:foo:1.0') {
                    notRequested()
                    byConstraint()
                    byAncestor()
                }
                module('org:bar:1.0') {
                    edge('org:foo:2.0', 'org:foo:1.0')
                }
            }
        }
    }

    def "a strict constraint wins over a nested strict constraint"() {
        boolean publishedConstraintsSupported = gradleMetadataPublished

        given:
        repository {
            'org:a:1.0' {
                dependsOn(group: 'org', artifact: 'b', version: '1.0')
                constraint(group: 'org', artifact: 'c', strictly: '2.0')
            }
            'org:b:1.0' {
                dependsOn 'org:c:3.0'
            }
            'org:c:1.0'()
            'org:c:2.0'()
            'org:c:3.0'()
        }

        buildFile << """
            dependencies {
                conf('org:a:1.0')
                constraints {
                    conf('org:c') { version { strictly '1.0' } }
                }
            }
        """

        when:
        repositoryInteractions {
            'org:a:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:b:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:c:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                module('org:a:1.0') {
                    module('org:b:1.0') {
                        edge('org:c:3.0', 'org:c:1.0') {
                            notRequested()
                            byAncestor()
                            byConstraint()
                        }
                    }
                    if (publishedConstraintsSupported) {
                        constraint('org:c:{strictly 2.0}', 'org:c:1.0')
                    }
                }
                constraint('org:c:{strictly 1.0}', 'org:c:1.0')
            }
        }
    }

    def "identical strict constraints can co-exist in a graph"() {
        boolean publishedConstraintsSupported = gradleMetadataPublished

        given:
        repository {
            'org:a:1.0' {
                dependsOn(group: 'org', artifact: 'b', version: '1.0')
                constraint(group: 'org', artifact: 'c', strictly: '1.0')
            }
            'org:b:1.0' {
                dependsOn 'org:c:2.0'
            }
            'org:c:1.0'()
            'org:c:2.0'()
        }

        buildFile << """
            dependencies {
                conf('org:a:1.0')
                constraints {
                    conf('org:c') { version { strictly '1.0' } }
                }
            }
        """

        when:
        repositoryInteractions {
            'org:a:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:b:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:c:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                module('org:a:1.0') {
                    module('org:b:1.0') {
                        edge('org:c:2.0', 'org:c:1.0') {
                            notRequested()
                            byAncestor()
                            byConstraint()
                        }
                    }
                    if (publishedConstraintsSupported) {
                        constraint('org:c:{strictly 1.0}', 'org:c:1.0')
                    }
                }
                constraint('org:c:{strictly 1.0}', 'org:c:1.0')
            }
        }
    }

    @RequiredFeature(feature=GradleMetadataResolveRunner.GRADLE_METADATA, value="true")
    def "conflicting version constraints fail resolution"() {
        given:
        repository {
            'org:a:1.0' {
                dependsOn(group: 'org', artifact: 'b', version: '1.0')
                dependsOn(group: 'org', artifact: 'c', strictly: '1.0')
            }
            'org:b:1.0' {
                dependsOn 'org:c:2.0'
            }
            'org:c:1.0'()
            'org:c:2.0'()
        }

        buildFile << """
            dependencies {
                conf('org:a:1.0')
                conf('org:c:2.0')
            }
        """

        when:
        repositoryInteractions {
            'org:a:1.0' {
                expectGetMetadata()
            }
            'org:b:1.0' {
                expectGetMetadata()
            }
            'org:c:2.0' {
                expectGetMetadata()
            }
        }
        fails ':checkDeps'

        then:
        failure.assertHasCause """Cannot find a version of 'org:c' that satisfies the version constraints:
   Dependency path ':test:unspecified' --> 'org:c:2.0'
   Dependency path ':test:unspecified' --> 'org:a:1.0' (runtime) --> 'org:c:{strictly 1.0}'
   Dependency path ':test:unspecified' --> 'org:a:1.0' (runtime) --> 'org:b:1.0' (runtime) --> 'org:c:2.0'"""
    }

    def "strict from selected and later evicted modules are ignored"() {
        given:
        repository {
            'org:a:1.0' {
                dependsOn(group: 'org', artifact: 'b', version: '1.0')
                dependsOn(group: 'org', artifact: 'c', strictly: '1.0')
            }
            'org:a:2.0' {
                dependsOn(group: 'org', artifact: 'b', version: '1.0')
                dependsOn(group: 'org', artifact: 'c', version: '1.0')
            }
            'org:b:1.0' {
                dependsOn 'org:c:2.0'
            }
            'org:c:1.0'()
            'org:c:2.0'()

            'org:x:1.0' {
                dependsOn 'org:y:1.0'
            }
            'org:y:1.0' {
                dependsOn 'org:a:2.0'
            }
        }

        buildFile << """
            dependencies {
                conf('org:a:1.0')
                conf('org:x:1.0')
            }
        """

        when:
        repositoryInteractions {
            'org:a:2.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:b:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:c:2.0' {
                expectGetMetadata()
                expectGetArtifact()
            }

            'org:x:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:y:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }

            'org:a:1.0' {
                expectGetMetadata()
            }
            'org:c:1.0' {
                expectGetMetadata()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                edge('org:a:1.0', 'org:a:2.0') {
                    module('org:b:1.0') {
                        module('org:c:2.0')
                    }
                    edge('org:c:1.0', 'org:c:2.0').byConflictResolution("between versions 2.0 and 1.0")
                }.byConflictResolution("between versions 2.0 and 1.0")
                module('org:x:1.0') {
                    module('org:y:1.0') {
                        module('org:a:2.0')
                    }
                }
            }
        }
    }

    def "can bring back a rejected version"() {
        String publishedFooDependencyVersion = gradleMetadataPublished ? '{require 2.0; reject 1.0}' : '2.0'

        given:
        repository {
            'org:foo:1.0'()
            'org:foo:2.0'()
            'org:bar:1.0' {
                dependsOn(group: 'org', artifact: 'foo', version: '2.0', rejects: ['1.0'])
            }
        }

        buildFile << """
            dependencies {
                constraints {
                    conf('org:foo') {
                       version { strictly '1.0' }
                    }
                }
                conf('org:bar:1.0')
            }
        """

        when:
        repositoryInteractions {
            'org:foo:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:bar:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                constraint('org:foo:{strictly 1.0}', 'org:foo:1.0') {
                    notRequested()
                    byConstraint()
                    byAncestor()
                }
                module('org:bar:1.0') {
                    edge("org:foo:$publishedFooDependencyVersion", 'org:foo:1.0')
                }
            }
        }
    }

    def "can downgrade a version range"() {
        given:
        repository {
            'org:foo:1.0'()
            'org:foo:2.1'()
            'org:bar:1.0' {
                dependsOn(group: 'org', artifact: 'foo', version: '[2.0,3.0)')
            }
        }

        buildFile << """
            dependencies {
                constraints {
                    conf('org:foo') {
                       version { strictly '1.0' }
                    }
                }
                conf('org:bar:1.0')
            }
        """

        when:
        repositoryInteractions {
            // no version listing needed!
            'org:foo:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:bar:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                constraint('org:foo:{strictly 1.0}', 'org:foo:1.0') {
                    notRequested()
                    byConstraint()
                    byAncestor()
                }
                module('org:bar:1.0') {
                    edge("org:foo:[2.0,3.0)", 'org:foo:1.0')
                }
            }
        }
    }

    def "can downgrade to a local project"() {
        given:
        repository {
            'org:foo:2.0'()
            'org:bar:1.0' {
               dependsOn 'org:foo:2.0'
            }
        }

        createDirs("foo")
        settingsFile << "\ninclude 'foo'"
        buildFile << """
            project(':foo') {
                configurations.create('default')
                group = 'org'
                version = '1.0'
            }
            dependencies {
                constraints {
                    conf('org:foo') {
                       version { strictly '1.0' }
                    }
                }
                conf('org:bar:1.0')
                conf(project(':foo'))
            }
        """

        when:
        repositoryInteractions {
            'org:bar:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                constraint('org:foo:{strictly 1.0}', ':foo', 'org:foo:1.0').byConstraint()
                module('org:bar:1.0') {
                    edge('org:foo:2.0', ':foo', 'org:foo:1.0') {}.byAncestor()
                }
                project(':foo', 'org:foo:1.0') {
                    configuration = 'default'
                    noArtifacts()
                }
            }
        }
    }

    def "incompatible strict constraint and local project fail to resolve"() {
        given:

        createDirs("foo")
        settingsFile << "\ninclude 'foo'"
        buildFile << """
            project(':foo') {
                configurations.create('default')
                group = 'org'
                version = '1.2'
            }
            dependencies {
                constraints {
                    conf('org:foo') {
                       version { strictly '1.0' }
                    }
                }
                conf(project(':foo'))
            }
        """

        when:
        fails ':checkDeps'

        then:
        failure.assertHasCause("""Cannot find a version of 'org:foo' that satisfies the version constraints:
   Dependency path ':test:unspecified' --> 'project :foo'
   Constraint path ':test:unspecified' --> 'org:foo:{strictly 1.0}'""")
    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    def "original version constraint is not ignored if there is another parent"() {
        given:
        repository {
            'org:x1:1.0' {
                dependsOn 'org:bar:1.0'
                constraint(group: 'org', artifact: 'foo', strictly: '1.0')
            }
            'org:x2:1.0' {
                dependsOn 'org:bar:1.0'
            }
            'org:foo:1.0'()
            'org:foo:2.0'()
            'org:bar:1.0' {
                dependsOn 'org:foo:2.0'
            }
        }

        buildFile << """
            dependencies {
                conf('org:x1:1.0')
                conf('org:x2:1.0')
            }
        """

        when:
        repositoryInteractions {
            'org:x1:1.0' {
                expectGetMetadata()
            }
            'org:x2:1.0' {
                expectGetMetadata()
            }
            'org:bar:1.0' {
                expectGetMetadata()
            }
            'org:foo:2.0' {
                expectGetMetadata()
            }
        }
        fails ':checkDeps'

        then:
        failure.assertHasCause """Cannot find a version of 'org:foo' that satisfies the version constraints:
   Dependency path ':test:unspecified' --> 'org:x1:1.0' (runtime) --> 'org:bar:1.0' (runtime) --> 'org:foo:2.0'
   Constraint path ':test:unspecified' --> 'org:x1:1.0' (runtime) --> 'org:foo:{strictly 1.0}'"""
    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    def "can reintroduce a strict version on the root level"() { // similar to test above, but reintroduces strict version in build script
        given:
        repository {
            'org:x1:1.0' {
                dependsOn 'org:bar:1.0'
                constraint(group: 'org', artifact: 'foo', strictly: '1.0')
            }
            'org:x2:1.0' {
                dependsOn 'org:bar:1.0'
            }
            'org:foo:1.0'()
            'org:foo:2.0'()
            'org:bar:1.0' {
                dependsOn 'org:foo:2.0'
            }
        }

        buildFile << """
            dependencies {
                conf('org:x1:1.0')
                conf('org:x2:1.0')
                constraints {
                    conf('org:foo') { version { strictly '1.0' } }
                }
            }
        """

        when:
        repositoryInteractions {
            'org:x1:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:x2:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:bar:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:foo:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                module('org:x1:1.0') {
                    module('org:bar:1.0') {
                        edge('org:foo:2.0', 'org:foo:1.0') {
                            notRequested()
                            byConstraint()
                            byAncestor()
                        }
                    }
                    constraint('org:foo:{strictly 1.0}', 'org:foo:1.0')
                }
                module('org:x2:1.0') {
                    module('org:bar:1.0')
                }
                constraint('org:foo:{strictly 1.0}', 'org:foo:1.0')
            }
        }
    }

    def "does not ignore a second dependency declaration which only differs in strictly detail"() {
        given:
        repository {
            'org:foo:1.0'()
        }

        buildFile << """
            dependencies {
                conf('org:foo:1.0')
                conf('org:foo') {
                   version { strictly '1.0' }
                }
            }
        """

        when:
        repositoryInteractions {
            'org:foo:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:foo:1.0", "org:foo:1.0")
                edge("org:foo:{strictly 1.0}", "org:foo:1.0")
            }
        }
    }
}
