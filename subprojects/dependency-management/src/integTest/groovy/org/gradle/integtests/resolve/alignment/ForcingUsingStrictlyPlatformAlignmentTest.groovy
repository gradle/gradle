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
package org.gradle.integtests.resolve.alignment

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.fixtures.publish.RemoteRepositorySpec
import org.gradle.test.fixtures.server.http.MavenHttpModule
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import spock.lang.Issue

@Requires(value = IntegTestPreconditions.IsEmbeddedExecutor, reason = """
This test is very expensive due to the permutation testing.
Because it tests the internal state of the resolution engine, the Gradle execution model does not matter.
Se we run the tests only in embedded mode
""")
class ForcingUsingStrictlyPlatformAlignmentTest extends AbstractAlignmentSpec {

    def "can force a virtual platform version by forcing one of its leaves"() {
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
                conf("org:databind") {
                    version { strictly '2.7.9' }
                }
                conf("org:kotlin:2.9.4.1")
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
                    byConstraint('belongs to platform org:platform:2.7.9')
                    forced()
                }
                edge("org:databind:{strictly 2.7.9}", "org:databind:2.7.9") {
                    byConstraint('belongs to platform org:platform:2.7.9')
                    forced()
                    module('org:annotations:2.7.9') {
                        byConstraint('belongs to platform org:platform:2.7.9')
                        forced()
                    }
                    module('org:core:2.7.9')
                }
                edge("org:kotlin:2.9.4.1", "org:kotlin:2.7.9") {
                    byConstraint('belongs to platform org:platform:2.7.9')
                    forced()
                    module('org:core:2.7.9')
                    module('org:annotations:2.7.9')
                }
            }
        }
    }

    def "fails if forcing a virtual platform version by forcing multiple leaves with different versions"() {
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
                conf("$dep1") {
                    version { strictly '$v1' }
                }
                conf("org:kotlin:2.9.4.1")

                conf("$dep2") {
                    version { strictly '$v2' }
                }
            }
        """

        and:
        "a rule which infers module set from group and version"()

        when:
        allowAllRepositoryInteractions()
        fails ':checkDeps'

        then:
        failure.assertHasCause """Cannot find a version of 'org:databind' that satisfies the version constraints:"""

        where:
        dep1           | v1      | dep2           | v2
        'org:core'     | '2.9.4' | 'org:databind' | '2.7.9'
        'org:databind' | '2.7.9' | 'org:core'     | '2.9.4'
    }

    def "fails if forcing a virtual platform version and forcing a leaf with different version"() {
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

                conf enforcedPlatform("org:platform:2.9.4")

                conf("org:kotlin:2.9.4.1")

                conf("org:databind") {
                    version { strictly '2.7.9' }
                }
            }
        """

        and:
        "a rule which infers module set from group and version"()

        when:
        allowAllRepositoryInteractions()
        fails ':checkDeps'

        then:
        failure.assertHasCause """Cannot find a version of 'org:databind' that satisfies the version constraints:
   Dependency path ':test:unspecified' --> 'org:databind:{strictly 2.7.9}'
   Constraint path ':test:unspecified' --> 'org:platform:2.9.4' (default) --> 'org:databind:2.9.4' because of the following reason: belongs to platform org:platform:2.9.4"""
    }

    def "fails if forcing a virtual platform version by forcing multiple leaves with different versions, including transitively"() {
        repository {
            ['2.7.9', '2.9.4', '2.9.4.1'].each {
                path "databind:$it -> core:$it"
                path "databind:$it -> annotations:$it"
                path "kotlin:$it -> core:$it"
                path "kotlin:$it -> annotations:$it"
            }
        }

        given:
        settingsFile << """
include 'other'
"""
        buildFile << """
            dependencies {
                conf project(path: ':other', configuration: 'conf')
                conf("org:kotlin:2.9.4.1")

                conf("org:databind") {
                    version { strictly '2.7.9' }
                }
            }

            project(':other') {
                configurations {
                    conf
                }
                dependencies {
                    conf("org:core") {
                        version { strictly '2.9.4' }
                    }
                    components.all(InferModuleSetFromGroupAndVersion)
                }
            }
        """

        and:
        "a rule which infers module set from group and version"()

        when:
        allowAllRepositoryInteractions()
        fails ':checkDeps'

        then:
        def coreVariant = GradleMetadataResolveRunner.gradleMetadataPublished || GradleMetadataResolveRunner.useMaven() ? 'runtime' : 'default'
        failure.assertHasCause """Cannot find a version of 'org:databind' that satisfies the version constraints:
   Dependency path ':test:unspecified' --> 'org:databind:{strictly 2.7.9}'
   Constraint path ':test:unspecified' --> 'test:other:unspecified' (conf) --> 'org:core:2.9.4' ($coreVariant) --> 'org:platform:2.9.4' (default) --> 'org:databind:2.9.4' because of the following reason: belongs to platform org:platform:2.9.4"""
    }

    def "succeeds if forcing a virtual platform version by forcing multiple leaves with same version"() {
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
                conf("org:core") {
                    version { strictly '2.7.9' }
                }
                conf("org:kotlin:2.9.4.1")

                conf("org:databind") {
                    version { strictly '2.7.9' }
                }
            }
        """

        and:
        "a rule which infers module set from group and version"()

        expect:
        allowAllRepositoryInteractions()
        succeeds ':checkDeps'
    }

    def "succeeds if forcing a virtual platform version and forcing a leaf with same version"() {
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

                conf enforcedPlatform("org:platform:2.7.9")

                conf("org:kotlin:2.9.4.1")

                conf("org:databind") {
                    version { strictly '2.7.9' }
                }
            }
        """

        and:
        "a rule which infers module set from group and version"()

        expect:
        allowAllRepositoryInteractions()
        succeeds ':checkDeps'
    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "maven")
    @Issue("nebula-plugins/gradle-nebula-integration#51")
    def "force to higher patch version should bring the rest of aligned group up"() {
        given:
        "repository simulating Jackson situation" {
            path 'com.amazonaws:aws-java-sdk-core:1.11.438 -> org:cbor:2.6.7'
            path 'com.amazonaws:aws-java-sdk-core:1.11.438 -> org:databind:2.6.7.1'
        }
        buildFile << """
            dependencies {
                conf "com.amazonaws:aws-java-sdk-core:1.11.438"
            }
            dependencies { conf('org:databind') { version { strictly '2.8.11.1' } } }
        """

        and:
        "align the 'org' group only"()

        when:
        allowAllRepositoryInteractions {
            "com.amazonaws:aws-java-sdk-core:1.11.438" {
                allowAll()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("com.amazonaws:aws-java-sdk-core:1.11.438") {
                    edge("org:cbor:2.6.7", "org:cbor:2.8.10") {
                        byConstraint("belongs to platform org:platform:2.8.11.1")
                        byConflictResolution("between versions 2.8.10 and 2.6.7")
                        forced()
                        module("org:core:2.8.10") {
                            byConstraint("belongs to platform org:platform:2.8.11.1")
                            byConflictResolution("between versions 2.8.10 and 2.6.7")
                            forced()
                        }
                    }
                    edge("org:databind:2.6.7.1", "org:databind:2.8.11.1") {
                        byConstraint("belongs to platform org:platform:2.8.11.1")
                        byAncestor()
                        forced()
                        edge("org:annotations:2.8.0", "org:annotations:2.8.10") {
                            byConstraint("belongs to platform org:platform:2.8.11.1")
                            byConflictResolution("between versions 2.8.10 and 2.8.0")
                            forced()
                        }
                        module("org:core:2.8.10")
                    }
                }
                edge("org:databind:{strictly 2.8.11.1}", "org:databind:2.8.11.1")
            }
        }

    }


    @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "maven")
    @Issue("nebula-plugins/gradle-nebula-integration#51")
    def "force to lower patch version should bring the rest of aligned group up"() {
        given:
        "repository simulating Jackson situation" {
            path 'com.amazonaws:aws-java-sdk-core:1.11.438 -> org:cbor:2.6.7'
            path 'com.amazonaws:aws-java-sdk-core:1.11.438 -> org:databind:2.8.0'
        }
        buildFile << """
            dependencies {
                conf "com.amazonaws:aws-java-sdk-core:1.11.438"
            }
            dependencies { conf('org:databind') { version { strictly '2.6.7.1' } } }
        """

        and:
        "align the 'org' group only"()

        when:
        allowAllRepositoryInteractions {
            "com.amazonaws:aws-java-sdk-core:1.11.438" {
                allowAll()
            }
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
                        byAncestor()
                        forced()
                        edge("org:annotations:2.6.0", "org:annotations:2.6.7") {
                            byConstraint("belongs to platform org:platform:2.6.7.1")
                            byConflictResolution("between versions 2.6.7 and 2.6.0")
                            forced()
                        }
                        module("org:core:2.6.7")
                    }
                }
                edge("org:databind:{strictly 2.6.7.1}", "org:databind:2.6.7.1")
            }
        }

    }

    def setup() {
        repoSpec.metaClass.platform = this.&platform.curry(repoSpec)
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
                    variant("platform") {
                        attribute('org.gradle.category', 'platform')
                        members.each { member ->
                            constraint(member)
                        }
                        noArtifacts = true
                    }
                    // this is used only in BOMs
                    members.each { member ->
                        constraint(member)
                    }

                    withModule(MavenHttpModule) {
                        // make it a BOM
                        hasPackaging('pom')
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
