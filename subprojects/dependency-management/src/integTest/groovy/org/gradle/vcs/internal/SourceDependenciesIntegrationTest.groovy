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
import org.gradle.vcs.fixtures.GitRepository
import org.junit.Rule

class SourceDependenciesIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    GitRepository first = new GitRepository('first', testDirectory)
    @Rule
    GitRepository second = new GitRepository('second', testDirectory)

    def setup() {
        buildFile << """
            apply plugin: 'base'
            group = 'org.gradle'
            version = '2.0'
            
            configurations {
                runtime 
            }
            dependencies {
                runtime "org.test:first:latest.integration"
            }
            
            task resolve {
                dependsOn configurations.runtime
                ext.message = "hello world"
                doLast {
                    def resolved = configurations.runtime.files
                    println "Looking for " + message
                    assert resolved.size() == 2
                    assert resolved[0].name == "first.txt"
                    assert resolved[0].text == message
                    assert resolved[1].name == "second.txt"
                    assert resolved[1].text == message
                }
            }
        """

        def commonConfiguration = """
            apply plugin: 'base'

            configurations {
                runtime
                'default' {
                    extendsFrom runtime
                }
            }
            
            task generate {
                dependsOn configurations.runtime
                ext.outputFile = new File(temporaryDir, project.name + ".txt")
                ext.message = "hello world"
                doLast {
                    // write to outputFile
                    println "Generating " + message
                    outputFile.parentFile.mkdirs()
                    outputFile.text = message
                }
            }
            
            artifacts {
                runtime (generate.outputFile) {
                    builtBy generate
                }
            }
        """

        buildTestFixture.withBuildInSubDir()
        singleProjectBuild("first") {
            buildFile << commonConfiguration
            buildFile << """
                dependencies {
                    runtime 'org.test:second:latest.integration'
                }
            """
        }
        first.commit("initial commit", first.listFiles())
        singleProjectBuild("second") {
            buildFile << commonConfiguration
        }
        second.commit("initial commit", second.listFiles())
    }

    def "can use source mappings in nested builds"() {
        settingsFile << """
            sourceControl {
                vcsMappings {
                    addRule('org.test group rule') { details ->
                        if (details.requested.group == "org.test") {
                            from vcs(GitVersionControlSpec) {
                                url = file(details.requested.module).toURI()
                            }
                        }
                    }
                }
            }
        """
        when:
        succeeds("resolve")
        then:
        result.assertTasksExecutedInOrder(":second:generate", ":first:generate", ":resolve")

        // Updating the remote repository causes changes downstream
        when:
        def message = "goodbye world"
        buildFile << """
            resolve.message = "$message"
        """
        file("first/build.gradle") << """
            generate.message = "$message"
        """
        file("second/build.gradle") << """
            generate.message = "$message"
        """
        first.commit("change message", file("first/build.gradle"))
        second.commit("change message", file("second/build.gradle"))
        then:
        succeeds("resolve")
    }
}
