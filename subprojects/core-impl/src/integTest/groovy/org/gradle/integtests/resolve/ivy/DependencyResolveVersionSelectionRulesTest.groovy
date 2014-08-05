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

    def "no version selection rules are applied when resolving a static version" () {
        ivyRepo.module("org.utils", "api", "1.3").publish()

        buildFile << """
            $baseBuildFile

            dependencies {
                conf "org.utils:api:1.3"
            }

            def ruleInvoked = false
            configurations.all {
                resolutionStrategy {
                    versionSelection {
                        anyVersion { selection ->
                            ruleInvoked = true
                        }
                    }
                }
            }

            resolveConf.doLast { assert ! ruleInvoked }
        """

        expect:
        succeeds 'resolveConf'
    }

    @Unroll
    def "version selection rules are applied when resolving #versionRequested" () {
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

            def versionsInvoked = []
            configurations.all {
                resolutionStrategy {
                    versionSelection {
                        anyVersion { VersionSelection selection ->
                            versionsInvoked.add(selection.candidate.version)
                        }
                    }
                }
            }

            resolveConf.doLast {
                def versionsExpected = ${versionsExpected}
                assert versionsInvoked.size() == versionsExpected.size()
                assert versionsInvoked.containsAll(versionsExpected)
            }
        """

        expect:
        succeeds 'resolveConf'

        where:
        versionRequested     | versionsAvailable                                                | versionsExpected
        '1.+'                | [ '2.0', '1.1', '1.0' ]                                          | "[ '2.0', '1.1' ]"
        'latest.integration' | [ '2.0', '1.1', '1.0' ]                                          | "[ '2.0' ]"
        'latest.release'     | [['2.0', 'integration'], ['1.1', 'release'], ['1.0', 'release']] | "[ '2.0', '1.1' ]"
    }
}
