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
import org.gradle.vcs.internal.spec.DirectoryRepositorySpec
import spock.lang.Ignore

@Ignore("none of this works yet")
class MultiprojectVcsIntegrationTest extends AbstractIntegrationSpec implements SourceDependencies {
    def B

    def setup() {
        buildTestFixture.withBuildInSubDir()
        B = multiProjectBuild("B", ["foo", "bar"]) {
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
        assertResolvesTo("foo-1.0.jar", "bar-SNAPSHOT.jar")
    }

    void mappingFor(String... coords) {
        settingsFile << """
            import ${DirectoryRepositorySpec.canonicalName}

            sourceControl {
                vcsMappings {
        """
        coords.each { coord ->
            settingsFile << """
                    withModule(${coord}) {
                        from vcs(DirectoryRepositorySpec) {
                            sourceDir = file("${B.name}")
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
