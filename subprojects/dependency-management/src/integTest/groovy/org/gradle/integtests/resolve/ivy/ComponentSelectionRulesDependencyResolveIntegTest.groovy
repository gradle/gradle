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

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.junit.Assume
import spock.lang.Issue


class ComponentSelectionRulesDependencyResolveIntegTest extends AbstractComponentSelectionRulesIntegrationTest {
    boolean isWellBehaved(boolean mavenCompatible, boolean gradleCompatible = true) {
        (GradleMetadataResolveRunner.useIvy() || mavenCompatible) && (!GradleMetadataResolveRunner.gradleMetadataPublished || gradleCompatible)
    }

    def "uses '#rule' rule to choose component for #selector"() {
        given:
        Assume.assumeTrue isWellBehaved(mavenCompatible, gradleCompatible)

        buildFile << """
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

            checkDeps.doLast {
                assert candidates == ${candidates}
            }
"""

        when:
        def chosenModule = setupInteractions(selector, chosenVersion, downloadedMetadata)

        then:
        checkDependencies {
            resolve.expectGraph {
                root(":", ":test:") {
                    edge("org.utils:api:${selector}", "org.utils:${chosenModule}:${chosenVersion}") {
                        byReasons(reasons)
                        maybeRequested()
                    }
                }
            }
        }

        when:
        resetExpectations()

        then:
        checkDependencies()

        and:
        resetExpectations()

        where:
        selector             | rule            | chosenVersion | candidates       | downloadedMetadata | mavenCompatible | gradleCompatible | reasons
        "1.+"                | "select 1.1"    | "1.1"         | '["1.2", "1.1"]' | ['1.1']            | true            | true             | ["didn't match versions 2.1, 2.0", "rejection: 1.2 by rule because not 1.1"]
        "1.+"                | "select status" | "1.1"         | '["1.2", "1.1"]' | ['1.2', '1.1']     | false           | true             | ["didn't match versions 2.1, 2.0", "rejection: 1.2 by rule because not milestone"]
        "1.+"                | "select branch" | "1.1"         | '["1.2", "1.1"]' | ['1.2', '1.1']     | false           | false            | ["didn't match versions 2.1, 2.0", "rejection: 1.2 by rule because not branch"]
        "latest.integration" | "select 2.1"    | "2.1"         | '["2.1"]'        | ['2.1']            | true            | true             | []
        "latest.milestone"   | "select 2.0"    | "2.0"         | '["2.0"]'        | ['2.1', '2.0']     | false           | true             | ["didn't match version 2.1"]
        "latest.milestone"   | "select status" | "2.0"         | '["2.0"]'        | ['2.1', '2.0']     | false           | true             | ["didn't match version 2.1"]
        "latest.milestone"   | "select branch" | "2.0"         | '["2.0"]'        | ['2.1', '2.0']     | false           | false            | ["didn't match version 2.1"]
        "1.1"                | "select 1.1"    | "1.1"         | '["1.1"]'        | ['1.1']            | true            | true             | []
        "1.1"                | "select status" | "1.1"         | '["1.1"]'        | ['1.1']            | false           | true             | []
        "1.1"                | "select branch" | "1.1"         | '["1.1"]'        | ['1.1']            | false           | false            | []
    }

    private String setupInteractions(String selector, String chosenVersion, List<String> downloadedMetadata, Closure<Void> more = {}) {
        def chosenModule = chosenVersion ? (chosenVersion.contains('-lib') ? 'lib' : 'api') : null
        repositoryInteractions {
            'org.utils:api' {
                if (!(selector in ["1.0", "1.1"])) {
                    expectVersionListing()
                }
            }
            downloadedMetadata.each { v ->
                group('org.utils') {
                    def mod = v.contains('-lib') ? 'lib' : 'api'
                    module(mod) {
                        version(v - '-lib') {
                            expectGetMetadata()
                        }
                    }
                }
            }
            if (chosenModule) {
                "org.utils:${chosenModule}:${chosenVersion - '-lib'}" {
                    expectGetArtifact()
                }
            }
            more.delegate = delegate
            more()
        }
        chosenModule
    }

