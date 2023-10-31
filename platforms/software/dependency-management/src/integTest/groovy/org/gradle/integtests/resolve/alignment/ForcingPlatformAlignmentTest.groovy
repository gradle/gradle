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
package org.gradle.integtests.resolve.alignment

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.publish.RemoteRepositorySpec
import org.gradle.internal.component.ResolutionFailureHandler
import org.junit.Assume
import spock.lang.Issue

@RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "ivy")
@RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
class ForcingPlatformAlignmentIvyWithGradleMetadataTest extends ForcingPlatformAlignmentTest {
}

@RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "ivy")
@RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "false")
class ForcingPlatformAlignmentIvyWithoutGradleMetadataTest extends ForcingPlatformAlignmentTest {
}

@RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "maven")
@RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
class ForcingPlatformAlignmentMavenWithGradleMetadataTest extends ForcingPlatformAlignmentTest {
}

@RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "maven")
@RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "false")
class ForcingPlatformAlignmentMavenWithoutGradleMetadataTest extends ForcingPlatformAlignmentTest {
}

abstract class ForcingPlatformAlignmentTest extends AbstractAlignmentSpec {

    def setup() {
        Assume.assumeTrue(GradleContextualExecuter.embedded)
        repoSpec.metaClass.platform = this.&platform.curry(repoSpec)

        file("gradle.properties").text = "${ResolutionFailureHandler.FULL_FAILURES_MESSAGE_PROPERTY}=true"
    }

