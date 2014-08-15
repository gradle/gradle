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

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.hamcrest.Matchers
import spock.lang.Unroll

class DependencyResolveVersionSelectionMetadataIntegrationTest extends AbstractHttpDependencyResolutionTest {

    String getBaseBuildFile() {
        """
        configurations { conf }
        repositories {
            ivy { url "${ivyRepo.uri}" }
        }
        task resolveConf << { configurations.conf.files }
        """
    }

    String getHttpBaseBuildFile() {
        """
        configurations { conf }
        repositories {
            ivy { url "${ivyHttpRepo.uri}" }
        }
        task resolveConf << { configurations.conf.files }
        """
    }

    def "component metadata is not requested for rule that doesn't require it" () {
        def modules = [:]
        modules["1.0"] = ivyHttpRepo.module("org.utils", "api", "1.0").publish()
        modules["1.1"] = ivyHttpRepo.module("org.utils", "api", "1.1").publish()
        modules["2.0"] = ivyHttpRepo.module("org.utils", "api", "2.0").publish()

        // Expect the version listing and download for 1.1, but not anything else
        ivyHttpRepo.directoryList("org.utils", "api").expectGet()
        modules["1.1"].ivy.expectDownload()
        modules["1.1"].artifact.expectDownload()

        buildFile << """
            $httpBaseBuildFile

            dependencies {
                conf "org.utils:api:1.+"
            }

            def ruleInvoked = false
            configurations.all {
                resolutionStrategy {
                    versionSelection {
                        all { VersionSelection vs ->
                            ruleInvoked = true
                        }
                    }
                }
            }

            resolveConf.doLast {
                assert ruleInvoked
            }
        """

        expect:
        succeeds 'resolveConf'
        // Should use cache
        succeeds 'resolveConf'
    }

    def "component metadata is requested only once for rules that do require it" () {
        def modules = [:]
        modules["1.0"] = ivyHttpRepo.module("org.utils", "api", "1.0").withStatus("release").publish()
        modules["1.1"] = ivyHttpRepo.module("org.utils", "api", "1.1").withStatus("milestone").publish()
        modules["2.0"] = ivyHttpRepo.module("org.utils", "api", "2.0").publish()

        // Expect the version listing and metadata download for all versions once,
        // plus artifact download of 1.0 but not anything else
        ivyHttpRepo.directoryList("org.utils", "api").expectGet()
        modules.each { version, module -> module.ivy.expectDownload() }
        modules["1.0"].artifact.expectDownload()

        buildFile << """
            $httpBaseBuildFile

            dependencies {
                conf "org.utils:api:latest.release"
            }

            def rule1Invoked = false
            def rule2Invoked = false
            configurations.all {
                resolutionStrategy {
                    versionSelection {
                        all { VersionSelection vs, IvyModuleDescriptor imd, ComponentMetadata cm ->
                            rule1Invoked = true
                        }
                        all { VersionSelection vs, ComponentMetadata cm ->
                            rule2Invoked = true
                        }
                    }
                }
            }

            resolveConf.doLast {
                assert rule1Invoked
                assert rule2Invoked
            }
        """

        expect:
        succeeds 'resolveConf'
        // Should use cache
        succeeds 'resolveConf'
    }