    @ToBeFixedForConfigurationCache(because = "task uses Configuration API")
    def "uses '#rule' rule to reject all candidates for dynamic version #selector"() {
        given:
        Assume.assumeTrue isWellBehaved(mavenCompatible)

        buildFile << """

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

            task checkLenient {
                doLast {
                    def artifacts = configurations.conf.resolvedConfiguration.lenientConfiguration.getArtifacts(Specs.SATISFIES_ALL)
                    assert artifacts.size() == 0
                    assert candidates == ${candidates}
                }
            }
"""

        when:
        setupInteractions(selector, null, downloadedMetadata)

        then:
        checkDependencies(':checkLenient')

        when:
        resetExpectations()
        repositoryInteractions {
            'org.utils:api' {
                expectHeadVersionListing()
            }
        }

        then:
        fails ':checkDeps'
        failureHasCause("Could not find any version that matches org.utils:api:${selector}.")

        when:
        resetExpectations()
        repositoryInteractions {
            'org.utils:api' {
                expectHeadVersionListing()
            }
        }

        then:
        fails ':checkDeps'
        failureHasCause("Could not find any version that matches org.utils:api:${selector}.")

        and:
        resetExpectations()

        where:
        selector             | rule                       | candidates              | downloadedMetadata    | mavenCompatible
        "1.+"                | "reject all"               | '["1.2", "1.1", "1.0"]' | []                    | true
        "latest.integration" | "reject all"               | '["2.1"]'               | ['2.1']               | false
        "latest.milestone"   | "reject all"               | '["2.0"]'               | ['2.1', '2.0']        | false
        "1.+"                | "reject all with metadata" | '["1.2", "1.1", "1.0"]' | ['1.2', '1.1', '1.0'] | true
        "latest.integration" | "reject all with metadata" | '["2.1"]'               | ['2.1']               | false
        "latest.milestone"   | "reject all with metadata" | '["2.0"]'               | ['2.1', '2.0']        | false
        // latest.milestone is 2.0, but since the rule rejects it, we should never reach version 1.1
        "latest.milestone"   | "select 1.1"               | '["2.0"]'               | ['2.1', '2.0']        | false
    }

