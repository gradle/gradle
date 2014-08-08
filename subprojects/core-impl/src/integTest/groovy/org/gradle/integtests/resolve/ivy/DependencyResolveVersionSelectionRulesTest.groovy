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

import org.gradle.api.artifacts.VersionSelection
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Unroll

class DependencyResolveVersionSelectionRulesTest extends AbstractIntegrationSpec {

    String getBaseBuildFile() {
        """
        configurations { conf }
        repositories {
            ivy { url "${ivyRepo.uri}" }
        }
        task resolveConf << { configurations.conf.files }
        """
    }

    def "resolving a static version with version selection rules resolves properly" () {
        ivyRepo.module("org.utils", "api", "2.0").publish()
        ivyRepo.module("org.utils", "api", "1.3").publish()
        ivyRepo.module("org.utils", "api", "1.1").publish()

        buildFile << """
            $baseBuildFile

            dependencies {
                conf "org.utils:api:1.3"
            }

            configurations.all {
                resolutionStrategy {
                    versionSelection {
                        all { selection ->
                            println selection.candidate.version
                        }
                    }
                }
            }

            resolveConf.doLast {
                assert configurations.conf.resolvedConfiguration.resolvedArtifacts.size() == 1
                assert configurations.conf.resolvedConfiguration.resolvedArtifacts[0].moduleVersion.id.version == '1.3'
            }
        """

        expect:
        succeeds 'resolveConf'
    }

    @Unroll
    def "all version selection rules are applied when resolving #versionRequested" () {
        versionsAvailable.each { v ->
            if (v instanceof List) {
                ivyRepo.module("org.utils", "api", v[0]).withStatus(v[1])publish()
            } else {
                ivyRepo.module("org.utils", "api", v).publish()
            }
        }

        buildFile << """
            $baseBuildFile

            dependencies {
                conf "org.utils:api:${versionRequested}"
            }

            def rule1VersionsInvoked = []
            def rule2VersionsInvoked = []
            configurations.all {
                resolutionStrategy {
                    versionSelection {
                        // Rule 1
                        all { VersionSelection selection ->
                            rule1VersionsInvoked.add(selection.candidate.version)
                        }
                        // Rule 2
                        all { VersionSelection selection ->
                            rule2VersionsInvoked.add(selection.candidate.version)
                        }
                    }
                }
            }

            resolveConf.doLast {
                def versionsExpected = ${versionsExpected}
                assert rule1VersionsInvoked.size() == versionsExpected.size()
                assert rule1VersionsInvoked.containsAll(versionsExpected)
                assert rule2VersionsInvoked.size() == versionsExpected.size()
                assert rule2VersionsInvoked.containsAll(versionsExpected)
            }
        """

        expect:
        succeeds 'resolveConf'

        where:
        versionRequested     | versionsAvailable                                                | versionsExpected
        '1.+'                | [ '2.0', '1.1', '1.0' ]                                          | "[ '2.0', '1.1' ]"
        'latest.integration' | [ '2.0', '1.1', '1.0' ]                                          | "[ '2.0' ]"
        'latest.release'     | [['2.0', 'integration'], ['1.1', 'release'], ['1.0', 'release']] | "[ '2.0', '1.1' ]"
        '1.0'                | [ '2.0', '1.1', '1.0' ]                                          | "[ '2.0', '1.1', '1.0' ]"
        '1.1'                | [ '2.0', '1.1', '1.0' ]                                          | "[ '2.0', '1.1' ]"
    }

    def "produces sensible error when bad code is supplied in version selection rule" () {
        ivyRepo.module("org.utils", "api", "1.3").publish()

        buildFile << """
            $baseBuildFile

            dependencies {
                conf "org.utils:api:1.3"
            }

            configurations.all {
                resolutionStrategy {
                    versionSelection {
                        all { VersionSelection selection ->
                            foo()
                        }
                    }
                }
            }
        """

        expect:
        fails 'resolveConf'
        failure.assertHasDescription("Execution failed for task ':resolveConf'.")
        failure.assertHasLineNumber(18)
        failure.assertHasCause("Could not apply version selection rule with all().")
        failure.assertHasCause("Could not find method foo()")
    }

