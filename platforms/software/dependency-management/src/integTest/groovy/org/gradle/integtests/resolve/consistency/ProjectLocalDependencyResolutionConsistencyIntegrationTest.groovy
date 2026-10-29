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
   Dependency path: root project 'test' (conf) --> 'org:foo:1.1'
   Constraint path: root project 'test' (conf) --> 'org:foo:{strictly 1.0}' because of the following reason: version resolved in configuration ':other' by consistent resolution"""
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
                conf.extendsFrom(implementation, runtimeOnly)
                conf {
                   shouldResolveConsistentlyWith(compileClasspath)
                }
            }

            dependencies {
                implementation 'org:foo:1.0'
                runtimeOnly 'org:bar:1.0'
            }
        """

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

    @Issue("https://github.com/gradle/gradle/issues/39472")
    def "strict versions from consistency apply to dependencies of nodes in a cycle that is upgraded by constraints"() {
        repository {
            // ':test' depends on 'test-ui:1.0', which is upgraded to '2.0' by a constraint
            // coming from 'ui-android', which is part of the consistency source graph
            'org:test-ui:1.0' {
                variants(["api", "runtime"]) {
                    availableAt("../../test-ui-android/1.0/test-ui-android-1.0.module", "org", "test-ui-android", "1.0")
                }
            }
            'org:test-ui:2.0' {
                variants(["api", "runtime"]) {
                    availableAt("../../test-ui-android/2.0/test-ui-android-2.0.module", "org", "test-ui-android", "2.0")
                }
            }
            'org:test-ui-android:1.0' {
                dependsOn 'org:activity:1.0'
                dependsOn 'org:activity-compose:1.0'
            }
            'org:test-ui-android:2.0' {
                dependsOn 'org:activity-compose:1.0'
                dependsOn 'org:espresso:1.0'
            }
            'org:ui:2.0' {
                variants(["api", "runtime"]) {
                    availableAt("../../ui-android/2.0/ui-android-2.0.module", "org", "ui-android", "2.0")
                }
            }
            'org:ui-android:2.0' {
                constraint 'org:test-ui:2.0'
            }
            'org:ui:1.0'()
            'org:activity:1.0'()
            'org:activity:2.0' {
                constraint 'org:activity-compose:2.0'
                constraint 'org:activity-ktx:2.0'
            }
            'org:activity-ktx:2.0' {
                dependsOn 'org:activity:2.0'
            }
            'org:activity-compose:1.0'()
            'org:activity-compose:2.0' {
                dependsOn 'org:ui:1.0'
            }
            'org:fragment:1.0'()
            'org:fragment:2.0' {
                dependsOn 'org:activity-ktx:2.0'
            }
            'org:fragment-ktx:2.0' {
                dependsOn 'org:fragment:2.0'
            }
            'org:appcompat:1.0' {
                dependsOn 'org:fragment:1.0'
            }
            'org:futures:1.0'()
            'org:futures:2.0'()
            'org:futures-ktx:1.0' {
                dependsOn 'org:futures:1.0'
            }
            'org:futures-ktx:2.0' {
                constraint 'org:futures:2.0'
            }
            'org:test-core:1.0' {
                dependsOn 'org:futures-ktx:2.0'
            }
            'org:espresso:1.0'()
            'org:espresso:2.0' {
                dependsOn 'org:futures:2.0'
                dependsOn 'org:test-core:1.0'
            }
            'org:espresso-contrib:2.0' {
                dependsOn 'org:appcompat:1.0'
            }
        }

        buildFile << """
            configurations {
                other
                conf.shouldResolveConsistentlyWith(other)
            }

            dependencies {
                other 'org:futures-ktx:1.0'
                other 'org:fragment-ktx:2.0'
                other 'org:ui:2.0'

                conf 'org:espresso:2.0'
                conf 'org:espresso-contrib:2.0'
                conf 'org:test-ui:1.0'
            }
        """

        when:
        repositoryInteractions {
            [
                'org:test-ui:1.0', 'org:test-ui:2.0', 'org:test-ui-android:1.0', 'org:test-ui-android:2.0',
                'org:ui:2.0', 'org:ui-android:2.0', 'org:ui:1.0',
                'org:activity:1.0', 'org:activity:2.0', 'org:activity-ktx:2.0', 'org:activity-compose:1.0', 'org:activity-compose:2.0',
                'org:fragment:1.0', 'org:fragment:2.0', 'org:fragment-ktx:2.0', 'org:appcompat:1.0',
                'org:futures:1.0', 'org:futures:2.0', 'org:futures-ktx:1.0', 'org:futures-ktx:2.0',
                'org:test-core:1.0', 'org:espresso:1.0', 'org:espresso:2.0', 'org:espresso-contrib:2.0'
            ].each { coordinates ->
                "$coordinates" { allowAll() }
            }
        }
        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                module('org:espresso:2.0') {
                    byConflictResolution('between versions 2.0 and 1.0')
                    edge('org:futures:2.0', 'org:futures:1.0') {
                        byAncestor()
                        byConsistentResolution('other')
                        notRequested()
                    }
                    module('org:test-core:1.0') {
                        edge('org:futures-ktx:2.0', 'org:futures-ktx:1.0') {
                            byAncestor()
                            byConsistentResolution('other')
                            notRequested()
                            module('org:futures:1.0')
                        }
                    }
                }
                module('org:espresso-contrib:2.0') {
                    module('org:appcompat:1.0') {
                        edge('org:fragment:1.0', 'org:fragment:2.0') {
                            byAncestor()
                            byConsistentResolution('other')
                            notRequested()
                            module('org:activity-ktx:2.0') {
                                byAncestor()
                                byConsistentResolution('other')
                                notRequested()
                                module('org:activity:2.0') {
                                    byAncestor()
                                    byConsistentResolution('other')
                                    notRequested()
                                    constraint('org:activity-compose:2.0')
                                    constraint('org:activity-ktx:2.0')
                                }
                            }
                        }
                    }
                }
                edge('org:test-ui:1.0', 'org:test-ui:2.0') {
                    byConstraint()
                    byConflictResolution('between versions 2.0 and 1.0')
                    noArtifacts()
                    module('org:test-ui-android:2.0') {
                        edge('org:activity-compose:1.0', 'org:activity-compose:2.0') {
                            byConstraint()
                            byConflictResolution('between versions 2.0 and 1.0')
                            edge('org:ui:1.0', 'org:ui:2.0') {
                                byAncestor()
                                byConsistentResolution('other')
                                notRequested()
                                noArtifacts()
                                module('org:ui-android:2.0') {
                                    byAncestor()
                                    byConsistentResolution('other')
                                    notRequested()
                                    constraint('org:test-ui:2.0')
                                }
                            }
                        }
                        edge('org:espresso:1.0', 'org:espresso:2.0')
                    }
                }
                constraint('org:futures:{strictly 1.0}', 'org:futures:1.0')
                constraint('org:futures-ktx:{strictly 1.0}', 'org:futures-ktx:1.0')
                constraint('org:fragment:{strictly 2.0}', 'org:fragment:2.0')
                constraint('org:activity:{strictly 2.0}', 'org:activity:2.0')
                constraint('org:activity-ktx:{strictly 2.0}', 'org:activity-ktx:2.0')
                constraint('org:ui:{strictly 2.0}', 'org:ui:2.0')
                constraint('org:ui-android:{strictly 2.0}', 'org:ui-android:2.0')
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