    def "can force a virtual platform version by forcing one of its leaves through resolutionStrategy.force"() {
        repository {
            ['2.7.9', '2.9.4', '2.9.4.1'].each {
                path "databind:$it -> core:$it"
                path "databind:$it -> annotations:$it"
                path "kotlin:$it -> core:$it"
                path "kotlin:$it -> annotations:$it"
            }
        }

        given:
        buildFile << """
            dependencies {
                conf("org:core:2.9.4")
                conf("org:databind:2.7.9")
                conf("org:kotlin:2.9.4.1")
            }

            configurations {
                conf.resolutionStrategy.force("org:databind:2.7.9")
            }
        """

        and:
        "a rule which infers module set from group and version"()

        when:
        expectAlignment {
            module('core') tries('2.9.4') alignsTo('2.7.9') byVirtualPlatform()
            module('databind') alignsTo('2.7.9') byVirtualPlatform()
            module('kotlin') tries('2.9.4.1') alignsTo('2.7.9') byVirtualPlatform()
            module('annotations') tries('2.9.4.1') alignsTo('2.7.9') byVirtualPlatform()
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:core:2.9.4", "org:core:2.7.9") {
                    forced()
                    byConstraint("belongs to platform org:platform:2.7.9")
                }
                module("org:databind:2.7.9") {
                    forced()
                    byConstraint("belongs to platform org:platform:2.7.9")
                    module('org:annotations:2.7.9') {
                        forced()
                        byConstraint("belongs to platform org:platform:2.7.9")
                    }
                    module('org:core:2.7.9')
                }
                edge("org:kotlin:2.9.4.1", "org:kotlin:2.7.9") {
                    forced()
                    byConstraint("belongs to platform org:platform:2.7.9")
                    module('org:core:2.7.9')
                    module('org:annotations:2.7.9')
                }
            }
        }
    }

    def "can force a virtual platform version by forcing one of its leaves through resolutionStrategy.substitution"() {
        repository {
            ['2.7.9', '2.9.4', '2.9.4.1'].each {
                path "databind:$it -> core:$it"
                path "databind:$it -> annotations:$it"
                path "kotlin:$it -> core:$it"
                path "kotlin:$it -> annotations:$it"
            }
        }

        given:
        buildFile << """
            dependencies {
                conf("org:core:2.9.4")
                conf("org:databind:2.7.9")
                conf("org:kotlin:2.9.4.1")
            }

            configurations {
                conf.resolutionStrategy.dependencySubstitution {
                    substitute module("org:databind") using module("org:databind:2.7.9")
                }
            }
        """

        and:
        "a rule which infers module set from group and version"()

        when:
        expectAlignment {
            module('core') tries('2.9.4') alignsTo('2.7.9') byVirtualPlatform()
            module('databind') alignsTo('2.7.9') byVirtualPlatform()
            module('kotlin') tries('2.9.4.1') alignsTo('2.7.9') byVirtualPlatform()
            module('annotations') tries('2.9.4.1') alignsTo('2.7.9') byVirtualPlatform()
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:core:2.9.4", "org:core:2.7.9") {
                    forced()
                    byConstraint("belongs to platform org:platform:2.7.9")
                }
                module("org:databind:2.7.9") {
                    selectedByRule()
                    forced()
                    byConstraint("belongs to platform org:platform:2.7.9")
                    module('org:annotations:2.7.9') {
                        forced()
                        byConstraint("belongs to platform org:platform:2.7.9")
                    }
                    module('org:core:2.7.9')
                }
                edge("org:kotlin:2.9.4.1", "org:kotlin:2.7.9") {
                    forced()
                    byConstraint("belongs to platform org:platform:2.7.9")
                    module('org:core:2.7.9')
                    module('org:annotations:2.7.9')
                }
            }
        }
    }

    def "fails if forcing a virtual platform version by forcing multiple leaves with different versions through resolutionStrategy"() {
        repository {
            ['2.7.9', '2.9.4', '2.9.4.1'].each {
                path "databind:$it -> core:$it"
                path "databind:$it -> annotations:$it"
                path "kotlin:$it -> core:$it"
                path "kotlin:$it -> annotations:$it"
            }
        }

        given:
        buildFile << """
            configurations {
                conf.resolutionStrategy {
                    force('org:core:2.9.4')
                    force('org:databind:2.7.9')
                }
            }
            dependencies {
                conf("org:core:2.9.4.1")
                conf("org:kotlin:2.9.4.1")

                conf("org:databind:2.9.4.1")
            }
        """

        and:
        "a rule which infers module set from group and version"()

        when:
        allowAllRepositoryInteractions()
        fails ':checkDeps'

        then:
        failureCauseContains("Multiple forces on different versions for virtual platform org:platform")
    }

    def "fails if forcing a virtual platform version by forcing multiple leaves with different versions through resolutionStrategy.dependencySubstitution"() {
        repository {
            ['2.7.9', '2.9.4', '2.9.4.1'].each {
                path "databind:$it -> core:$it"
                path "databind:$it -> annotations:$it"
                path "kotlin:$it -> core:$it"
                path "kotlin:$it -> annotations:$it"
            }
        }

        given:
        buildFile << """
            configurations {
                conf.resolutionStrategy.dependencySubstitution {
                    substitute module('org:core') using module('org:core:2.9.4')
                    substitute module('org:databind') using module('org:databind:2.7.9')
                }
            }
            dependencies {
                conf("org:core:2.9.4.1")
                conf("org:kotlin:2.9.4.1")

                conf("org:databind:2.9.4.1")
            }
        """

        and:
        "a rule which infers module set from group and version"()

        when:
        allowAllRepositoryInteractions()
        fails ':checkDeps'

        then:
        failureCauseContains("Multiple forces on different versions for virtual platform org:platform")
    }

    def "succeeds if using a virtual platform version and forcing a leaf with different version through resolutionStrategy"() {
        repository {
            ['2.7.9', '2.9.4', '2.9.4.1'].each {
                path "databind:$it -> core:$it"
                path "databind:$it -> annotations:$it"
                path "kotlin:$it -> core:$it"
                path "kotlin:$it -> annotations:$it"
            }
        }

        given:
        buildFile << """
            configurations {
                conf.resolutionStrategy {
                    force('org:databind:2.7.9')
                }
            }
            dependencies {
                conf("org:core:2.9.4")

                conf enforcedPlatform("org:platform:2.9.4")

                conf("org:kotlin:2.9.4.1")

                conf("org:databind:2.9.4")
            }
        """

        and:
        "a rule which infers module set from group and version"()

        when:
        allowAllRepositoryInteractions()

        then:
        succeeds(':checkDeps')
    }

    def "succeeds if forcing a virtual platform version by forcing multiple leaves with same version through resolutionStrategy"() {
        repository {
            ['2.7.9', '2.9.4', '2.9.4.1'].each {
                path "databind:$it -> core:$it"
                path "databind:$it -> annotations:$it"
                path "kotlin:$it -> core:$it"
                path "kotlin:$it -> annotations:$it"
            }
        }

        given:
        buildFile << """
            configurations {
                conf.resolutionStrategy {
                    force('org:core:2.7.9')
                    force('org:databind:2.7.9')
                }
            }
            dependencies {
                conf("org:core:2.9.4")
                conf("org:kotlin:2.9.4.1")

                conf("org:databind:2.7.9")
            }
        """

        and:
        "a rule which infers module set from group and version"()

        expect:
        allowAllRepositoryInteractions()
        succeeds ':checkDeps'
    }

    def "succeeds if forcing a virtual platform version and forcing a leaf with same version through resolutionStrategy"() {
        repository {
            ['2.7.9', '2.9.4', '2.9.4.1'].each {
                path "databind:$it -> core:$it"
                path "databind:$it -> annotations:$it"
                path "kotlin:$it -> core:$it"
                path "kotlin:$it -> annotations:$it"
            }
        }

        given:
        buildFile << """
            configurations {
                conf.resolutionStrategy {
                    force('org:databind:2.7.9')
                }
            }
            dependencies {
                conf("org:core:2.9.4")

                conf enforcedPlatform("org:platform:2.7.9")

                conf("org:kotlin:2.9.4.1")

                conf("org:databind:2.9.4")
            }
        """

        and:
        "a rule which infers module set from group and version"()

        expect:
        allowAllRepositoryInteractions()
        succeeds ':checkDeps'
    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "maven")
    def 'forced platform turning selector state to force after being selected and deselected'() {
        repository {
            ['1.0', '2.0'].each {
                path "webapp:$it -> xml:$it"
                path "org:other:$it"
            }
            'root:root:1.0' {
                dependsOn('org:webapp:1.0')
                dependsOn('org:other:1.0')
            }
        }
        def bomDep = mavenHttpRepo.module('org', 'xml', '2.0')
        mavenHttpRepo.module('bom', 'bom', '1.0').dependencyConstraint(bomDep).hasPackaging('pom').publish()

        given:
        buildFile << """
            configurations {
                conf.resolutionStrategy.force 'org:other:1.0'
            }
            dependencies {
                conf platform("bom:bom:1.0")
                conf "root:root:1.0"
            }
        """

        and:
        "align the 'org' group only"()

        when:
        repositoryInteractions {
            group('org') {
                ['webapp', 'xml', 'other'].each { mod ->
                    module(mod) {
                        ['1.0', '2.0'].each { v ->
                            version(v) {
                                allowAll()
                            }
                        }
                    }
                }
            }
            'bom:bom:1.0' {
                allowAll()
            }
            'root:root:1.0' {
                allowAll()
            }
        }

        succeeds ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("bom:bom:1.0") {
                    configuration = 'platform-runtime'
                    constraint("org:xml:2.0", "org:xml:1.0") {
                        forced()
                        byConstraint()
                        byConstraint("belongs to platform org:platform:1.0")
                    }
                    noArtifacts()
                }
                module("root:root:1.0") {
                    module('org:webapp:1.0') {
                        forced()
                        byConstraint("belongs to platform org:platform:1.0")
                        module('org:xml:1.0')
                    }
                    module('org:other:1.0') {
                        forced()
                        byConstraint("belongs to platform org:platform:1.0")
                    }
                }
            }
        }
    }

    def "can force a virtual platform version by forcing the platform itself via a dependency"() {
        repository {
            ['2.7.9', '2.9.4', '2.9.4.1'].each {
                path "databind:$it -> core:$it"
                path "databind:$it -> annotations:$it"
                path "kotlin:$it -> core:$it"
                path "kotlin:$it -> annotations:$it"
            }
        }

        given:
        buildFile << """
            dependencies {
                $dependencies
            }
        """

        and:
        "a rule which infers module set from group and version"()

        when:
        allowAllRepositoryInteractions()
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:core:2.9.4", "org:core:2.7.9") {
                    forced()
                    byConstraint("belongs to platform org:platform:2.7.9")
                }
                edge("org:databind:2.9.4", "org:databind:2.7.9") {
                    forced()
                    byConstraint("belongs to platform org:platform:2.7.9")
                    module('org:annotations:2.7.9') {
                        forced()
                        byConstraint("belongs to platform org:platform:2.7.9")
                    }
                    module('org:core:2.7.9')
                }
                edge("org:kotlin:2.9.4.1", "org:kotlin:2.7.9") {
                    forced()
                    byConstraint("belongs to platform org:platform:2.7.9")
                    module('org:core:2.7.9')
                    module('org:annotations:2.7.9')
                }
            }
        }

        where: "order of dependencies doesn't matter"
        dependencies << [
                'conf("org:core:2.9.4")',
                'conf("org:databind:2.9.4")',
                'conf("org:kotlin:2.9.4.1")',
                'conf enforcedPlatform("org:platform:2.7.9")'
        ].permutations()*.join("\n")
    }

    def "can force a virtual platform version by forcing the platform itself via a constraint"() {
        repository {
            ['2.7.9', '2.9.4', '2.9.4.1'].each {
                path "databind:$it -> core:$it"
                path "databind:$it -> annotations:$it"
                path "kotlin:$it -> core:$it"
                path "kotlin:$it -> annotations:$it"
            }
        }

        given:
        buildFile << """
            dependencies {
                $dependencies
            }
        """

        and:
        "a rule which infers module set from group and version"()

        when:
        allowAllRepositoryInteractions()
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:core:2.9.4", "org:core:2.7.9") {
                    forced()
                    byConstraint("belongs to platform org:platform:2.7.9")
                }
                module("org:databind:2.7.9") {
                    forced()
                    byConstraint("belongs to platform org:platform:2.7.9")
                    module('org:annotations:2.7.9') {
                        forced()
                        byConstraint("belongs to platform org:platform:2.7.9")
                    }
                    module('org:core:2.7.9')
                }
                edge("org:kotlin:2.9.4.1", "org:kotlin:2.7.9") {
                    forced()
                    byConstraint("belongs to platform org:platform:2.7.9")
                    module('org:core:2.7.9')
                    module('org:annotations:2.7.9')
                }
            }
            virtualConfiguration('org:platform:2.7.9')
        }

        where: "order of dependencies doesn't matter"
        dependencies << [
                'conf("org:core:2.9.4")',
                'conf("org:databind:2.7.9")',
                'conf("org:kotlin:2.9.4.1")',
                'constraints { conf enforcedPlatform("org:platform:2.7.9") }'
        ].permutations()*.join("\n")
    }

    def "can force a virtual platform version by forcing the platform itself via a constraint using a strict version"() {
        repository {
            ['2.7.9', '2.9.4', '2.9.4.1'].each {
                path "databind:$it -> core:$it"
                path "databind:$it -> annotations:$it"
                path "kotlin:$it -> core:$it"
                path "kotlin:$it -> annotations:$it"
            }
        }

        given:
        buildFile << """
            dependencies {
                $dependencies
            }
        """

        and:
        "a rule which infers module set from group and version"()

        when:
        allowAllRepositoryInteractions()
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:core:2.9.4", "org:core:2.7.9") {
                    forced()
                    byConstraint("belongs to platform org:platform:2.7.9")
                }
                module("org:databind:2.7.9") {
                    forced()
                    byConstraint("belongs to platform org:platform:2.7.9")
                    module('org:annotations:2.7.9') {
                        forced()
                        byConstraint("belongs to platform org:platform:2.7.9")
                    }
                    module('org:core:2.7.9')
                }
                edge("org:kotlin:2.9.4.1", "org:kotlin:2.7.9") {
                    forced()
                    byConstraint("belongs to platform org:platform:2.7.9")
                    module('org:core:2.7.9')
                    module('org:annotations:2.7.9')
                }
            }
            virtualConfiguration('org:platform:2.7.9')
        }

        where: "order of dependencies doesn't matter"
        dependencies << [
                'conf("org:core:2.9.4")',
                'conf("org:databind:2.7.9")',
                'conf("org:kotlin:2.9.4.1")',
                'constraints { conf ("org:platform") { version { strictly("2.7.9") } } }'
        ].permutations()*.join("\n")
    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "maven")
    def "can constrain a virtual platforms components by adding the platform itself via a constraint"() {
        repository {
            ['2.7.9', '2.9.4', '2.9.4.1'].each {
                path "databind:$it -> core:$it"
                path "databind:$it -> annotations:$it"
                path "kotlin:$it -> core:$it"
                path "kotlin:$it -> annotations:$it"
            }
        }
        given:
        buildFile << """
            dependencies {
                $dependencies
            }
        """
        and:
        "a rule which infers module set from group and version"()
        when:
        allowAllRepositoryInteractions()
        run ':checkDeps'
        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:core:2.7.9", "org:core:2.9.4.1") {
                    byConstraint("belongs to platform org:platform:2.9.4.1")
                    maybeByConflictResolution()
                }
                edge("org:databind:2.7.9", "org:databind:2.9.4.1") {
                    byConstraint("belongs to platform org:platform:2.9.4.1")
                    byConflictResolution("between versions 2.9.4.1 and 2.7.9")
                    module('org:annotations:2.9.4.1') {
                        byConstraint("belongs to platform org:platform:2.9.4.1")
                        maybeByConflictResolution()
                    }
                    module('org:core:2.9.4.1')
                }
                edge("org:kotlin:2.9.4", "org:kotlin:2.9.4.1") {
                    byConstraint("belongs to platform org:platform:2.9.4.1")
                    byConflictResolution("between versions 2.9.4.1 and 2.9.4")
                    module('org:core:2.9.4.1')
                    module('org:annotations:2.9.4.1')
                }
            }
            virtualConfiguration('org:platform:2.9.4.1')
        }
        where: "order of dependencies doesn't matter"
        dependencies << [
                'conf("org:core:2.7.9")',
                'conf("org:databind:2.7.9")',
                'conf("org:kotlin:2.9.4")',
                'constraints { conf "org:platform:2.9.4.1" }'
        ].permutations()*.join("\n")
    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "maven")
    def "can force a published platform version by forcing the platform itself via a dependency"() {
        repository {
            ['2.7.9', '2.9.4', '2.9.4.1'].each { v ->
                path "databind:$v -> core:$v"
                path "databind:$v -> annotations:$v"
                path "kotlin:$v -> core:$v"
                path "kotlin:$v -> annotations:$v"

                platform("org", "platform", v, [
                        "org:core:$v",
                        "org:databind:$v",
                        "org:kotlin:$v",
                        "org:annotations:$v",
                ])
            }
        }

        given:
        buildFile << """
            dependencies {
                $dependencies
            }
        """

        and:
        "a rule which infers module set from group and version"(false)

        when:
        allowAllRepositoryInteractions()
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:core:2.9.4", "org:core:2.7.9") {
                    byConstraint()
                    forced()
                }
                module("org:databind:2.7.9") {
                    byConstraint()
                    forced()
                    module('org:annotations:2.7.9') {
                        byConstraint()
                        forced()
                        module("org:platform:2.7.9")
                    }
                    module('org:core:2.7.9') {
                        module("org:platform:2.7.9")
                    }
                    module("org:platform:2.7.9")
                }
                edge("org:kotlin:2.9.4.1", "org:kotlin:2.7.9") {
                    byConstraint()
                    forced()
                    module('org:core:2.7.9')
                    module('org:annotations:2.7.9')
                    module("org:platform:2.7.9")
                }
                String expectedVariant = GradleMetadataResolveRunner.isGradleMetadataPublished() ? 'enforcedRuntimeElements' : 'enforced-platform-runtime'
                edge("org:platform:{strictly 2.7.9}", "org:platform:2.7.9") {
                    byAncestor()
                    configuration(expectedVariant)
                    constraint('org:core:2.7.9')
                    constraint('org:databind:2.7.9')
                    constraint('org:annotations:2.7.9')
                    constraint('org:kotlin:2.7.9')
                    noArtifacts()
                    module("org:platform:2.7.9")
                }
            }
        }

        where: "order of dependencies doesn't matter"
        dependencies << [
                'conf("org:core:2.9.4")',
                'conf("org:databind:2.7.9")',
                'conf("org:kotlin:2.9.4.1")',
                'conf enforcedPlatform("org:platform:2.7.9")',
        ].permutations()*.join("\n")
    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "maven")
    def "cannot force a published platform version by forcing the platform itself via a constraint"() {
        // This fails. Users can use enforcedPlatform() dependencies instead - see test above.
        // The ability to define enforcedPlatform() constraints only exists to allow the enforcing of virtual platforms.
        repository {
            ['2.7.9', '2.9.4', '2.9.4.1'].each { v ->
                path "databind:$v -> core:$v"
                path "databind:$v -> annotations:$v"
                path "kotlin:$v -> core:$v"
                path "kotlin:$v -> annotations:$v"

                platform("org", "platform", v, [
                        "org:core:$v",
                        "org:databind:$v",
                        "org:kotlin:$v",
                        "org:annotations:$v",
                ])
            }
        }

        given:
        buildFile << """
            dependencies {
                conf("org:core:2.9.4")
                conf("org:databind:2.7.9")
                conf("org:kotlin:2.9.4.1")
                constraints { conf enforcedPlatform("org:platform:2.7.9") }
            }
        """

        and:
        "a rule which infers module set from group and version"(false)

        when:
        allowAllRepositoryInteractions()
        fails ':checkDeps'

        then:
        failureCauseContains("Inconsistency between attributes of a constraint and a dependency, on attribute 'org.gradle.category' : dependency requires 'platform' while constraint required 'enforced-platform'")
    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "maven")
    @Issue("nebula-plugins/gradle-nebula-integration#51")
    def "force to higher patch version should bring the rest of aligned group up (notation=#forceNotation)"() {
        given:
        "repository simulating Jackson situation" {
            path 'com.amazonaws:aws-java-sdk-core:1.11.438 -> org:cbor:2.6.7'
            path 'com.amazonaws:aws-java-sdk-core:1.11.438 -> org:databind:2.6.7.1'
        }
        buildFile << """
            dependencies {
                conf "com.amazonaws:aws-java-sdk-core:1.11.438"
            }
            $forceNotation
        """

        and:
        "align the 'org' group only"()

        when:
        allowAllRepositoryInteractions {
            "com.amazonaws:aws-java-sdk-core:1.11.438" {
                allowAll()
            }
        }
        if (forceNotation.contains("force = ")) {
            executer.expectDeprecationWarning()
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("com.amazonaws:aws-java-sdk-core:1.11.438") {
                    edge("org:cbor:2.6.7", "org:cbor:2.8.10") {
                        byConstraint("belongs to platform org:platform:2.8.11.1")
                        maybeByConflictResolution()
                        forced()
                        module("org:core:2.8.10") {
                            byConstraint("belongs to platform org:platform:2.8.11.1")
                            maybeByConflictResolution()
                            forced()
                        }
                    }
                    edge("org:databind:2.6.7.1", "org:databind:2.8.11.1") {
                        byConstraint("belongs to platform org:platform:2.8.11.1")
                        maybeByConflictResolution()
                        forced()
                        edge("org:annotations:2.8.0", "org:annotations:2.8.10") {
                            byConstraint("belongs to platform org:platform:2.8.11.1")
                            byConflictResolution("between versions 2.8.10 and 2.8.0")
                            forced()
                        }
                        module("org:core:2.8.10")
                    }
                }
                if (forceNotation.contains("force = true")) {
                    module("org:databind:2.8.11.1")
                }
            }
        }

        where:
        forceNotation << [
                "configurations.all { resolutionStrategy { force 'org:databind:2.8.11.1' } }",
                "dependencies { conf enforcedPlatform('org:platform:2.8.11.1') }",
        ]
    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "maven")
    @Issue("nebula-plugins/gradle-nebula-integration#51")
    def "force to lower patch version should bring the rest of aligned group up (notation=#forceNotation)"() {
        given:
        "repository simulating Jackson situation" {
            path 'com.amazonaws:aws-java-sdk-core:1.11.438 -> org:cbor:2.6.7'
            path 'com.amazonaws:aws-java-sdk-core:1.11.438 -> org:databind:2.8.0'
        }
        buildFile << """
            dependencies {
                conf "com.amazonaws:aws-java-sdk-core:1.11.438"
            }
            $forceNotation
        """

        and:
        "align the 'org' group only"()

        when:
        allowAllRepositoryInteractions {
            "com.amazonaws:aws-java-sdk-core:1.11.438" {
                allowAll()
            }
        }
        if (forceNotation.contains("force =")) {
            executer.expectDeprecationWarning()
        }

        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("com.amazonaws:aws-java-sdk-core:1.11.438") {
                    module("org:cbor:2.6.7") {
                        byConstraint("belongs to platform org:platform:2.6.7.1")
                        forced()
                        module("org:core:2.6.7") {
                            byConstraint("belongs to platform org:platform:2.6.7.1")
                            forced()
                        }
                    }
                    edge("org:databind:2.8.0", "org:databind:2.6.7.1") {
                        byConstraint("belongs to platform org:platform:2.6.7.1")
                        forced()
                        edge("org:annotations:2.6.0", "org:annotations:2.6.7") {
                            byConstraint("belongs to platform org:platform:2.6.7.1")
                            byConflictResolution("between versions 2.6.7 and 2.6.0")
                            forced()
                        }
                        module("org:core:2.6.7")
                    }
                }
                if (forceNotation.contains("force = true")) {
                    module("org:databind:2.6.7.1")
                }
            }
        }

        where:
        forceNotation << [
                "configurations.all { resolutionStrategy { force 'org:databind:2.6.7.1' } }",
                "dependencies { conf enforcedPlatform('org:platform:2.6.7.1') }",
        ]
    }

    /**
     * Generates a BOM, or Gradle metadata
     * @param repo
     * @param platformGroup
     * @param platformName
     * @param platformVersion
     * @param members
     */
    void platform(RemoteRepositorySpec repo, String platformGroup, String platformName, String platformVersion, List<String> members) {
        repo.group(platformGroup) {
            module(platformName) {
                version(platformVersion) {
                    asPlatform()
                    members.each { member ->
                        constraint(member)
                    }
                }
            }
        }
    }

    void allowAllRepositoryInteractions(@DelegatesTo(value = RemoteRepositorySpec, strategy = Closure.DELEGATE_FIRST) Closure<Void> extraSpec = {}) {
        repositoryInteractions {
            group('org') {
                ['core', 'databind', 'annotations', 'kotlin', 'cbor', 'platform'].each { mod ->
                    module(mod) {
                        ['2.6.0', '2.6.7', '2.6.7.1', '2.7.9', '2.8.0', '2.8.10', '2.8.11', '2.8.11.1', '2.9.0', '2.9.7', '2.9.4', '2.9.4.1'].each { v ->
                            version(v) {
                                // Not interested in the actual interactions, especially with
                                // the complexity introduced by permutation testing
                                allowAll()
                            }
                        }
                    }
                }
            }

            extraSpec.delegate = delegate
            extraSpec.resolveStrategy = Closure.DELEGATE_FIRST
            extraSpec()
        }
    }

    void "repository simulating Jackson situation"(@DelegatesTo(value = RemoteRepositorySpec, strategy = Closure.DELEGATE_FIRST) Closure<Void> extraSpec = {}) {
        // see https://gist.github.com/melix/0f539bca5d29dafe295877ddff707e4a to generate the code below

        repository {
            "org:annotations:2.6.0"()
            "org:annotations:2.6.7"()
            "org:annotations:2.8.0"()
            "org:annotations:2.8.10"()
            "org:annotations:2.8.11"()
            "org:annotations:2.9.0"()
            "org:annotations:2.9.4"()
            "org:annotations:2.9.7"()
            "org:cbor:2.6.0"()
            "org:cbor:2.6.7"()
            "org:cbor:2.8.0"()
            "org:cbor:2.8.10"()
            "org:cbor:2.8.11"()
            "org:cbor:2.9.0"()
            "org:cbor:2.9.4"()
            "org:cbor:2.9.7"()
            "org:core:2.6.0"()
            "org:core:2.6.7"()
            "org:core:2.8.0"()
            "org:core:2.8.10"()
            "org:core:2.8.11"()
            "org:core:2.9.0"()
            "org:core:2.9.4"()
            "org:core:2.9.7"()
            "org:databind:2.6.0"()
            "org:databind:2.6.7"()
            "org:databind:2.6.7.1"()
            "org:databind:2.8.0"()
            "org:databind:2.8.10"()
            "org:databind:2.8.11"()
            "org:databind:2.8.11.1"()
            "org:databind:2.9.0"()
            "org:databind:2.9.4"()
            "org:databind:2.9.7"()
            "org:kotlin:2.6.0"()
            "org:kotlin:2.6.7"()
            "org:kotlin:2.8.0"()
            "org:kotlin:2.8.10"()
            "org:kotlin:2.8.11"()
            "org:kotlin:2.8.11.1"()
            "org:kotlin:2.9.0"()
            "org:kotlin:2.9.4"()
            "org:kotlin:2.9.4.1"()
            "org:kotlin:2.9.7"()
            path "cbor:2.6.0 -> core:2.6.0"
            path "cbor:2.6.7 -> core:2.6.7"
            path "cbor:2.8.0 -> core:2.8.0"
            path "cbor:2.8.10 -> core:2.8.10"
            path "cbor:2.8.11 -> core:2.8.11"
            path "cbor:2.9.0 -> core:2.9.0"
            path "cbor:2.9.4 -> core:2.9.4"
            path "cbor:2.9.7 -> core:2.9.7"
            path "databind:2.6.0 -> annotations:2.6.0"
            path "databind:2.6.0 -> annotations:2.6.0"
            path "databind:2.6.0 -> core:2.6.0"
            path "databind:2.6.0 -> core:2.6.0"
            path "databind:2.6.7 -> annotations:2.6.0"
            path "databind:2.6.7 -> annotations:2.6.0"
            path "databind:2.6.7 -> core:2.6.7"
            path "databind:2.6.7 -> core:2.6.7"
            path "databind:2.6.7.1 -> annotations:2.6.0"
            path "databind:2.6.7.1 -> core:2.6.7"
            path "databind:2.8.0 -> annotations:2.8.0"
            path "databind:2.8.0 -> annotations:2.8.0"
            path "databind:2.8.0 -> core:2.8.0"
            path "databind:2.8.0 -> core:2.8.0"
            path "databind:2.8.10 -> annotations:2.8.0"
            path "databind:2.8.10 -> annotations:2.8.0"
            path "databind:2.8.10 -> core:2.8.10"
            path "databind:2.8.10 -> core:2.8.10"
            path "databind:2.8.11 -> annotations:2.8.0"
            path "databind:2.8.11 -> annotations:2.8.0"
            path "databind:2.8.11 -> annotations:2.8.0"
            path "databind:2.8.11 -> core:2.8.10"
            path "databind:2.8.11 -> core:2.8.10"
            path "databind:2.8.11 -> core:2.8.10"
            path "databind:2.8.11.1 -> annotations:2.8.0"
            path "databind:2.8.11.1 -> core:2.8.10"
            path "databind:2.9.0 -> annotations:2.9.0"
            path "databind:2.9.0 -> annotations:2.9.0"
            path "databind:2.9.0 -> core:2.9.0"
            path "databind:2.9.0 -> core:2.9.0"
            path "databind:2.9.4 -> annotations:2.9.0"
            path "databind:2.9.4 -> annotations:2.9.0"
            path "databind:2.9.4 -> annotations:2.9.0"
            path "databind:2.9.4 -> core:2.9.4"
            path "databind:2.9.4 -> core:2.9.4"
            path "databind:2.9.4 -> core:2.9.4"
            path "databind:2.9.7 -> annotations:2.9.0"
            path "databind:2.9.7 -> annotations:2.9.0"
            path "databind:2.9.7 -> core:2.9.7"
            path "databind:2.9.7 -> core:2.9.7"
            path "kotlin:2.6.0 -> annotations:2.6.0"
            path "kotlin:2.6.0 -> databind:2.6.0"
            path "kotlin:2.6.7 -> annotations:2.6.0"
            path "kotlin:2.6.7 -> databind:2.6.7"
            path "kotlin:2.8.0 -> annotations:2.8.0"
            path "kotlin:2.8.0 -> databind:2.8.0"
            path "kotlin:2.8.10 -> annotations:2.8.0"
            path "kotlin:2.8.10 -> databind:2.8.10"
            path "kotlin:2.8.11 -> annotations:2.8.0"
            path "kotlin:2.8.11 -> databind:2.8.11"
            path "kotlin:2.8.11.1 -> annotations:2.8.0"
            path "kotlin:2.8.11.1 -> databind:2.8.11"
            path "kotlin:2.9.0 -> annotations:2.9.0"
            path "kotlin:2.9.0 -> databind:2.9.0"
            path "kotlin:2.9.4 -> annotations:2.9.0"
            path "kotlin:2.9.4 -> databind:2.9.4"
            path "kotlin:2.9.4.1 -> annotations:2.9.0"
            path "kotlin:2.9.4.1 -> databind:2.9.4"
            path "kotlin:2.9.7 -> annotations:2.9.0"
            path "kotlin:2.9.7 -> databind:2.9.7"

            extraSpec.delegate = delegate
            extraSpec.resolveStrategy = Closure.DELEGATE_FIRST
            extraSpec()
        }
    }
}
