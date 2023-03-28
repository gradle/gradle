/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.resolve.consistency

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest
import spock.lang.Issue

// Limit the combinations of tests since we're only interested in the consistency
// behavior, not actual metadata
@RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "maven")
@RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
class ProjectLocalDependencyResolutionConsistencyIntegrationTest extends AbstractModuleDependencyResolveTest {
    def "can declare consistency between two configurations"() {
        repository {
            'org:foo:1.1'()
        }

        buildFile << """
            configurations {
                other
                conf.shouldResolveConsistentlyWith(other)
            }

            dependencies {
                conf 'org:foo:1.0'
                other 'org:foo:1.1'
            }
        """

        when:
        repositoryInteractions {
            'org:foo:1.1' {
                expectResolve()
            }
        }
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                edge('org:foo:1.0', 'org:foo:1.1') {
                    byConsistentResolution('other')
                    byConflictResolution('between versions 1.1 and 1.0')
                }
                constraint("org:foo:{strictly 1.1}", "org:foo:1.1")
            }
        }
    }

    def "fails if there's a conflict between a first level dependency version and a strict version from consistency"() {
        repository {
            'org:foo:1.0'()
            'org:foo:1.1'()
        }

        buildFile << """
            configurations {
                other
                conf.shouldResolveConsistentlyWith(other)
            }

            dependencies {
                conf 'org:foo:1.1'
                other 'org:foo:1.0'
            }
        """

        when:
        repositoryInteractions {
            'org:foo:1.0' {
                expectGetMetadata()
            }
            'org:foo:1.1' {
                expectGetMetadata()
            }
        }
        fails 'checkDeps'

        then:
        failure.assertHasCause """Cannot find a version of 'org:foo' that satisfies the version constraints:
   Dependency path ':test:unspecified' --> 'org:foo:1.1'
   Constraint path ':test:unspecified' --> 'org:foo:{strictly 1.0}' because of the following reason: version resolved in configuration ':other' by consistent resolution"""
    }

    def "first level dependency can be downgraded only if it's a preferred version"() {
        repository {
            'org:foo:1.0'()
            'org:foo:1.1'()
        }

        buildFile << """
            configurations {
                other
                conf.shouldResolveConsistentlyWith(other)
            }

            dependencies {
                conf('org:foo') {
                    version {
                        prefer '1.1'
                    }
                }
                other 'org:foo:1.0'
            }
        """

        when:
        repositoryInteractions {
            'org:foo:1.0' {
                expectResolve()
            }
        }
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                edge('org:foo:{prefer 1.1}', 'org:foo:1.0') {
                    byConsistentResolution('other')
                }
                constraint("org:foo:{strictly 1.0}", "org:foo:1.0")
            }
        }
    }

    def "a transitive dependency may be downgraded by consistent resolution"() {
        repository {
            'org:foo:1.0' {
                dependsOn 'org:fooA:1.0'
            }
            'org:bar:1.0' {
                dependsOn 'org:barA:1.0'
            }
            'org:fooA:1.0' {
                dependsOn 'org:transitive:1.0'
            }
            'org:barA:1.0' {
                dependsOn 'org:transitive:2.0'
            }
            'org:transitive:1.0'()
            'org:transitive:2.0'()
        }

        buildFile << """
            configurations {
                implementation
                runtimeOnly.extendsFrom(implementation)
                compileClasspath.extendsFrom(implementation)
                runtimeClasspath.extendsFrom(implementation, runtimeOnly)
                runtimeClasspath {
                   shouldResolveConsistentlyWith(compileClasspath)
                }
            }

            dependencies {
                implementation 'org:foo:1.0'
                runtimeOnly 'org:bar:1.0'
            }
        """
        def resolve = new ResolveTestFixture(buildFile, "runtimeClasspath")
        resolve.expectDefaultConfiguration("runtime")
        resolve.prepare()
        when:
        repositoryInteractions {
            'org:foo:1.0' {
                expectResolve()
            }
            'org:bar:1.0' {
                expectResolve()
            }
            'org:fooA:1.0' {
                expectResolve()
            }
            'org:barA:1.0' {
                expectResolve()
            }
            'org:transitive:1.0' {
                expectResolve()
            }
        }
        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                module('org:foo:1.0') {
                    byConsistentResolution('compileClasspath')
                    module('org:fooA:1.0') {
                        byConsistentResolution('compileClasspath')
                        byAncestor()
                        notRequested()
                        module("org:transitive:1.0") {
                            byConsistentResolution('compileClasspath')
                            byAncestor()
                            notRequested()
                        }
                    }
                }
                module('org:bar:1.0') {
                    module('org:barA:1.0') {
                        edge("org:transitive:2.0", "org:transitive:1.0") {
                            byConsistentResolution('compileClasspath')
                        }
                    }
                }
                // The following constraints come from the compile classpath configuration resolution result
                constraint("org:foo:{strictly 1.0}", "org:foo:1.0")
                constraint("org:fooA:{strictly 1.0}", "org:fooA:1.0")
                constraint("org:transitive:{strictly 1.0}", "org:transitive:1.0")
            }
        }
    }

    def "detects cycles in consistency"() {
        repository {
            'org:foo:1.0'()
            'org:foo:1.1'()
        }
        buildFile << """
            configurations {
                other.shouldResolveConsistentlyWith(another)
                another.shouldResolveConsistentlyWith(conf)
                conf.shouldResolveConsistentlyWith(other)
            }

            dependencies {
                conf 'org:foo:1.0'
                other 'org:foo:1.1'
            }
        """

        when:
        fails ':checkDeps'

        then:
        failure.assertHasCause 'Cycle detected in consistent resolution sources: conf -> other -> another -> conf'
    }

    def "resolution rules have higher priority than consistency"() {
        repository {
            'org:foo:1.0'()
            'org:foo:1.1'()
            'org:foo:1.2'()
        }

        buildFile << """
            configurations {
                other
                conf.shouldResolveConsistentlyWith(other)

                conf.resolutionStrategy.eachDependency { details ->
                    if (details.requested.name == 'foo') {
                        details.useVersion '1.2'
                    }
                }
            }

            dependencies {
                conf 'org:foo:1.1'
                other 'org:foo:1.0'
            }
        """

        when:
        repositoryInteractions {
            'org:foo:1.0' {
                expectGetMetadata()
            }
            'org:foo:1.2' {
                expectResolve()
            }
        }
        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                edge('org:foo:1.1', 'org:foo:1.2') {
                    selectedByRule()
                    byConsistentResolution('other')
                }
                constraint("org:foo:{strictly 1.0}", "org:foo:1.2")
            }
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/15588")
    def "shouldn't resolve source configuration during task dependency resolution phase"() {
        repository {
            'org:foo:1.1'()
        }

        buildFile << """
            configurations {
                other
                conf.shouldResolveConsistentlyWith(other)
            }

            dependencies {
                conf 'org:foo:1.0'
                other 'org:foo:1.1'
            }
        """
        withEagerResolutionPrevention()

        when:
        repositoryInteractions {
            'org:foo:1.1' {
                expectResolve()
            }
        }
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                edge('org:foo:1.0', 'org:foo:1.1') {
                    byConsistentResolution('other')
                    byConflictResolution('between versions 1.1 and 1.0')
                }
                constraint("org:foo:{strictly 1.1}", "org:foo:1.1")
            }
        }
    }

    void withEagerResolutionPrevention() {
        buildFile << """
            def beforeTaskExecutionPhase = true
            gradle.taskGraph.whenReady {
                beforeTaskExecutionPhase = false
            }
            project.configurations.all {
                it.incoming.beforeResolve { configuration ->
                    if (beforeTaskExecutionPhase) {
                        throw new RuntimeException("Configuration \${configuration.name} is being resolved before task execution phase.")
                    }
                }
            }
        """
    }
}
