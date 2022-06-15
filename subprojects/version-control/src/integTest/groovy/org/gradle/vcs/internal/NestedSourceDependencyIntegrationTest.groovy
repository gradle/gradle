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
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.util.internal.TextUtil
import org.gradle.vcs.fixtures.GitFileRepository
import org.junit.Rule

class NestedSourceDependencyIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    GitFileRepository first = new GitFileRepository('first', testDirectory)
    @Rule
    GitFileRepository second = new GitFileRepository('second', testDirectory)
    @Rule
    GitFileRepository third = new GitFileRepository('third', testDirectory)
    @Rule
    GitFileRepository fourth = new GitFileRepository('fourth', testDirectory)

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
                    println "Generating " + message + " against " + configurations.runtime.files
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
        first.commit("initial commit")

        singleProjectBuild("second") {
            buildFile << commonConfiguration
        }
        second.commit("initial commit")

        singleProjectBuild("third") {
            buildFile << commonConfiguration
        }
        third.commit("initial commit")

        singleProjectBuild("fourth") {
            buildFile << commonConfiguration
        }
        fourth.commit("initial commit")
    }

    @ToBeFixedForConfigurationCache
    def "can use source mappings in nested builds"() {
        given:
        settingsFile << """
            sourceControl {
                vcsMappings {
                    all { details ->
                        if (details.requested.group == "org.test") {
                            from(GitVersionControlSpec) {
                                url = uri(details.requested.module)
                            }
                        }
                    }
                }
            }
        """
        dependency(first, "org.test:second")
        shouldResolve(first, second)

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

    @ToBeFixedForConfigurationCache
    def "can use source mappings defined in nested builds"() {
        given:
        vcsMapping('org.test:first', first)
        nestedVcsMapping(first, 'org.test:second', second)
        dependency(first, "org.test:second")
        shouldResolve(first, second)

        when:
        succeeds("resolve", "--info")
        then:
        result.assertTasksExecutedInOrder(":second:generate", ":first:generate", ":resolve")

        // Updating the remote repository causes changes downstream
        when:
        changeMessage("goodbye world", first, second)

        then:
        succeeds("resolve")
    }

    @ToBeFixedForConfigurationCache
    def "can use a source mapping defined in both the parent build and a nested build"() {
        given:
        settingsFile << """
            sourceControl {
                vcsMappings {
                    all { details ->
                        if (details.requested.group == "org.test") {
                            from(GitVersionControlSpec) {
                                url = uri(details.requested.module)
                            }
                        }
                    }
                }
            }
        """
        nestedVcsMapping(first, 'org.test:second', second)
        dependency(first, "org.test:second")
        shouldResolve(first, second)

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

    @ToBeFixedForConfigurationCache
    def "prefers a source mapping defined in the root build to one defined in a nested build"() {
        given:
        vcsMapping('org.test:first', first)
        vcsMapping('org.test:second', second)
        nestedVcsMapping(first, 'org.test:second', third)

        third.with {
            file('settings.gradle').text = """
                rootProject.name = 'second'
            """
            commit("Set project name to second", 'settings.gradle')
        }

        dependency(first, "org.test:second")
        shouldResolve(first, second)

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

    @ToBeFixedForConfigurationCache
    def "prefers a source mapping defined in the root build to one defined in a nested build when they differ only by plugins"() {
        given:
        def pluginBuilder = new PluginBuilder(file("plugin"))
        pluginBuilder.addSettingsPlugin """
            settings.gradle.allprojects {
                println "Hello from root build's plugin"
            }
        """, "org.gradle.test.MyPlugin", "MyPlugin"

        pluginBuilder.prepareToExecute()

        settingsFile << """
            includeBuild("plugin")
        """

        vcsMapping('org.test:first', first)
        // root build applies a plugin to second
        vcsMapping('org.test:second', second, ['org.gradle.test.MyPlugin'])
        // first build does not inject a plugin in second
        nestedVcsMapping(first, 'org.test:second', second)

        dependency(first, "org.test:second")
        shouldResolve(first, second)

        when:
        succeeds("resolve")

        then:
        result.assertTasksExecutedInOrder(":plugin:compileJava", ":plugin:compileGroovy", ":plugin:pluginDescriptors", ":plugin:processResources", ":plugin:classes", ":plugin:jar", ":second:generate", ":first:generate", ":resolve")
        outputContains("Hello from root build's plugin")
    }

    @ToBeFixedForConfigurationCache
    def "prefers a source mapping defined in the root build to one defined in a nested build when the nested build requests plugins"() {
        given:
        vcsMapping('org.test:first', first)
        // root build does not inject any plugins to second
        vcsMapping('org.test:second', second)
        // first build injects a plugin in second
        nestedVcsMapping(first, 'org.test:second', second, ['com.example.DoesNotExist'])

        dependency(first, "org.test:second")
        shouldResolve(first, second)

        when:
        succeeds("resolve")

        then:
        result.assertTasksExecutedInOrder(":second:generate", ":first:generate", ":resolve")
    }

    @ToBeFixedForConfigurationCache
    def "can use a source mapping defined similarly in two nested builds"() {
        given:
        vcsMapping('org.test:first', first)
        vcsMapping('org.test:second', second)
        nestedVcsMapping(first, 'org.test:third', third)
        nestedVcsMapping(second, 'org.test:third', third)
        dependency(first, "org.test:second")
        dependency(first, "org.test:third")
        dependency(second, "org.test:third")
        shouldResolve(first, second, third)

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
        given:
        vcsMapping('org.test:first', first)
        vcsMapping('org.test:second', second)
        nestedVcsMapping(first, 'org.test:third', third)
        nestedVcsMapping(second, 'org.test:third', fourth)

        fourth.with {
            file('settings.gradle').text = """
                rootProject.name = 'third'
            """
            commit("Set project name to third", 'settings.gradle')
        }

        dependency(first, "org.test:second")
        dependency(first, "org.test:third")
        dependency(second, "org.test:third")

        when:
        fails("resolve")

        then:
        failure.assertHasCause("Conflicting external source dependency rules were found in nested builds for org.test:third:latest.integration")
    }

    @ToBeFixedForConfigurationCache
    def "can resolve a mapping conflict by defining a rule in the root build"() {
        given:
        vcsMapping('org.test:first', first)
        vcsMapping('org.test:second', second)
        vcsMapping('org.test:third', third)
        nestedVcsMapping(first, 'org.test:third', third)
        nestedVcsMapping(second, 'org.test:third', fourth)

        fourth.with {
            file('settings.gradle').text = """
                rootProject.name = 'third'
            """
            commit("Set project name to third", 'settings.gradle')
        }

        dependency(first, "org.test:second")
        dependency(first, "org.test:third")
        dependency(second, "org.test:third")
        shouldResolve(first, second, third)

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

    void dependency(GitFileRepository consumer, String target) {
        consumer.file("build.gradle") << """
            dependencies {
                runtime '${target}:latest.integration'
            }
        """
        consumer.commit("Create dependency on ${target}", "build.gradle")
    }

    void shouldResolve(GitFileRepository... targets) {
        targets.each { target ->
            buildFile << """
                resolve.dependencies << "${target.workTree.name}"
                resolve.assertions << { resolved, message ->
                    def artifactFile = resolved.find { it.name == "${target.workTree.name}.txt" }
                    assert artifactFile != null
                    assert artifactFile.text == message
                }
            """
        }
    }

    void changeMessage(String message, GitFileRepository... repos) {
        buildFile << """
            resolve.message = "$message"
        """
        repos.each { repo ->
            repo.file("build.gradle") << """
                generate.message = "$message"
            """
            repo.commit("change message", "build.gradle")
        }
    }

    void vcsMapping(File settings, String module, String location, List<String> plugins) {
        String pluginsAsString = plugins.collect { "id '$it'" }.join('\n')

        settings << """
            sourceControl {
                vcsMappings {
                    withModule('${module}') {
                        from(GitVersionControlSpec) {
                            url = file('${location}').toURI()
                            plugins {
                                ${pluginsAsString}
                            }
                        }
                    }
                }
            }
        """
    }

    void vcsMapping(String module, GitFileRepository repo, List<String> plugins=[]) {
        vcsMapping(settingsFile, module, repo.getWorkTree().name, plugins)
    }

    void nestedVcsMapping(GitFileRepository repo, String module, GitFileRepository target, List<String> plugins=[]) {
        vcsMapping(repo.file('settings.gradle'), module, TextUtil.normaliseFileSeparators(file(target.workTree.name).absolutePath), plugins)
        repo.commit("add source mapping", 'settings.gradle')
    }
}
