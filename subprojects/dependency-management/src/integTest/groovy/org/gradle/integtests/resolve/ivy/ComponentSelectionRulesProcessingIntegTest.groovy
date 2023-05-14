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
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.test.fixtures.ivy.IvyModule
import org.gradle.test.fixtures.maven.MavenModule

class ComponentSelectionRulesProcessingIntegTest extends AbstractComponentSelectionRulesIntegrationTest {

    @ToBeFixedForConfigurationCache(because = "task uses Configuration API")
    def "rules are not fired when no candidate matches selector"() {
        buildFile << """

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

            task lenientCheck {
                doLast {
                    def artifacts = configurations.conf.resolvedConfiguration.lenientConfiguration.getArtifacts(Specs.SATISFIES_ALL)
                    assert artifacts.size() == 0
                    assert candidates.empty
                }
            }
"""
        when:
        repositoryInteractions {
            'org.utils:api' {
                expectVersionListing()
            }
        }

        then:
        succeeds 'lenientCheck'
    }

    def "further rules are not fired when any rule rejects candidate"() {
        buildFile << """
            dependencies {
                conf "org.utils:api:1.+"
            }

            def extraRuleCandidates = []
            configurations.all {
                resolutionStrategy {
                    componentSelection {
                        all ${rules['select 1.1']}
                        all { ComponentSelection selection ->
                            if (selection.metadata != null) {
                                extraRuleCandidates << selection.candidate.version
                            }
                        }
                    }
                }
            }

            checkDeps.doLast {
                assert extraRuleCandidates == ['1.1']
            }
"""
        when:
        repositoryInteractions {
            'org.utils:api' {
                expectVersionListing()
                '1.1' {
                    expectResolve()
                }
            }
        }

        then:
        checkDependencies()
    }

