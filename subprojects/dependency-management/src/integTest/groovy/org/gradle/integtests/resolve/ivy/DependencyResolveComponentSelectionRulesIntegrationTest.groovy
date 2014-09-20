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
import org.gradle.test.fixtures.server.http.IvyHttpModule
import spock.lang.Unroll

class DependencyResolveComponentSelectionRulesIntegrationTest extends AbstractHttpDependencyResolutionTest {
    Map<String, IvyHttpModule> modules = [:]

    def setup() {
        modules['1.0'] = ivyHttpRepo.module("org.utils", "api", "1.0").publish()
        modules['1.1'] = ivyHttpRepo.module("org.utils", "api", "1.1").withBranch("test").withStatus("milestone").publish()
        modules['1.2'] = ivyHttpRepo.module("org.utils", "api", "1.2").publish()
        modules['2.0'] = ivyHttpRepo.module("org.utils", "api", "2.0").withBranch("test").withStatus("milestone").publish()
        modules['2.1'] = ivyHttpRepo.module("org.utils", "api", "2.1").publish()
        modules['1.0-lib'] = ivyHttpRepo.module("org.utils", "lib", "1.0").publish()
        modules['1.1-lib'] = ivyHttpRepo.module("org.utils", "lib", "1.1").withBranch("test").withStatus("milestone").publish()
    }

    String getBaseBuildFile() {
        """
        def candidates = []
        configurations { conf }
        repositories {
            ivy { url "${ivyRepo.uri}" }
        }
        task resolveConf << { configurations.conf.files }
        """
    }

    String getHttpBaseBuildFile() {
        """
        def candidates = []
        configurations { conf }
        repositories {
            ivy { url "${ivyHttpRepo.uri}" }
        }
        task resolveConf << { configurations.conf.files }
        """
    }

    private static def rules = [
            "reject all": """{ ComponentSelection selection ->
                selection.reject("rejecting everything")
                candidates << selection.candidate.version
            }
            """,
            "reject all with metadata": """{ ComponentSelection selection, ComponentMetadata metadata ->
                selection.reject("rejecting everything")
                candidates << selection.candidate.version
            }
            """,
            "select 1.1": """{ ComponentSelection selection ->
                if (selection.candidate.version != '1.1') {
                    selection.reject("not 1.1")
                }
                candidates << selection.candidate.version
            }
            """,
            "select branch": """{ ComponentSelection selection, IvyModuleDescriptor ivy ->
                if (ivy.branch != 'test') {
                    selection.reject("not branch")
                }
                candidates << selection.candidate.version
            }
            """,
            "select status": """{ ComponentSelection selection, ComponentMetadata metadata ->
                if (metadata.status != 'milestone') {
                    selection.reject("not milestone")
                }
                candidates << selection.candidate.version
            }
            """
    ]

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
        "latest.integration" | "select 1.1"    | "1.1"  | '["2.1", "2.0", "1.2", "1.1"]'        | ['1.1'] // Custom rule fires before version matching
        "latest.integration" | "select status" | "2.0"  | '["2.1", "2.0"]'                      | ['2.1', '2.0']
        "latest.integration" | "select branch" | "2.0"  | '["2.1", "2.0"]'                      | ['2.1', '2.0']
        "latest.milestone"   | "select 1.1"    | "1.1"  | '["2.1", "2.0", "1.2", "1.1"]'        | ['1.1'] // Custom rule fires before version matching
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
        "latest.integration" | "reject all"               | '["2.1", "2.0", "1.2", "1.1", "1.0"]' | []
        "latest.milestone"   | "reject all"               | '["2.1", "2.0", "1.2", "1.1", "1.0"]' | []
        "1.+"                | "reject all with metadata" | '["1.2", "1.1", "1.0"]'               | ['1.2', '1.1', '1.0']
        "latest.integration" | "reject all with metadata" | '["2.1", "2.0", "1.2", "1.1", "1.0"]' | ['2.1', '2.0', '1.2', '1.1', '1.0']
        "latest.milestone"   | "reject all with metadata" | '["2.0", "1.1"]'                      | ['2.1', '2.0', '1.2', '1.1', '1.0']
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

    def "rules are not fired when no candidate matches selector"() {
        buildFile << """
            $baseBuildFile

            dependencies {
                conf "org.utils:api:3.+"
            }

            configurations.all {
                resolutionStrategy {
                    componentSelection {
                        all { ComponentSelection selection ->
                            candidates << selection.candidate.version
                        }
                    }
                }
            }

            task checkConf << {
                def artifacts = configurations.conf.resolvedConfiguration.lenientConfiguration.getArtifacts(Specs.SATISFIES_ALL)
                assert artifacts.size() == 0
                assert candidates.empty
            }
"""
        expect:
        succeeds 'checkConf'
    }

