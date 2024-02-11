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

package org.gradle.vcs.internal

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.vcs.fixtures.GitFileRepository
import org.junit.Rule

class BuildSrcSourceDependenciesIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    GitFileRepository first = new GitFileRepository('first', testDirectory)

    @ToBeFixedForConfigurationCache(because = "source dependency VCS mappings are defined")
    def "can build with a source dependency that has a buildSrc directory"() {
        buildTestFixture.withBuildInSubDir()
        vcsMapping('org.test:first', first)
        singleProjectBuild("first") {
            buildFile << """
                apply plugin: "java"
                def foo = new Foo()
            """
            file("buildSrc/src/main/java/Foo.java") << """
                class Foo { }
            """
        }
        first.commit("initial commit")

        buildFile << """
            configurations {
                foo
            }

            dependencies {
                foo "org.test:first:latest.integration"
            }

            task resolve {
                doLast {
                    println configurations.foo.files.collect { it.name }
                }
            }
        """

        expect:
        succeeds("resolve")

        and:
        outputContains("[first-1.0.jar]")
    }

    void vcsMapping(String module, GitFileRepository repo) {
        String location = repo.getWorkTree().name
        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule('${module}') {
                        from(GitVersionControlSpec) {
                            url = file('${location}').toURI()
                        }
                    }
                }
            }
        """
    }
}
