/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.integtests.resolve.ivy

import spock.lang.Unroll

class ComponentSelectionRulesDependencyResolveIntegTest extends AbstractComponentSelectionRulesIntegrationTest {
    @Unroll
    def "uses '#rule' rule to choose component for #selector" () {

        buildFile << """
            $httpBaseBuildFile

            dependencies {
                conf "org.utils:api:${selector}"
            }

            configurations.all {
                resolutionStrategy {
                    componentSelection {
                        all ${rules[rule]}
                    }
                }
            }

            resolveConf.doLast {
                def artifacts = configurations.conf.resolvedConfiguration.resolvedArtifacts
                assert artifacts.size() == 1
                assert artifacts[0].moduleVersion.id.version == '${chosen}'
                assert candidates == ${candidates}
            }
"""

        when:
        if (selector != "1.1") {
            ivyHttpRepo.directoryList("org.utils", "api").expectGet()
        }
        downloadedMetadata.each {
            modules[it].ivy.expectGet()
        }
        modules[chosen].artifact.expectGet()

        then:
        succeeds 'resolveConf'

        where:
        selector             | rule            | chosen | candidates                            | downloadedMetadata
        "1.+"                | "select 1.1"    | "1.1"  | '["1.2", "1.1"]'                      | ['1.1']
        "1.+"                | "select status" | "1.1"  | '["1.2", "1.1"]'                      | ['1.2', '1.1']
        "1.+"                | "select branch" | "1.1"  | '["1.2", "1.1"]'                      | ['1.2', '1.1']
        "latest.integration" | "select 2.1"    | "2.1"  | '["2.1"]'                             | ['2.1']
        "latest.milestone"   | "select 2.0"    | "2.0"  | '["2.0"]'                             | ['2.1', '2.0']
        "latest.milestone"   | "select status" | "2.0"  | '["2.0"]'                             | ['2.1', '2.0']
        "latest.milestone"   | "select branch" | "2.0"  | '["2.0"]'                             | ['2.1', '2.0']
        "1.1"                | "select 1.1"    | "1.1"  | '["1.1"]'                             | ['1.1']
        "1.1"                | "select status" | "1.1"  | '["1.1"]'                             | ['1.1']
        "1.1"                | "select branch" | "1.1"  | '["1.1"]'                             | ['1.1']
    }

    @Unroll
    def "uses '#rule' rule to reject all candidates for dynamic version #selector" () {
        buildFile << """
            $httpBaseBuildFile

            dependencies {
                conf "org.utils:api:${selector}"
            }

            configurations.all {
                resolutionStrategy {
                    componentSelection {
                        all ${rules[rule]}
                    }
                }
            }

            task checkConf << {
                def artifacts = configurations.conf.resolvedConfiguration.lenientConfiguration.getArtifacts(Specs.SATISFIES_ALL)
                assert artifacts.size() == 0
                assert candidates == ${candidates}
            }
"""

        when:
        ivyHttpRepo.directoryList("org.utils", "api").expectGet()
        downloadedMetadata.each {
            modules[it].ivy.expectGet()
        }

        then:
        succeeds 'checkConf'

        when:
        server.resetExpectations()
        ivyHttpRepo.directoryList("org.utils", "api").expectGet()
        downloadedMetadata.each {
            modules[it].ivy.expectHead()
        }

        then:
        fails 'resolveConf'
        failureDescriptionStartsWith("Execution failed for task ':resolveConf'.")
        failureHasCause("Could not resolve all dependencies for configuration ':conf'.")
        failureHasCause("Could not find any version that matches org.utils:api:${selector}.")

        where:
        selector             | rule                       | candidates                            | downloadedMetadata
        "1.+"                | "reject all"               | '["1.2", "1.1", "1.0"]'               | []
        "latest.integration" | "reject all"               | '["2.1"]'                             | ['2.1']
        "latest.milestone"   | "reject all"               | '["2.0"]'                             | ['2.1', '2.0']
        "1.+"                | "reject all with metadata" | '["1.2", "1.1", "1.0"]'               | ['1.2', '1.1', '1.0']
        "latest.integration" | "reject all with metadata" | '["2.1"]'                             | ['2.1']
        "latest.milestone"   | "reject all with metadata" | '["2.0"]'                             | ['2.1', '2.0']
        // latest.milestone is 2.0, but since the rule rejects it, we should never reach version 1.1
        "latest.milestone"   | "select 1.1"               | '["2.0"]'                             | ['2.1', '2.0']
    }