    def "produces sensible error when two rules set the version selection state differently" () {
        ivyRepo.module("org.utils", "api", "1.3").publish()

        buildFile << """
            $baseBuildFile

            dependencies {
                conf "org.utils:api:1.3"
            }

            def rule1VersionsInvoked = []
            def rule2VersionsInvoked = []
            configurations.all {
                resolutionStrategy {
                    versionSelection {
                        // Rule 1
                        all { VersionSelection selection ->
                            selection.accept()
                        }
                        // Rule 2
                        all { VersionSelection selection ->
                            selection.reject()
                        }
                    }
                }
            }
        """

        expect:
        fails 'resolveConf'
        failure.assertHasDescription("Execution failed for task ':resolveConf'.")
        failure.assertHasLineNumber(25)
        failure.assertHasCause("Could not apply version selection rule with all().")
        failure.assertHasCause("Once a version selection has been accepted or rejected, it cannot be changed.")
    }

    def "two version selection rules can set the state to the same thing" () {
        ivyRepo.module("org.utils", "api", "1.3").publish()
        ivyRepo.module("org.utils", "api", "1.2").publish()

        buildFile << """
            $baseBuildFile

            dependencies {
                conf "org.utils:api:1.+"
            }

            def rule1VersionsInvoked = []
            def rule2VersionsInvoked = []
            configurations.all {
                resolutionStrategy {
                    versionSelection {
                        // Rule 1
                        all { VersionSelection selection ->
                            if (selection.candidate.version == '1.3') {
                                selection."${operation}"()
                            }
                        }
                        // Rule 2
                        all { VersionSelection selection ->
                            if (selection.candidate.version == '1.3') {
                                selection."${operation}"()
                            }
                        }
                    }
                }
            }
        """

        expect:
        succeeds 'resolveConf'

        where:
        operation | _
        "accept"  | _
        "reject"  | _
    }

    def "rejects all versions by rule" () {
        ivyRepo.module("org.utils", "api", "1.0").publish()
        ivyRepo.module("org.utils", "api", "1.1").publish()
        ivyRepo.module("org.utils", "api", "2.0").publish()

        buildFile << """
            $baseBuildFile

            dependencies {
                conf "org.utils:api:1.0"
            }

            configurations.all {
                resolutionStrategy {
                    versionSelection {
                        all { VersionSelection selection ->
                            selection.reject()
                        }
                    }
                }
            }
        """

        expect:
        fails 'resolveConf'
        failure.assertHasDescription("Execution failed for task ':resolveConf'.")
        failure.assertHasCause("Could not find any version that matches org.utils:api:1.0.")
    }

    private static def rules = [
            "always select 2.0": """{ VersionSelection selection ->
                if (selection.candidate.version == '2.0') {
                    selection.accept()
                }
                ruleInvoked = true
            }
            """,
            "always reject 1.1": """{ VersionSelection selection ->
                if (selection.candidate.version == '1.1') {
                    selection.reject()
                }
                ruleInvoked = true
            }
            """,
            "never select or reject any version": """{ VersionSelection selection ->
                ruleInvoked = true
            }
            """,
    ]

    @Unroll
    def "uses selection rule '#rule' to select a particular version" () {
        ivyRepo.module("org.utils", "api", "1.0").withStatus("release").publish()
        ivyRepo.module("org.utils", "api", "1.1").withStatus("milestone").publish()
        ivyRepo.module("org.utils", "api", "2.0").publish()

        buildFile << """
            $baseBuildFile

            dependencies {
                conf "org.utils:api:${requestedVersion}"
            }

            def ruleInvoked = false
            configurations.all {
                resolutionStrategy {
                    versionSelection {
                        all ${rules[rule]}
                    }
                }
            }

            resolveConf.doLast {
                assert ruleInvoked == true
                configurations.conf.files.each { println it }
                assert configurations.conf.resolvedConfiguration.resolvedArtifacts.size() == 1
                assert configurations.conf.resolvedConfiguration.resolvedArtifacts[0].moduleVersion.id.version == '${expectedVersion}'
            }
        """

        expect:
        succeeds 'resolveConf'

        where:
        requestedVersion     | rule                                 | expectedVersion
        "1.0"                | "always select 2.0"                  | "2.0"
        "1.+"                | "always select 2.0"                  | "2.0"
        "1.+"                | "always reject 1.1"                  | "1.0"
        "1.0"                | "never select or reject any version" | "1.0"
        "1.+"                | "never select or reject any version" | "1.1"
        "latest.integration" | "never select or reject any version" | "2.0"
        "latest.milestone"   | "never select or reject any version" | "1.1"
    }
}
