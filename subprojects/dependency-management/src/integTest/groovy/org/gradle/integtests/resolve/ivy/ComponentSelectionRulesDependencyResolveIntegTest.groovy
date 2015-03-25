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

import spock.lang.Issue
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

        when:
        server.resetExpectations()

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

        then:
        fails 'resolveConf'
        failureHasCause("Could not find any version that matches org.utils:api:${selector}.")

        when:
        server.resetExpectations()
        ivyHttpRepo.directoryList("org.utils", "api").expectGet()

        then:
        fails 'resolveConf'
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

    def "reports all candidates rejected by rule" () {
        buildFile << """
            $httpBaseBuildFile

            dependencies {
                conf "org.utils:api:1.+"
            }

            configurations.all {
                resolutionStrategy {
                    componentSelection {
                        all ${rules["reject all with metadata"]}
                    }
                }
            }
"""

        when:
        def dirList = ivyHttpRepo.directoryList("org.utils", "api")
        dirList.expectGet()
        modules["1.2"].ivy.expectGet()
        modules["1.1"].ivy.expectGet()
        modules["1.0"].ivy.expectGet()

        then:
        fails 'resolveConf'

        and:
        failureHasCause("""Could not find any version that matches org.utils:api:1.+.
Versions that do not match:
    2.1
    2.0
Versions rejected by component selection rules:
    1.2
    1.1
    1.0
Searched in the following locations:
    ${dirList.uri}
    ${modules["1.2"].ivy.uri}
    ${modules["1.1"].ivy.uri}
    ${modules["1.0"].ivy.uri}
Required by:
""")

        when:
        server.resetExpectations()
        dirList.expectGet()

        then:
        fails 'resolveConf'

        and:
        // TODO - this failure and the previous failure should report the same urls (whatever that happens to be)
        failureHasCause("""Could not find any version that matches org.utils:api:1.+.
Versions that do not match:
    2.1
    2.0
Versions rejected by component selection rules:
    1.2
    1.1
    1.0
Searched in the following locations:
    ${dirList.uri}
Required by:
""")
    }

    @Unroll
    def "uses '#rule' rule to reject candidate for static version #selector" () {
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
        failureHasCause("Could not find org.utils:api:${selector}.")

        when:
        server.resetExpectations()

        then:
        fails 'resolveConf'
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
                        withModule("some.other:module") { ComponentSelection cs ->
                            throw new RuntimeException()
                        }
                        withModule("some.other:module") { ComponentSelection cs, IvyModuleDescriptor descriptor, ComponentMetadata metadata ->
                            throw new RuntimeException()
                        }
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

    @Issue("GRADLE-3236")
    def "can select a different component for the same selector in different configurations" () {
        buildFile << """
            $httpBaseBuildFile

            configurations {
                modules
                modulesA {
                    extendsFrom modules
                    resolutionStrategy {
                        componentSelection {
                            all { ComponentSelection selection, IvyModuleDescriptor ivy ->
                                println "A is evaluating \$selection.candidate"
                                if (selection.candidate.version != "1.1") { selection.reject("Rejected by A") }
                            }
                        }
                    }
                }
                modulesB {
                    extendsFrom modules
                    resolutionStrategy {
                        componentSelection {
                            all { ComponentSelection selection, IvyModuleDescriptor ivy ->
                                println "B is evaluating \$selection.candidate"
                                if (selection.candidate.version != "1.0") { selection.reject("Rejected by B") }
                            }
                        }
                    }
                }
            }

            dependencies {
                modules "org.utils:api:1.+"
            }

            task verify {
                doLast {
                    assert configurations.modulesA.files.collect { it.name } == [ 'api-1.1.jar']
                    assert configurations.modulesB.files.collect { it.name } == [ 'api-1.0.jar']
                }
            }
        """

        when:
        ivyHttpRepo.directoryList("org.utils", "api").expectGet()
        modules['1.2'].ivy.expectGet()
        modules['1.1'].ivy.expectGet()
        modules['1.0'].ivy.expectGet()
        modules['1.1'].artifact.expectGet()
        modules['1.0'].artifact.expectGet()

        then:
        succeeds "verify"

        when:
        server.resetExpectations()

        then:
        succeeds "verify"
    }
}