    @Unroll
    def "uses '#rule' rule to reject all candidates for static version #selector" () {
        buildFile << """
            $httpBaseBuildFile

            dependencies {
                conf "org.utils:api:${selector}"
            }

            configurations.all {
                resolutionStrategy {
                    componentSelection {
                        all ${rules[rule]}
                    }
                }
            }

            task checkConf << {
                def artifacts = configurations.conf.resolvedConfiguration.lenientConfiguration.getArtifacts(Specs.SATISFIES_ALL)
                assert artifacts.size() == 0
                assert candidates == ${candidates}
            }
"""

        when:
        downloadedMetadata.each {
            modules[it].ivy.expectGet()
        }

        then:
        succeeds 'checkConf'

        when:
        server.resetExpectations()

        then:
        fails 'resolveConf'
        failureDescriptionStartsWith("Execution failed for task ':resolveConf'.")
        failureHasCause("Could not resolve all dependencies for configuration ':conf'.")
        failureHasCause("Could not find org.utils:api:${selector}.")

        where:
        selector             | rule            | candidates                            | downloadedMetadata
        "1.0"                | "reject all"    | '["1.0"]'                             | ['1.0']
        "1.0"                | "select 1.1"    | '["1.0"]'                             | ['1.0']
        "1.0"                | "select status" | '["1.0"]'                             | ['1.0']
        "1.0"                | "select branch" | '["1.0"]'                             | ['1.0']
        "1.1"                | "reject all"    | '["1.1"]'                             | ['1.1']
    }

    @Unroll
    def "can use component selection rule to choose component from different repository for #selector"() {
        def ivyRepo2 = ivyRepo("repo2")
        def module2 = ivyRepo2.module("org.utils", "api", "1.1").withBranch("other").publishWithChangedContent()

        buildFile << """
            configurations { conf }
            repositories {
                ivy { url "${ivyRepo.uri}" }
                ivy { url "${ivyRepo2.uri}" }
            }

            dependencies {
                conf "org.utils:api:${selector}"
            }

            configurations.all {
                resolutionStrategy {
                    componentSelection {
                        all { ComponentSelection selection, IvyModuleDescriptor ivy ->
                            if (ivy.branch != "other") {
                                selection.reject("looking for other")
                            }
                        }
                    }
                }
            }

            task retrieve(type: Copy) {
                from configurations.conf
                into "libs"
            }
"""
        when:
        succeeds "retrieve"

        then:
        file("libs").assertHasDescendants("api-1.1.jar")
        file("libs/api-1.1.jar").assertIsDifferentFrom(modules['1.1'].jarFile)
        file("libs/api-1.1.jar").assertIsCopyOf(module2.jarFile)

        where:
        selector << ["1.1", "1.+"]
    }

    @Unroll
    def "can control selection of components by module for #selector" () {
        buildFile << """
            $httpBaseBuildFile

            dependencies {
                conf "org.utils:api:${selector}"
                conf "org.utils:lib:1.+"
            }

            configurations.all {
                resolutionStrategy {
                    componentSelection {
                        withModule("org.utils:api") ${rules[rule]}
                        withModule("org.utils:api") { ComponentSelection cs ->
                            assert cs.candidate.group == "org.utils"
                            assert cs.candidate.module == "api"
                        }
                        withModule("some.other:module") { ComponentSelection cs -> }
                        withModule("some.other:module") { ComponentSelection cs, IvyModuleDescriptor descriptor, ComponentMetadata metadata -> }
                    }
                }
            }

            resolveConf.doLast {
                def artifacts = configurations.conf.resolvedConfiguration.resolvedArtifacts
                assert artifacts.size() == 2
                assert artifacts[0].moduleVersion.id.version == '${chosen}'
                assert candidates == ${candidates}
            }
        """

        when:
        if (selector != "1.1") {
            ivyHttpRepo.directoryList("org.utils", "api").expectGet()
        }
        downloadedMetadata.each {
            modules[it].ivy.expectGet()
        }
        modules[chosen].artifact.expectGet()

        ivyHttpRepo.directoryList("org.utils", "lib").expectGet()
        modules["1.1-lib"].ivy.expectGet()
        modules["1.1-lib"].artifact.expectGet()

        then:
        succeeds 'resolveConf'

        where:
        selector             | rule            | chosen | candidates                            | downloadedMetadata
        "1.+"                | "select 1.1"    | "1.1"  | '["1.2", "1.1"]'                      | ['1.1']
        "latest.milestone"   | "select status" | "2.0"  | '["2.0"]'                             | ['2.1', '2.0']
        "1.1"                | "select branch" | "1.1"  | '["1.1"]'                             | ['1.1']
    }
}
