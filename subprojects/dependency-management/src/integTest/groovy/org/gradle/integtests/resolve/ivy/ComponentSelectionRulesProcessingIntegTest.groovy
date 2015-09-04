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

class ComponentSelectionRulesProcessingIntegTest extends AbstractComponentSelectionRulesIntegrationTest {

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

    def "maven parent pom is not affected by selection rules" () {
        mavenRepo.module("org", "parent_dep", "1.2").publish()
        mavenRepo.module("org", "child_dep", "1.7").publish()

        def parent = mavenRepo.module("org", "parent", "1.0")
        parent.hasPackaging('pom')
        parent.dependsOn("org", "parent_dep", "1.2")
        parent.publish()

        def child = mavenRepo.module("org", "child", "1.0")
        child.dependsOn("org", "child_dep", "1.7")
        child.parent("org", "parent", "1.0")
        child.publish()

        buildFile << """
            configurations { conf }
            repositories {
                maven { url "${mavenRepo.uri}" }
            }

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

            task resolveConf << {
                configurations.conf.files
                assert fired.sort() == [ 'child', 'child_dep', 'parent_dep' ]
            }
        """

        expect:
        succeeds "resolveConf"
    }

    def "ivy extension module is not affected by selection rules" () {
        ivyRepo.module("org", "parent_dep", "1.2").publish()
        ivyRepo.module("org", "child_dep", "1.7").publish()

        def parent = ivyRepo.module("org", "parent", "1.0").dependsOn("org", "parent_dep", "1.2").publish()
        def child = ivyRepo.module("org", "child", "1.0")
        child.dependsOn("org", "child_dep", "1.7")
        child.extendsFrom(organisation: "org", module: "parent", revision: "1.0")
        child.publish()

        buildFile << """
            configurations { conf }
            repositories {
                ivy { url "${ivyRepo.uri}" }
            }

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

            task resolveConf << {
                configurations.conf.files
                assert fired.sort() == [ 'child', 'child_dep', 'parent_dep' ]
            }
        """

        expect:
        succeeds "resolveConf"
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

            def var = "here to change length of the bytecode"
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

    def "can provide component selection rule as closure" () {
        buildFile << """
            $baseBuildFile

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

            resolveConf.doLast {
                assert candidates == ['1.2', '1.2', '1.2', '1.2']
            }
        """

        expect:
        succeeds 'resolveConf'
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

                @Mutate
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
}