    def "changed component metadata becomes visible when module is refreshed" () {
        def modules = [:]
        modules["1.0"] = ivyHttpRepo.module("org.utils", "api", "1.0").publish()
        modules["1.1"] = ivyHttpRepo.module("org.utils", "api", "1.1").withBranch('test').withStatus('milestone').publish()
        modules["2.0"] = ivyHttpRepo.module("org.utils", "api", "2.0").publish()

        // Expect the version listing and ivy download for 1.1 and 2.0, plus
        // artifact download of 1.1
        ivyHttpRepo.directoryList("org.utils", "api").expectGet()
        modules["2.0"].ivy.expectDownload()
        modules["1.1"].ivy.expectDownload()
        modules["1.1"].artifact.expectDownload()

        def commonBuildFile = """
            $httpBaseBuildFile

            dependencies {
                conf "org.utils:api:1.+"
            }

            def ruleInvoked = false
            def status = null
            def branch = null
            configurations.all {
                resolutionStrategy {
                    versionSelection {
                        all { VersionSelection selection, IvyModuleDescriptor descriptor, ComponentMetadata metadata ->
                            if (selection.candidate.version == '1.1') {
                                status = metadata.status
                                branch = descriptor.branch
                            }
                            ruleInvoked = true
                        }
                    }
                }
            }
        """

        when:
        buildFile << """
            $commonBuildFile

            resolveConf.doLast {
                assert ruleInvoked
                assert status == 'milestone'
                assert branch == 'test'
            }
        """

        then:
        succeeds 'resolveConf'

        when:
        modules["1.1"] = ivyHttpRepo.module("org.utils", "api", "1.1").withBranch('master').withStatus('release').publishWithChangedContent()

        and:
        server.resetExpectations()

        then:
        // Everything should come from cache
        succeeds 'resolveConf'

        when:
        args("--refresh-dependencies")
        buildFile.text = """
            $commonBuildFile

            resolveConf.doLast {
                assert ruleInvoked
                assert status == 'release'
                assert branch == 'master'
            }
        """

        and:
        server.resetExpectations()
        // Expect the version listing, HEAD for 2.0, and full download for changed 1.1
        ivyHttpRepo.directoryList("org.utils", "api").expectGet()
        modules["2.0"].ivy.expectMetadataRetrieve()
        modules["1.1"].ivy.expectMetadataRetrieve()
        modules["1.1"].ivy.sha1.expectGet()
        modules["1.1"].ivy.expectDownload()
        modules["1.1"].artifact.expectMetadataRetrieve()
        modules["1.1"].artifact.sha1.expectGet()
        modules["1.1"].artifact.expectDownload()

        then:
        succeeds 'resolveConf'
    }

