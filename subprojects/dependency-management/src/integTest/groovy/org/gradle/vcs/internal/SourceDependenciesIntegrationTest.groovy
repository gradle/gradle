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
import org.gradle.util.TextUtil
import org.gradle.vcs.fixtures.GitRepository
import org.junit.Rule

class SourceDependenciesIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    GitRepository first = new GitRepository('first', testDirectory)
    @Rule
    GitRepository second = new GitRepository('second', testDirectory)
    @Rule
    GitRepository third = new GitRepository('third', testDirectory)
    @Rule
    GitRepository fourth = new GitRepository('fourth', testDirectory)

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
                ext.dependencies = []
                ext.assertions = []
                doLast {
                    def resolved = configurations.runtime.files
                    println "Looking for " + message
                    assert resolved.size() == dependencies.size()
                    assertions.each { it.call(resolved, message) }
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
        }
        first.commit("initial commit", first.listFiles())

        singleProjectBuild("second") {
            buildFile << commonConfiguration
        }
        second.commit("initial commit", second.listFiles())

        singleProjectBuild("third") {
            buildFile << commonConfiguration
        }
        third.commit("initial commit", third.listFiles())

        singleProjectBuild("fourth") {
            buildFile << commonConfiguration
        }
        fourth.commit("initial commit", fourth.listFiles())
    }

    def "can use source mappings in nested builds"() {
        given:
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
        dependency(first, "second")
        shouldResolve("first", "second")

        when:
        succeeds("resolve")
        then:
        result.assertTasksExecutedInOrder(":second:generate", ":first:generate", ":resolve")

        // Updating the remote repository causes changes downstream
        when:
        changeMessage("goodbye world", first, second)

        then:
        succeeds("resolve")
    }

    def "can use source mappings defined in nested builds"() {
        def secondPath = TextUtil.normaliseFileSeparators(file('second').absolutePath)

        given:
        vcsMapping('first', 'first')
        nestedVcsMapping(first, 'second', secondPath)
        dependency(first, "second")
        shouldResolve("first", "second")

        when:
        succeeds("resolve")
        then:
        result.assertTasksExecutedInOrder(":second:generate", ":first:generate", ":resolve")

        // Updating the remote repository causes changes downstream
        when:
        changeMessage("goodbye world", first, second)

        then:
        succeeds("resolve")
    }

    def "can use a source mapping defined in both the parent build and a nested build"() {
        def secondPath = TextUtil.normaliseFileSeparators(file('second').absolutePath)

        given:
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
        nestedVcsMapping(first, 'second', secondPath)
        dependency(first, "second")
        shouldResolve("first", "second")

        when:
        succeeds("resolve")
        then:
        result.assertTasksExecutedInOrder(":second:generate", ":first:generate", ":resolve")

        // Updating the remote repository causes changes downstream
        when:
        changeMessage("goodbye world", first, second)

        then:
        succeeds("resolve")
    }

    def "prefers a source mapping defined in the root build to one defined in a nested build"() {
        def thirdPath = TextUtil.normaliseFileSeparators(file('third').absolutePath)

        given:
        vcsMapping('first', 'first')
        vcsMapping('second', 'second')
        nestedVcsMapping(first, 'second', thirdPath)

        third.with {
            file('settings.gradle').text = """
                rootProject.name = 'second'
            """
            commit("Set project name to second", file('settings.gradle'))
        }

        dependency(first, "second")
        shouldResolve("first", "second")

        when:
        succeeds("resolve")

        then:
        result.assertTasksExecutedInOrder(":second:generate", ":first:generate", ":resolve")

        // Updating the remote repository causes changes downstream
        when:
        changeMessage("goodbye world", first, second)

        then:
        succeeds("resolve")
    }

    def "can use a source mapping defined similarly in two nested builds"() {
        def thirdPath = TextUtil.normaliseFileSeparators(file('third').absolutePath)

        given:
        vcsMapping('first', 'first')
        vcsMapping('second', 'second')
        nestedVcsMapping(first, 'third', thirdPath)
        nestedVcsMapping(second, 'third', thirdPath)
        dependency(first, "second")
        dependency(first, "third")
        dependency(second, "third")
        shouldResolve("first", "second", "third")

        when:
        succeeds("resolve")

        then:
        result.assertTasksExecutedInOrder(":third:generate", ":second:generate", ":first:generate", ":resolve")

        // Updating the remote repository causes changes downstream
        when:
        changeMessage("goodbye world", first, second, third)

        then:
        succeeds("resolve")
    }

    def "produces a sensible error when nested builds define conflicting source mappings"() {
        def thirdPath = TextUtil.normaliseFileSeparators(file('third').absolutePath)
        def fourthPath = TextUtil.normaliseFileSeparators(file('fourth').absolutePath)

        given:
        vcsMapping('first', 'first')
        vcsMapping('second', 'second')
        nestedVcsMapping(first, 'third', thirdPath)
        nestedVcsMapping(second, 'third', fourthPath)

        fourth.with {
            file('settings.gradle').text = """
                rootProject.name = 'third'
            """
            commit("Set project name to third", file('settings.gradle'))
        }

        dependency(first, "second")
        dependency(first, "third")
        dependency(second, "third")

        when:
        fails("resolve")

        then:
        failure.assertHasCause("Conflicting external source dependency rules were found in nested builds for org.test:third:latest.integration")
    }

    def "can resolve a mapping conflict by defining a rule in the root build"() {
        def thirdPath = TextUtil.normaliseFileSeparators(file('third').absolutePath)
        def fourthPath = TextUtil.normaliseFileSeparators(file('fourth').absolutePath)

        given:
        vcsMapping('first', 'first')
        vcsMapping('second', 'second')
        vcsMapping('third', 'third')
        nestedVcsMapping(first, 'third', thirdPath)
        nestedVcsMapping(second, 'third', fourthPath)

        fourth.with {
            file('settings.gradle').text = """
                rootProject.name = 'third'
            """
            commit("Set project name to third", file('settings.gradle'))
        }

        dependency(first, "second")
        dependency(first, "third")
        dependency(second, "third")
        shouldResolve("first", "second", "third")

        when:
        succeeds("resolve")

        then:
        result.assertTasksExecutedInOrder(":third:generate", ":second:generate", ":first:generate", ":resolve")

        // Updating the remote repository causes changes downstream
        when:
        changeMessage("goodbye world", first, second, third)

        then:
        succeeds("resolve")
    }

    void dependency(GitRepository consumer, String target) {
        consumer.file("build.gradle") << """
            dependencies {
                runtime 'org.test:${target}:latest.integration'
            }
        """
        consumer.commit("Create dependency on ${target}", consumer.file("build.gradle"))
    }

    void shouldResolve(String... targets) {
        targets.each { target ->
            buildFile << """
                resolve.dependencies << "${target}"
                resolve.assertions << { resolved, message ->
                    def artifactFile = resolved.find { it.name == "${target}.txt" }
                    assert artifactFile != null
                    assert artifactFile.text == message
                }
            """
        }
    }

    void changeMessage(String message, GitRepository... repos) {
        buildFile << """
            resolve.message = "$message"
        """
        repos.each { repo ->
            repo.file("build.gradle") << """
                generate.message = "$message"
            """
            repo.commit("change message", repo.file("build.gradle"))
        }
    }

    void vcsMapping(File settings, String module, String location) {
        settings << """
            sourceControl {
                vcsMappings {
                    withModule('org.test:${module}') {
                        from vcs(GitVersionControlSpec) {
                            url = file('${location}').toURI()
                        }
                    }
                }
            }
        """
    }

    void vcsMapping(String module, String location) {
        vcsMapping(settingsFile, module, location)
    }

    void nestedVcsMapping(GitRepository repo, String module, String location) {
        vcsMapping(repo.file('settings.gradle'), module, location)
        repo.commit("add source mapping", repo.file('settings.gradle'))
    }
}