    // this test doesn't make sense with Gradle metadata
    @RequiredFeature(feature=GradleMetadataResolveRunner.GRADLE_METADATA, value="false")
    // only test one combination
    @RequiredFeature(feature=GradleMetadataResolveRunner.REPOSITORY_TYPE, value="ivy")
    def "maven module is not affected by rule requiring ivy module descriptor input"() {
        def mavenModule = mavenRepo.module("org.utils", "api", "1.1").publishWithChangedContent()

        buildFile << """
            configurations { conf }
            repositories {
                maven { url "${mavenRepo.uri}" }
            }

            dependencies {
                conf "org.utils:api:1.1"
            }

            configurations.all {
                resolutionStrategy {
                    componentSelection {
                        all { ComponentSelection selection ->
                            if (selection.getDescriptor(IvyModuleDescriptor) != null) {
                                selection.reject("rejecting all ivy modules")
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
        repositoryInteractions {
            'org.utils:api:1.1' {
                expectGetMetadata()
            }
        }
        succeeds "retrieve"

        then:
        file("libs").assertHasDescendants("api-1.1.jar")
        file("libs/api-1.1.jar").assertIsDifferentFrom(ivyHttpRepo.module('org.utils', 'api', '1.1').jarFile)
        file("libs/api-1.1.jar").assertIsCopyOf(mavenModule.artifactFile)
    }

    // Gradle metadata doesn't support parents
    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value="false")
    def "parent is not affected by selection rules" () {
        given:
        repository {
            'org:parent_dep:1.2'()
            'org:child_dep:1.7'()
            'org:parent:1.0' {
                dependsOn('org:parent_dep:1.2')
                withModule(MavenModule) {
                    hasPackaging('pom')
                }
            }
            'org:child:1.0' {
                dependsOn('org:child_dep:1.7')
                withModule(MavenModule) {
                    parent('org', 'parent', '1.0')
                }
                withModule(IvyModule) {
                    extendsFrom(organisation: "org", module: "parent", revision: "1.0")
                }
            }
        }

        buildFile << """
            configurations { conf }

            def fired = []
            configurations.all {
                resolutionStrategy {
                    componentSelection {
                        all { ComponentSelection selection ->
                            logger.warn("fired for \${selection.candidate.module}")
                            fired << "\${selection.candidate.module}"
                        }

                        withModule('org:parent') { ComponentSelection selection ->
                            logger.warn("rejecting parent")
                            selection.reject("Rejecting parent")
                        }
                    }
                }
            }

            dependencies {
                conf "org:child:1.0"
            }

            task resolveConf {
                def files = configurations.conf
                def modules = provider { fired }
                doLast {
                    files.files
                    assert modules.get().sort() == [ 'child', 'child_dep', 'parent_dep' ]
                }
            }
        """

        when:
        repositoryInteractions {
            'org:child:1.0' {
                expectResolve()
            }
            'org:parent:1.0' {
                expectGetMetadata()
            }
            'org:child_dep:1.7' {
                expectResolve()
            }
            'org:parent_dep:1.2' {
                expectResolve()
            }
        }

        then:
        succeeds "resolveConf"
    }

    // because of the IvyModuleDescriptor rule
    @RequiredFeature(feature=GradleMetadataResolveRunner.REPOSITORY_TYPE, value="ivy")
    def "component metadata is requested only once for rules that do require it" () {
        buildFile << """
            dependencies {
                conf "org.utils:api:2.0"
            }

            def rule1candidates = []
            def rule2candidates = []
            configurations.all {
                resolutionStrategy {
                    componentSelection {
                        all { ComponentSelection vs ->
                            if (vs.getDescriptor(IvyModuleDescriptor) != null && vs.metadata != null) {
                                rule1candidates << vs.candidate.version
                            }
                        }
                        all { ComponentSelection vs ->
                            if (vs.metadata != null) {
                                rule2candidates << vs.candidate.version
                            }
                        }
                    }
                }
            }

            checkDeps.doLast {
                assert rule1candidates == ['2.0']
                assert rule2candidates == ['2.0']
            }
        """

        when:
        repositoryInteractions {
            'org.utils:api:2.0' {
                expectResolve()
            }
        }

        then:
        checkDependencies()

        when:
        // Should use cache second time
        resetExpectations()

        then:
        checkDependencies()
    }

    // because of the IvyModuleDescriptor rule
    @RequiredFeature(feature=GradleMetadataResolveRunner.REPOSITORY_TYPE, value="ivy")
    // because of branch
    @RequiredFeature(feature=GradleMetadataResolveRunner.GRADLE_METADATA, value="false")
    def "changed component metadata becomes visible when module is refreshed" () {

        def commonBuildFile = buildFile.text + """
            dependencies {
                conf "org.utils:api:1.+"
            }

            def status11 = null
            def branch11 = null
            configurations.all {
                resolutionStrategy {
                    componentSelection {
                        all { ComponentSelection selection ->
                            if (selection.candidate.version == '1.1') {
                                status11 = selection.metadata.status
                                branch11 = selection.getDescriptor(IvyModuleDescriptor).branch
                            } else {
                                selection.reject('not 1.1')
                            }
                        }
                    }
                }
            }
        """

        when:
        buildFile.text = """
            $commonBuildFile

            checkDeps.doLast {
                assert status11 == 'milestone'
                assert branch11 == 'test'
            }
        """

        and:
        repositoryInteractions {
            'org.utils:api' {
                expectVersionListing()
                '1.1' {
                    expectResolve()
                }
            }
        }

        then:
        succeeds 'checkDeps'

        when:
        resetExpectations()
        repository {
            'org.utils:api:1.1' {
                withModule {
                    withBranch('master')
                    withStatus('release')
                    publishWithChangedContent()
                }
            }
        }

        then:
        repositoryInteractions {}
        // Everything should come from cache
        succeeds 'checkDeps'

        when:
        buildFile.text = """
            $commonBuildFile

            checkDeps.doLast {
                assert status11 == 'release'
                assert branch11 == 'master'
            }

            def var = "here to change length of the bytecode"
        """

        and:
        resetExpectations()
        repositoryInteractions {
            'org.utils:api' {
                expectVersionListing()
                '1.1' {
                    expectHeadMetadata()
                    withModule {
                        // todo: handle this properly in ModuleVersionSpec test fixture
                        getArtifact(name: 'ivy', ext: 'xml.sha1').allowGetOrHead()
                        getArtifact(ext: 'jar.sha1').allowGetOrHead()
                    }
                    expectGetMetadata()
                    expectHeadArtifact()
                    expectGetArtifact()
                }
            }
        }

        then:
        args("--refresh-dependencies")
        succeeds 'checkDeps'
    }

    def "copies selection rules when configuration is copied" () {
        buildFile << """
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

            task('assertDeps') {
                def conf = configurations.conf
                def confCopy = configurations.confCopy
                def notCopy = configurations.notCopy
                doLast {
                    assert conf*.name == ['api-1.1.jar']
                    assert confCopy*.name == ['api-1.1.jar']
                    assert notCopy*.name == ['api-1.2.jar']
                }
            }
        """

        when:
        repositoryInteractions {
            'org.utils:api' {
                expectVersionListing()
                '1.1' {
                    expectResolve()
                }
                '1.2' {
                    expectResolve()
                }
            }
        }

        then:
        checkDependencies("assertDeps")
    }

    def "can provide component selection rule as closure" () {
        buildFile << """
            dependencies {
                conf "org.utils:api:1.+"
            }

            configurations.conf {
                resolutionStrategy {
                    componentSelection {
                        all {
                            candidates << candidate.version
                        }
                        all { details ->
                            candidates << details.candidate.version
                        }
                        all { def details ->
                            candidates << details.candidate.version
                        }
                        all { ComponentSelection details ->
                            candidates << details.candidate.version
                        }
                    }
                }
            }

            checkDeps.doLast {
                assert candidates == ['1.2', '1.2', '1.2', '1.2']
            }
        """
        when:
        repositoryInteractions {
            'org.utils:api' {
                expectVersionListing()
                '1.2' {
                    expectResolve()
                }
            }
        }

        then:
        checkDependencies()
    }

    @ToBeFixedForConfigurationCache(because = "task uses Configuration API")
    def "can provide component selection rule as rule source"() {
        buildFile << """

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

            checkDeps.doLast {
                def artifacts = configurations.conf.resolvedConfiguration.resolvedArtifacts
                assert artifacts.size() == 1
                assert artifacts[0].moduleVersion.id.version == '1.1'
                assert ruleSource.candidates == ['1.2', '1.1']
            }

            class Select11 {
                def candidates = []

                @Mutate
                void select(ComponentSelection selection) {
                    if (selection.candidate.version != '1.1') {
                        selection.reject("not 1.1")
                    }
                    candidates << selection.candidate.version
                }
            }
        """

        when:
        repositoryInteractions {
            'org.utils:api' {
                expectVersionListing()
                '1.1' {
                    expectResolve()
                }
            }
        }

        then:
        checkDependencies()

    }
}
