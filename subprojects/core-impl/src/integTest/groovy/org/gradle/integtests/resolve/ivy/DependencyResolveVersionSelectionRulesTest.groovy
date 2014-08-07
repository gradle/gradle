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
}