    @Unroll
    def "all version selection rules requiring metadata are applied when resolving #versionRequested" () {
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
            def rule3VersionsInvoked = []
            def rule4VersionsInvoked = []
            configurations.all {
                resolutionStrategy {
                    versionSelection {
                        // Rule 1
                        all { VersionSelection selection, ComponentMetadata metadata ->
                            rule1VersionsInvoked.add(selection.candidate.version)
                        }
                        // Rule 2
                        all { VersionSelection selection, IvyModuleDescriptor descriptor ->
                            rule2VersionsInvoked.add(selection.candidate.version)
                        }
                        // Rule 3
                        all { VersionSelection selection, IvyModuleDescriptor descriptor, ComponentMetadata metadata ->
                            rule3VersionsInvoked.add(selection.candidate.version)
                        }
                        // Rule 4
                        all { VersionSelection selection, ComponentMetadata metadata, IvyModuleDescriptor descriptor ->
                            rule4VersionsInvoked.add(selection.candidate.version)
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

                assert rule3VersionsInvoked.size() == versionsExpected.size()
                assert rule3VersionsInvoked.containsAll(versionsExpected)

                assert rule4VersionsInvoked.size() == versionsExpected.size()
                assert rule4VersionsInvoked.containsAll(versionsExpected)

                assert configurations.conf.resolvedConfiguration.resolvedArtifacts.size() == 1
                assert configurations.conf.resolvedConfiguration.resolvedArtifacts[0].moduleVersion.id.version == '${resolutionExpected}'
            }
        """

        expect:
        succeeds 'resolveConf'

        where:
        versionRequested     | versionsAvailable                                                | versionsExpected          | resolutionExpected
        '1.+'                | [ '2.0', '1.1', '1.0' ]                                          | "[ '2.0', '1.1' ]"        | '1.1'
        'latest.integration' | [ '2.0', '1.1', '1.0' ]                                          | "[ '2.0' ]"               | '2.0'
        'latest.release'     | [['2.0', 'integration'], ['1.1', 'release'], ['1.0', 'release']] | "[ '2.0', '1.1' ]"        | '1.1'
        '1.0'                | [ '2.0', '1.1', '1.0' ]                                          | "[ '2.0', '1.1', '1.0' ]" | '1.0'
        '1.1'                | [ '2.0', '1.1', '1.0' ]                                          | "[ '2.0', '1.1' ]"        | '1.1'
    }

    def static rules = [
        "always select if ivy branch is test": """{ VersionSelection selection, IvyModuleDescriptor ivyModule, ComponentMetadata cm ->
            if (ivyModule.branch == 'test') {
                selection.accept()
            }
            ruleInvoked = true
        }""",
        "always select if status is milestone": """{ VersionSelection selection, ComponentMetadata metadata ->
            if (metadata.status == 'milestone') {
                selection.accept()
            }
            ruleInvoked = true
        }""",
        "always select if ivy branch is release and status is milestone": """{ VersionSelection selection, IvyModuleDescriptor ivyModule, ComponentMetadata cm ->
            if (ivyModule.branch == 'release' && cm.status == 'milestone') {
                selection.accept()
            }
            ruleInvoked = true
        }""",
        "always reject if ivy branch is release": """{ VersionSelection selection, IvyModuleDescriptor ivyModule, ComponentMetadata cm ->
            if (ivyModule.branch == 'release') {
                selection.reject()
            }
            ruleInvoked = true
        }""",
        "always reject if status is integration": """{ VersionSelection selection, ComponentMetadata metadata ->
            if (metadata.status == 'integration') {
                selection.reject()
            }
            ruleInvoked = true
        }""",
        "always reject if ivy branch is release and status is milestone": """{ VersionSelection selection, IvyModuleDescriptor ivyModule, ComponentMetadata cm ->
            if (ivyModule.branch == 'release' && cm.status == 'milestone') {
                selection.reject()
            }
            ruleInvoked = true
        }""",
    ]

    def "uses selection rule '#rule' to select a particular version" () {
        ivyRepo.module("org.utils", "api", "1.0").withStatus("release").publish()
        ivyRepo.module("org.utils", "api", "1.1").withStatus("milestone").withBranch('release').publish()
        ivyRepo.module("org.utils", "api", "2.0").withBranch("test").publish()

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
        requestedVersion     | rule                                                             | expectedVersion
        "1.0"                | "always select if ivy branch is test"                            | "2.0"
        "1.+"                | "always select if ivy branch is test"                            | "2.0"
        "latest.milestone"   | "always select if ivy branch is test"                            | "2.0"
        "latest.release"     | "always select if status is milestone"                           | "1.1"
        "latest.release"     | "always select if ivy branch is release and status is milestone" | "1.1"
        "1.+"                | "always reject if ivy branch is release"                         | "1.0"
        "latest.milestone"   | "always reject if ivy branch is release"                         | "1.0"
        "latest.integration" | "always reject if status is integration"                         | "1.1"
        "latest.milestone"   | "always reject if ivy branch is release and status is milestone" | "1.0"
    }

    def "produces sensible error when bad parameters are supplied to version selection rule" () {
        ivyRepo.module("org.utils", "api", "1.3").publish()

        buildFile << """
            $baseBuildFile

            dependencies {
                conf "org.utils:api:1.3"
            }

            configurations.all {
                resolutionStrategy {
                    versionSelection {
                        all { ${parameters} }
                    }
                }
            }
        """

        expect:
        fails 'resolveConf'
        failure.assertThatDescription(Matchers.startsWith("A problem occurred evaluating root project"))
        failure.assertHasLineNumber(17)
        failure.assertHasCause(message)

        where:
        parameters                                                                        | message
        ""                                                                                | "First parameter of a version selection rule needs to be of type 'VersionSelection'."
        "vs ->"                                                                           | "First parameter of a version selection rule needs to be of type 'VersionSelection'."
        "String vs ->"                                                                    | "First parameter of a version selection rule needs to be of type 'VersionSelection'."
        "VersionSelection vs, String s ->"                                                | "Unsupported parameter type for version selection rule: java.lang.String"
        "VersionSelection vs, o ->"                                                       | "Unsupported parameter type for version selection rule: java.lang.Object"
        "VersionSelection vs, ComponentMetadata cm, String s ->"                          | "Unsupported parameter type for version selection rule: java.lang.String"
        "VersionSelection vs, IvyModuleDescriptor imd, ComponentMetadata cm, String s ->" | "Unsupported parameter type for version selection rule: java.lang.String"
    }

    def "produces sensible error when rule throws an exception" () {
        ivyRepo.module("org.utils", "api", "1.3").publish()

        buildFile << """
            $baseBuildFile

            dependencies {
                conf "org.utils:api:1.3"
            }

            configurations.all {
                resolutionStrategy {
                    versionSelection {
                        all ${rule}
                    }
                }
            }
        """

        expect:
        fails 'resolveConf'
        failure.assertHasDescription("Execution failed for task ':resolveConf'.")
        failure.assertHasLineNumber(17)
        failure.assertHasCause("Could not apply version selection rule with all().")
        failure.assertHasCause("From test")

        where:
        rule                                                                                                         | _
        '{ VersionSelection vs -> throw new Exception("From test") }'                                                | _
        '{ VersionSelection vs, ComponentMetadata cm -> throw new Exception("From test") }'                          | _
        '{ VersionSelection vs, ComponentMetadata cm, IvyModuleDescriptor imd -> throw new Exception("From test") }' | _
    }
}
