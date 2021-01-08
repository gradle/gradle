/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.composite

class CompositeBuildLookupIntegrationTest extends AbstractCompositeBuildIntegrationTest {

    def "can query the included builds defined by the root build"() {
        given:
        def buildB = singleProjectBuild("buildB") {
        }
        includeBuild(buildB)
        def buildC = singleProjectBuild("buildC") {
        }
        includeBuild(buildC)
        buildA.buildFile << """
            assert gradle.includedBuild("buildB").name == "buildB"
            assert gradle.includedBuild("buildB").projectDir == file('${buildB.toURI()}')
            assert gradle.includedBuild("buildC").name == "buildC"
            assert gradle.includedBuild("buildC").projectDir == file('${buildC.toURI()}')
            assert gradle.includedBuilds.name == ["buildB", "buildC"]

            task broken {
                doLast {
                    assert gradle.includedBuilds.name == ["buildB", "buildC"]
                    gradle.includedBuild("unknown")
                }
            }
        """

        when:
        fails(buildA, "broken")

        then:
        failure.assertHasCause("Included build 'unknown' not found in build 'buildA'.")
    }

    def "dir name is used as build name"() {
        given:
        def buildB = singleProjectBuild("buildB") {
            settingsFile << """
                rootProject.name = 'b'
            """
        }
        includeBuild(buildB)
        def buildC = singleProjectBuild("buildC") {
            settingsFile << """
                rootProject.name = 'c'
            """
        }
        includeBuild(buildC)
        buildA.buildFile << """
            assert gradle.includedBuild("buildB").name == "buildB"
            assert gradle.includedBuild("buildB").projectDir == file('${buildB.toURI()}')
            assert gradle.includedBuild("buildC").name == "buildC"
            assert gradle.includedBuild("buildC").projectDir == file('${buildC.toURI()}')
            assert gradle.includedBuilds.name == ["buildB", "buildC"]

            task broken {
                doLast {
                    assert gradle.includedBuilds.name == ["buildB", "buildC"]
                    gradle.includedBuild("b")
                }
            }
        """

        when:
        fails(buildA, "broken")

        then:
        failure.assertHasCause("Included build 'b' not found in build 'buildA'.")
    }

    def "build name can be specified at include time"() {
        given:
        def buildB = singleProjectBuild("buildB") {
            settingsFile << """
                rootProject.name = 'b'
            """
        }
        includeBuildAs(buildB, "b1")
        def buildC = singleProjectBuild("buildC") {
            settingsFile << """
                rootProject.name = 'c'
            """
        }
        includeBuildAs(buildC, 'c1')
        buildA.buildFile << """
            assert gradle.includedBuild("b1").name == "b1"
            assert gradle.includedBuild("b1").projectDir == file('${buildB.toURI()}')
            assert gradle.includedBuild("c1").name == "c1"
            assert gradle.includedBuild("c1").projectDir == file('${buildC.toURI()}')
            assert gradle.includedBuilds.name == ["b1", "c1"]

            task broken {
                doLast {
                    assert gradle.includedBuilds.name == ["b1", "c1"]
                    gradle.includedBuild("buildB")
                }
            }
        """

        when:
        fails(buildA, "broken")

        then:
        failure.assertHasCause("Included build 'buildB' not found in build 'buildA'.")
    }

    def "included builds added from command-line are visible to root build"() {
        given:
        def buildB = singleProjectBuild("buildB") {
            buildFile << """
                task validate {
                    doLast {
                        assert gradle.includedBuilds.empty
                    }
                }
            """
        }
        includeBuild(buildB)
        def buildC = singleProjectBuild("buildC") {
        }

        buildA.buildFile << """
            task withBuild {
                dependsOn gradle.includedBuild("buildB").task(":validate")
                doLast {
                    assert gradle.includedBuild("buildB").name == "buildB"
                    assert gradle.includedBuild("buildC").name == "buildC"
                    assert gradle.includedBuild("buildC").projectDir == file('${buildC.toURI()}')
                    assert gradle.includedBuilds.name == ["buildB", "buildC"]
                }
            }
            task withoutBuild {
                dependsOn gradle.includedBuild("buildB").task(":validate")
                doLast {
                    assert gradle.includedBuild("buildB").name == "buildB"
                    assert gradle.includedBuilds.name == ["buildB"]
                }
            }
        """

        expect:
        executer.withArguments("--include-build", "../buildC")
        execute(buildA, "withBuild")

        executer.withArguments("--include-build", "../buildB")
        execute(buildA, "withoutBuild")
    }

    def "parent and sibling builds are not visible from included build"() {
        given:
        def buildB = singleProjectBuild("buildB") {
            buildFile << """
                assert gradle.includedBuilds.empty

                task broken1 {
                    doLast {
                        assert gradle.includedBuilds.empty
                        gradle.includedBuild("buildA")
                    }
                }
                task broken2 {
                    doLast {
                        assert gradle.includedBuilds.empty
                        gradle.includedBuild("buildC")
                    }
                }
            """
        }
        includeBuild(buildB)

        def buildC = singleProjectBuild("buildC") {
        }
        includeBuild(buildC)

        buildA.buildFile << """
            task broken {
                dependsOn gradle.includedBuild("buildB").task(":broken1")
                dependsOn gradle.includedBuild("buildB").task(":broken2")
            }
        """

        when:
        executer.withArgument("--continue")
        fails(buildA, "broken")

        then:
        failure.assertHasCause("Included build 'buildA' not found in build 'buildB'.")
        failure.assertHasCause("Included build 'buildC' not found in build 'buildB'.")
    }
}
