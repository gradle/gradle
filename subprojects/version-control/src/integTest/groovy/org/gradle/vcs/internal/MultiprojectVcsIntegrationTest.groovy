/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.vcs.internal

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.build.BuildTestFile
import org.gradle.test.fixtures.file.TestFile
import org.gradle.vcs.internal.spec.DirectoryRepositorySpec

class MultiprojectVcsIntegrationTest extends AbstractIntegrationSpec implements SourceDependencies {
    BuildTestFile buildB

    def setup() {
        buildTestFixture.withBuildInSubDir()
        buildB = multiProjectBuild("B", ["foo", "bar"]) {
            buildFile << """
                allprojects {
                    apply plugin: 'java'

                    repositories { maven { url "${mavenRepo.uri}" } }
                }
            """
        }
        buildFile << """
            apply plugin: 'base'
            
            repositories { maven { url "${mavenRepo.uri}" } }

            configurations {
                conf
            }
            
            task resolve {
                dependsOn configurations.conf
                doLast {
                    def expectedResult = result.split(",")
                    assert configurations.conf.files.collect { it.name } == expectedResult
                }
            }
        """
    }

    def "can resolve subproject of multi-project source dependency"() {
        mappingFor("org.test:foo")
        buildFile << """
            dependencies {
                conf 'org.test:foo:latest.integration'
            }
        """
        expect:
        assertResolvesTo("foo-1.0.jar")
    }

    def "can resolve root of multi-project source dependency"() {
        mappingFor("org.test:B")
        buildFile << """
            dependencies {
                conf 'org.test:B:latest.integration'
            }
        """
        expect:
        assertResolvesTo("B-1.0.jar")
    }

    def "can resolve multiple projects of multi-project source dependency"() {
        mappingFor("org.test:foo", "org.test:bar")
        buildFile << """
            dependencies {
                conf 'org.test:foo:latest.integration'
                conf 'org.test:bar:latest.integration'
            }
        """
        expect:
        assertResolvesTo("foo-1.0.jar", "bar-1.0.jar")
    }

    def "only resolves a single project of multi-project source dependency"() {
        mavenRepo.module("org.test", "bar", "1.0-SNAPSHOT").withNonUniqueSnapshots().publish()
        mappingFor("org.test:foo")
        buildFile << """
            dependencies {
                conf 'org.test:foo:latest.integration'
                conf 'org.test:bar:latest.integration'
            }
        """
        expect:
        // foo should be from the source dependencies and bar should be from the external repo
        assertResolvesTo("foo-1.0.jar", "bar-1.0-SNAPSHOT.jar")
    }

    def "uses included build subproject of multi-project source dependency"() {
        mavenRepo.module("org.test", "bar", "1.0-SNAPSHOT").withNonUniqueSnapshots().publish()
        buildB.buildFile << """
            project(":foo") {
                dependencies {
                    compile project(":bar")
                }
            }
        """
        mappingFor("org.test:foo")
        buildFile << """
            dependencies {
                conf 'org.test:foo:latest.integration'
                conf 'org.test:bar:latest.integration'
            }
        """
        expect:
        assertResolvesTo("foo-1.0.jar", "bar-1.0.jar")
    }

    def "uses root mapping for duplicate subproject of multi-project source dependency"() {
        buildB.buildFile << """
            project(":foo") {
                dependencies {
                    compile project(":bar")
                }
            }
        """
        def buildBar = singleProjectBuild("bar") {
            buildFile << """
                apply plugin: 'java'
                version = "2.0"
            """
        }

        mappingFor(buildB, "org.test:foo")
        mappingFor(buildBar, "org.test:bar")
        buildFile << """
            dependencies {
                conf 'org.test:foo:latest.integration'
                conf 'org.test:bar:latest.integration'
            }
        """
        expect:
        assertResolvesTo("foo-1.0.jar", "bar-2.0.jar")
    }

    def "reasonable error when VCS mapping does not match underlying build"() {
        mavenRepo.module("org.test", "bar", "1.0-SNAPSHOT").withNonUniqueSnapshots().publish()
        buildB.buildFile << """
            allprojects {
                group = "new.group"
            }
        """
        mappingFor("org.test:foo")
        buildFile << """
            dependencies {
                conf 'org.test:foo:latest.integration'
            }
        """
        expect:
        fails("resolve")
        failure.assertHasCause("dir repo ${buildB} did not contain a project publishing the specified dependency.")
    }

    void mappingFor(TestFile repo=buildB, String... coords) {
        settingsFile << """
            import ${DirectoryRepositorySpec.canonicalName}

            sourceControl {
                vcsMappings {
        """
        coords.each { coord ->
            settingsFile << """
                    withModule("${coord}") {
                        from(DirectoryRepositorySpec) {
                            sourceDir = file("${repo.name}")
                        }
                    }
            """
        }
        settingsFile << """
                }
            }
        """
    }

    void assertResolvesTo(String... files) {
        def result = "-Presult=" + files.join(',')
        succeeds("resolve", result)
    }
}