    def "reports all candidates rejected by rule"() {
        buildFile << """

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
        setupInteractions('1.+', null, ['1.2', '1.1', '1.0'])

        then:
        fails ':checkDeps'

        and:
        failureHasCause("""Could not find any version that matches org.utils:api:1.+.
Versions that do not match:
  - 2.1
  - 2.0
Versions rejected by component selection rules:
  - 1.2
  - 1.1
  - 1.0
Searched in the following locations:
  - ${versionListingURI('org.utils', 'api')}
${triedMetadata('org.utils', 'api', "1.2")}
${triedMetadata('org.utils', 'api', "1.1")}
${triedMetadata('org.utils', 'api', "1.0")}
Required by:
""")

        when:
        resetExpectations()
        repositoryInteractions {
            'org.utils:api' {
                expectHeadVersionListing()
            }
        }

        then:
        fails ':checkDeps'

        and:
        // TODO - this failure and the previous failure should report the same urls (whatever that happens to be)
        failureHasCause("""Could not find any version that matches org.utils:api:1.+.
Versions that do not match:
  - 2.1
  - 2.0
Versions rejected by component selection rules:
  - 1.2
  - 1.1
  - 1.0
Searched in the following locations:
  - ${versionListingURI('org.utils', 'api')}
Required by:
""")
    }

    @ToBeFixedForConfigurationCache(because = "task uses Configuration API")
    def "uses '#rule' rule to reject candidate for static version #selector"() {
        given:
        Assume.assumeTrue isWellBehaved(mavenCompatible, gradleCompatible)

        buildFile << """
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

            task checkLenient {
                doLast {
                    def artifacts = configurations.conf.resolvedConfiguration.lenientConfiguration.getArtifacts(Specs.SATISFIES_ALL)
                    assert artifacts.size() == 0
                    assert candidates == ${candidates}
                }
            }
"""

        when:
        setupInteractions(selector, null, downloadedMetadata)

        then:
        checkDependencies(':checkLenient')

        when:
        resetExpectations()

        then:
        fails ':checkDeps'

        when:
        resetExpectations()

        then:
        fails ':checkDeps'

        where:
        selector | rule            | candidates | downloadedMetadata | mavenCompatible | gradleCompatible
        "1.0"    | "reject all"    | '["1.0"]'  | ['1.0']            | true            | true
        "1.0"    | "select 1.1"    | '["1.0"]'  | ['1.0']            | true            | true
        "1.0"    | "select status" | '["1.0"]'  | ['1.0']            | true            | true
        "1.0"    | "select branch" | '["1.0"]'  | ['1.0']            | false           | false
        "1.1"    | "reject all"    | '["1.1"]'  | ['1.1']            | true            | true
    }

    def "can use component selection rule to choose component from different repository for #selector"() {
        def ivyRepo2 = ivyRepo("repo2")
        def module2 = ivyRepo2.module("org.utils", "api", "1.1").withBranch("other").publishWithChangedContent()

        buildFile << """
            repositories {
                ivy { url "${ivyRepo2.uri}" }
            }

            dependencies {
                conf "org.utils:api:${selector}"
            }

            configurations.all {
                resolutionStrategy {
                    componentSelection {
                        all { ComponentSelection selection ->
                            def ivy = selection.getDescriptor(IvyModuleDescriptor)
                            if (ivy != null && ivy.branch != "other") {
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
        if (GradleMetadataResolveRunner.useIvy()) {
            repositoryInteractions {
                'org.utils:api' {
                    if (selector != '1.1') {
                        expectVersionListing()
                    }
                    '1.2' {
                        allowAll()
                    }
                    '1.1' {
                        allowAll()
                    }
                    '1.0' {
                        allowAll()
                    }
                }
            }
            succeeds "retrieve"
        }

        then:
        if (GradleMetadataResolveRunner.useIvy()) {
            file("libs").assertHasDescendants("api-1.1.jar")
            file("libs/api-1.1.jar").assertIsDifferentFrom(ivyHttpRepo.module('org.utils', 'api', '1.1').jarFile)
            file("libs/api-1.1.jar").assertIsCopyOf(module2.jarFile)
        }

        where:
        selector << ["1.1", "1.+"]
    }

    @ToBeFixedForConfigurationCache(because = "task uses Configuration API")
    def "can control selection of components by module rule #rule for #selector"() {
        given:
        Assume.assumeTrue isWellBehaved(mavenCompatible, gradleCompatible)

        buildFile << """
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
                    }
                }
            }

            checkDeps.doLast {
                def artifacts = configurations.conf.resolvedConfiguration.resolvedArtifacts
                assert artifacts.size() == 2
                assert artifacts[0].moduleVersion.id.version == '${chosen}'
                assert candidates == ${candidates}
            }
        """

        when:
        setupInteractions(selector, chosen, downloadedMetadata) {
            'org.utils:lib' {
                expectVersionListing()
            }
            'org.utils:lib:1.1' {
                expectResolve()
            }
        }

        then:
        checkDependencies()

        and:
        resetExpectations()

        where:
        selector           | rule            | chosen | candidates       | downloadedMetadata | mavenCompatible | gradleCompatible
        "1.+"              | "select 1.1"    | "1.1"  | '["1.2", "1.1"]' | ['1.1']            | true            | true
        "latest.milestone" | "select status" | "2.0"  | '["2.0"]'        | ['2.1', '2.0']     | false           | true
        "1.1"              | "select branch" | "1.1"  | '["1.1"]'        | ['1.1']            | false           | false
    }

    @Issue("GRADLE-3236")
    def "can select a different component for the same selector in different configurations"() {
        def descriptorArg = GradleMetadataResolveRunner.useIvy() ? 'selection.getDescriptor(IvyModuleDescriptor)' : 'selection.metadata'
        buildFile << """
            configurations {
                modules
                modulesA {
                    extendsFrom modules
                    resolutionStrategy {
                        componentSelection {
                            all { ComponentSelection selection ->
                                if ($descriptorArg != null) {
                                    println "A is evaluating \$selection.candidate"
                                    if (selection.candidate.version != "1.1") { selection.reject("Rejected by A") }
                                }
                            }
                        }
                    }
                }
                modulesB {
                    extendsFrom modules
                    resolutionStrategy {
                        componentSelection {
                            all { ComponentSelection selection ->
                                if ($descriptorArg != null) {
                                    println "B is evaluating \$selection.candidate"
                                    if (selection.candidate.version != "1.0") { selection.reject("Rejected by B") }
                                }
                            }
                        }
                    }
                }
            }

            dependencies {
                modules "org.utils:api:1.+"
            }

            task verify {
                def filesA = configurations.modulesA
                def filesB = configurations.modulesB
                doLast {
                    assert filesA.collect { it.name } == [ 'api-1.1.jar']
                    assert filesB.collect { it.name } == [ 'api-1.0.jar']
                }
            }
        """

        when:
        repositoryInteractions {
            'org.utils:api' {
                expectVersionListing()
                '1.2' {
                    expectGetMetadata()
                }
                '1.1' {
                    expectResolve()
                }
                '1.0' {
                    expectResolve()
                }
            }
        }

        then:
        succeeds "verify"

        when:
        resetExpectations()

        then:
        succeeds "verify"
    }

}
