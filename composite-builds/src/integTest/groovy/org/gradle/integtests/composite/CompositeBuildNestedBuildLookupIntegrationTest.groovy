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

class CompositeBuildNestedBuildLookupIntegrationTest extends AbstractCompositeBuildIntegrationTest {
    def "can query the included builds defined by an included build"() {
        given:
        def buildC = singleProjectBuild("buildC") {
        }
        def buildB = singleProjectBuild("buildB") {
            settingsFile << """
                includeBuild('${buildC.toURI()}')
            """
            buildFile << """
                assert gradle.includedBuild("buildC").name == "buildC"
                assert gradle.includedBuild("buildC").projectDir == file("${buildC.toURI()}")
                assert gradle.includedBuilds.name == ["buildC"]

                task broken {
                    doLast {
                        assert gradle.includedBuilds.name == ["buildC"]
                        gradle.includedBuild("unknown")
                    }
                }
            """
        }
        includeBuild(buildB)

        buildA.buildFile << """
            task broken {
                dependsOn gradle.includedBuild("buildB").task(":broken")
            }
        """

        when:
        fails(buildA, "broken")

        then:
        failure.assertHasCause("Included build 'unknown' not found in build 'buildB'.")
    }

    def "other builds are not visible from included build"() {
        given:
        def buildC = singleProjectBuild("buildC") {
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
                        gradle.includedBuild("buildB")
                    }
                }
            """
        }
        def buildB = singleProjectBuild("buildB") {
            settingsFile << """
                includeBuild('${buildC.toURI()}')
            """
            buildFile << """
                task broken {
                    dependsOn gradle.includedBuild("buildC").task(":broken1")
                    dependsOn gradle.includedBuild("buildC").task(":broken2")
                }
            """
        }
        includeBuild(buildB)

        buildA.buildFile << """
            task broken {
                dependsOn gradle.includedBuild("buildB").task(":broken")
            }
        """

        when:
        executer.withArgument("--continue")
        fails(buildA, "broken")

        then:
        failure.assertHasCause("Included build 'buildA' not found in build 'buildC'.")
        failure.assertHasCause("Included build 'buildB' not found in build 'buildC'.")
    }
}