    def "further rules are not fired when any rule rejects candidate"() {
        buildFile << """
            $baseBuildFile

            dependencies {
                conf "org.utils:api:1.+"
            }

            def extraRuleCandidates = []
            configurations.all {
                resolutionStrategy {
                    componentSelection {
                        all { ComponentSelection selection, ComponentMetadata metadata ->
                            extraRuleCandidates << selection.candidate.version
                        }
                        all ${rules['select 1.1']}
                    }
                }
            }

            resolveConf.doLast {
                assert extraRuleCandidates == ['1.1']
            }
"""
        expect:
        succeeds 'resolveConf'
    }

    def "maven module is not affected by rule requiring ivy module descriptor input"() {
        def mavenModule = mavenRepo.module("org.utils", "api", "1.1").publishWithChangedContent()

        buildFile << """
            configurations { conf }
            repositories {
                ivy { url "${ivyRepo.uri}" }
                maven { url "${mavenRepo.uri}" }
            }

            dependencies {
                conf "org.utils:api:1.1"
            }

            configurations.all {
                resolutionStrategy {
                    componentSelection {
                        all { ComponentSelection selection, IvyModuleDescriptor ivy ->
                            selection.reject("rejecting all ivy modules")
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
        file("libs/api-1.1.jar").assertIsCopyOf(mavenModule.artifactFile)
    }

    def "produces sensible error when bad code is supplied in version selection rule" () {
        buildFile << """
            $baseBuildFile

            dependencies {
                conf "org.utils:api:1.2"
            }

            configurations.all {
                resolutionStrategy {
                    componentSelection {
                        all { ComponentSelection selection ->
                            foo()
                        }
                    }
                }
            }
        """

        expect:
        fails 'resolveConf'
        failure.assertHasDescription("Execution failed for task ':resolveConf'.")
        failure.assertHasCause("Could not apply component selection rule with all().")
        failure.assertHasCause("Could not find method foo()")
    }

    def "produces sensible error for invalid component selection rule" () {
        buildFile << """
            $baseBuildFile

            dependencies {
                conf "org.utils:api:1.2"
            }

            configurations.all {
                resolutionStrategy {
                    componentSelection {
                        all { ${parameters} }
                    }
                }
            }
        """

        expect:
        fails 'resolveConf'
        failureDescriptionStartsWith("A problem occurred evaluating root project")
        failureHasCause("The closure provided is not valid as a rule for 'ComponentSelectionRules'.")
        failureHasCause(message)

        where:
        parameters                           | message
        "String vs ->"                       | "First parameter of rule action closure must be of type 'ComponentSelection'."
        "ComponentSelection vs, String s ->" | "Unsupported parameter type: java.lang.String"
    }

    def "produces sensible error when rule throws an exception" () {
        buildFile << """
            $baseBuildFile

            dependencies {
                conf "org.utils:api:1.2"
            }

            configurations.all {
                resolutionStrategy {
                    componentSelection {
                        all { ComponentSelection cs -> throw new Exception("From test") }
                    }
                }
            }
        """

        expect:
        fails 'resolveConf'
        failure.assertHasDescription("Execution failed for task ':resolveConf'.")
        failure.assertHasCause("Could not apply component selection rule with all().")
        failure.assertHasCause("From test")

        where:
        rule                                                                                                           | _
        '{ ComponentSelection cs -> throw new RuntimeException("From test") }'                                         | _
        '{ ComponentSelection cs -> throw new Exception("From test") }'                                                | _
        '{ ComponentSelection cs, ComponentMetadata cm -> throw new Exception("From test") }'                          | _
        '{ ComponentSelection cs, ComponentMetadata cm, IvyModuleDescriptor imd -> throw new Exception("From test") }' | _
    }

    def "component metadata is requested only once for rules that do require it" () {
        buildFile << """
            $httpBaseBuildFile

            dependencies {
                conf "org.utils:api:2.0"
            }

            def rule1candidates = []
            def rule2candidates = []
            configurations.all {
                resolutionStrategy {
                    componentSelection {
                        all { ComponentSelection vs, IvyModuleDescriptor imd, ComponentMetadata cm ->
                            rule1candidates << vs.candidate.version
                        }
                        all { ComponentSelection vs, ComponentMetadata cm ->
                            rule2candidates << vs.candidate.version
                        }
                    }
                }
            }

            resolveConf.doLast {
                assert rule1candidates == ['2.0']
                assert rule2candidates == ['2.0']
            }
        """

        when:
        modules['2.0'].ivy.expectDownload()
        modules['2.0'].artifact.expectDownload()

        then:
        succeeds 'resolveConf'

        when:
        // Should use cache second time
        server.resetExpectations()

        then:
        succeeds 'resolveConf'
    }

    def "changed component metadata becomes visible when module is refreshed" () {

        def commonBuildFile = """
            $httpBaseBuildFile

            dependencies {
                conf "org.utils:api:1.+"
            }

            def status11 = null
            def branch11 = null
            configurations.all {
                resolutionStrategy {
                    componentSelection {
                        all { ComponentSelection selection, IvyModuleDescriptor descriptor, ComponentMetadata metadata ->
                            if (selection.candidate.version == '1.1') {
                                status11 = metadata.status
                                branch11 = descriptor.branch
                            } else {
                                selection.reject('not 1.1')
                            }
                        }
                    }
                }
            }
        """

        when:
        buildFile << """
            $commonBuildFile

            resolveConf.doLast {
                assert status11 == 'milestone'
                assert branch11 == 'test'
            }
        """

        and:
        ivyHttpRepo.directoryList("org.utils", "api").expectGet()
        modules["1.2"].ivy.expectDownload()
        modules["1.1"].ivy.expectDownload()
        modules["1.1"].artifact.expectDownload()

        then:
        succeeds 'resolveConf'

        when:
        modules["1.1"].withBranch('master').withStatus('release').publishWithChangedContent()

        and:
        server.resetExpectations()

        then:
        // Everything should come from cache
        succeeds 'resolveConf'

        when:
        buildFile.text = """
            $commonBuildFile

            resolveConf.doLast {
                assert status11 == 'release'
                assert branch11 == 'master'
            }
        """

        and:
        server.resetExpectations()
        ivyHttpRepo.directoryList("org.utils", "api").expectGet()
        modules["1.2"].ivy.expectHead()
        modules["1.1"].ivy.expectHead()
        modules["1.1"].ivy.sha1.expectGet()
        modules["1.1"].ivy.expectDownload()
        modules["1.1"].artifact.expectMetadataRetrieve()
        modules["1.1"].artifact.sha1.expectGet()
        modules["1.1"].artifact.expectDownload()

        then:
        args("--refresh-dependencies")
        succeeds 'resolveConf'
    }

    def "can control selection of components within a module" () {
        buildFile << """
            $httpBaseBuildFile

            dependencies {
                conf "org.utils:api:${selector}"
                conf "org.utils:lib:1.+"
            }

            configurations.all {
                resolutionStrategy {
                    componentSelection {
                        module("org.utils:api") ${rules[rule]}
                        module("org.utils:api") { ComponentSelection cs ->
                            assert cs.candidate.group == "org.utils"
                            assert cs.candidate.module == "api"
                        }
                        module("some.other:module") { ComponentSelection cs -> }
                        module("some.other:module") { ComponentSelection cs, IvyModuleDescriptor descriptor, ComponentMetadata metadata -> }
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
        "latest.integration" | "select status" | "2.0"  | '["2.1", "2.0"]'                      | ['2.1', '2.0']
        "1.1"                | "select branch" | "1.1"  | '["1.1"]'                             | ['1.1']
    }

    def "produces sensible error for invalid module target id" () {
        buildFile << """
            $baseBuildFile

            dependencies {
                conf "org.utils:api:1.2"
            }

            configurations.all {
                resolutionStrategy {
                    componentSelection {
                        module("org.utils") { ComponentSelection cs -> }
                    }
                }
            }
        """

        expect:
        fails 'resolveConf'
        failureDescriptionStartsWith("A problem occurred evaluating root project")
        failure.assertHasLineNumber(18)
        failureHasCause("Could not add a component selection rule for module 'org.utils'.")
        failureHasCause("Cannot convert the provided notation to an object of type ModuleIdentifier: org.utils")
    }

    def "can provide component selection rule as rule source"() {
        buildFile << """
            $baseBuildFile

            dependencies {
                conf "org.utils:api:1.+"
            }

            def ruleSource = new Select11()

            configurations.all {
                resolutionStrategy {
                    componentSelection {
                        all ruleSource
                    }
                }
            }

            resolveConf.doLast {
                def artifacts = configurations.conf.resolvedConfiguration.resolvedArtifacts
                assert artifacts.size() == 1
                assert artifacts[0].moduleVersion.id.version == '1.1'
                assert ruleSource.candidates == ['1.2', '1.1']
            }

            class Select11 {
                def candidates = []

                @org.gradle.model.Mutate
                void select(ComponentSelection selection) {
                    if (selection.candidate.version != '1.1') {
                        selection.reject("not 1.1")
                    }
                    candidates << selection.candidate.version
                }
            }
"""
        expect:
        succeeds "resolveConf"
    }

    def "copies selection rules when configuration is copied" () {
        buildFile << """
            $baseBuildFile

            configurations {
                notCopy 
            }

            dependencies {
                conf "org.utils:api:1.+"
                notCopy "org.utils:api:1.+"
            }

            configurations.conf {
                resolutionStrategy {
                    componentSelection {
                        all ${rules['select 1.1']}
                    }
                }
            }
            configurations.add(configurations.conf.copy())

            task('checkConf') << {
                assert configurations.conf.files*.name == ['api-1.1.jar']
                assert configurations.confCopy.files*.name == ['api-1.1.jar']
                assert configurations.notCopy.files*.name == ['api-1.2.jar']
            }
        """

        expect:
        succeeds 'checkConf'
    }
}
