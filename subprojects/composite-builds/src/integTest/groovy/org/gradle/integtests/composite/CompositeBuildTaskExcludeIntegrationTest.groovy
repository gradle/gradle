/*
 * Copyright 2020 the original author or authors.
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

import spock.lang.Issue

class CompositeBuildTaskExcludeIntegrationTest extends AbstractCompositeBuildTaskExecutionIntegrationTest {

    def setup() {
        createDirs("sub", "included", "included/sub")
        settingsFile << """
            rootProject.name = 'root'
            include('sub')
            includeBuild('included')
        """
        file('included/settings.gradle') << """
            include('sub')
        """
        buildFile << """
            def test = tasks.register('test') {
                doLast {
                     println 'test executed'
                }
            }

            tasks.register('build') {
                doLast {
                     println 'build executed'
                }
                dependsOn test
                dependsOn gradle.includedBuild('included').task(':build')
            }

            project(':sub') {
                def subTest = tasks.register('test') {
                    doLast {
                         println 'sub test executed'
                    }
                }

                tasks.register('build') {
                    doLast {
                        println 'sub build executed'
                    }
                    dependsOn subTest
                    dependsOn gradle.includedBuild('included').task(':sub:build')
                }
            }
        """
        file('included/build.gradle') << """
            def test = tasks.register('test') {
                doLast {
                     println 'included test executed'
                }
            }

            tasks.register('build') {
                doLast {
                     println 'included build executed'
                }
                dependsOn test
            }

            project(':sub') {
                def subTest = tasks.register('test') {
                    doLast {
                         println 'included sub test executed'
                    }
                }
                tasks.register('build') {
                    doLast {
                         println 'included sub build executed'
                    }
                    dependsOn subTest
                }
            }
        """
    }

    def "can exclude tasks from an included build"() {
        expect:
        2.times {
            succeeds("build", "-x", ":included:sub:test")
            result.assertTasksExecuted(":test", ":build", ":sub:test", ":sub:build", ":included:test", ":included:build", ":included:sub:build")
            result.assertTaskNotExecuted(":included:sub:test")
        }
        2.times {
            succeeds("build", "-x", "included:sub:test")
            result.assertTasksExecuted(":test", ":build", ":sub:test", ":sub:build", ":included:test", ":included:build", ":included:sub:build")
            result.assertTaskNotExecuted(":included:sub:test")
        }
    }

    def "can exclude tasks using pattern matching"() {
        expect:
        2.times {
            succeeds("build", "-x", ":included:sub:te")
            result.assertTasksExecuted(":test", ":build", ":sub:test", ":sub:build", ":included:test", ":included:build", ":included:sub:build")
            result.assertTaskNotExecuted(":included:sub:test")
        }
        2.times {
            succeeds("build", "-x", "i:s:te")
            result.assertTasksExecuted(":test", ":build", ":sub:test", ":sub:build", ":included:test", ":included:build", ":included:sub:build")
            result.assertTaskNotExecuted(":included:sub:test")
        }
    }

    def "excluding a task from a root project does not affect included task with same path"() {
        when:
        succeeds("build", "-x", ":sub:test")

        then:
        result.assertTasksExecuted(":test", ":build", ":sub:build", ":included:test", ":included:build", ":included:sub:test", ":included:sub:build")
        result.assertTaskNotExecuted(":sub:test")
    }

    def "cannot use unqualified task paths to exclude tasks from included build roots"() {
        when:
        run("build", "-x", "test")

        then:
        result.assertTasksExecuted(":build", ":sub:build", ":included:build", ":included:sub:build", ":included:test", ":included:sub:test")
        result.assertTaskNotExecuted(":test")
        result.assertTaskNotExecuted(":sub:test")
    }

    def "cannot use unqualified task paths to exclude tasks from included build subproject"() {
        when:
        run("build", "-x", "sub:test")

        then:
        result.assertTasksExecuted(":test", ":build", ":sub:build", ":included:test", ":included:build", ":included:sub:test", ":included:sub:build")
        result.assertTaskNotExecuted(":sub:test")
    }

    def "can exclude task from main build when root build uses project plugin from included build"() {
        setup:
        settingsFile << "includeBuild('build-logic')"
        def rootDir = file("build-logic")
        addPluginIncludedBuild(rootDir)
        buildFile.text = """
            plugins {
                id("test.plugin")
                id("java-library")
            }
        """

        expect:
        2.times {
            succeeds("assemble", "-x", "jar")
            result.assertTaskNotExecuted(":jar")
        }
    }

    def "can exclude task from main build when root build uses settings plugin from included build"() {
        setup:
        def rootDir = file("build-logic")
        addSettingsPluginIncludedBuild(rootDir)
        settingsFile.text = """
            pluginManagement {
                includeBuild("build-logic")
            }
            plugins {
                id("test.plugin")
            }
        """
        buildFile.text = """
            plugins {
                id("java-library")
            }
        """

        expect:
        2.times {
            succeeds("assemble", "-x", "jar")
            result.assertTaskNotExecuted(":jar")
        }
    }

    def "can exclude task from included build that produces a project plugin used from root build"() {
        setup:
        settingsFile << "includeBuild('build-logic')"
        def rootDir = file("build-logic")
        addPluginIncludedBuild(rootDir)
        buildFile.text = """
            plugins {
                id("test.plugin")
                id("java-library")
            }
        """

        expect:
        succeeds("greeting", ":build-logic:classes")
        2.times {
            succeeds("greeting", "-x", ":build-logic:classes")
            result.assertTaskNotExecuted(":build-logic:classes")
        }
    }

    def "can exclude task from included build that is a dependency of the root build and also produces a project plugin used from root build"() {
        setup:
        settingsFile << "includeBuild('build-logic')"
        def rootDir = file("build-logic")
        addPluginIncludedBuild(rootDir)
        buildFile.text = """
            plugins {
                id("test.plugin")
                id("java-library")
            }
            dependencies {
                implementation("lib:lib:1.0")
            }
        """

        expect:
        succeeds("greeting", ":build-logic:classes")
        2.times {
            succeeds("greeting", "-x", ":build-logic:jar")
            result.assertTaskNotExecuted(":build-logic:jar")
            result.assertTaskNotExecuted(":build-logic:compileJava")
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/21708")
    def "can exclude task from included build that requires a project plugin from another build"() {
        setup:
        settingsFile << """
            includeBuild('build-logic')
            includeBuild('app')
        """
        def rootDir = file("build-logic")
        addPluginIncludedBuild(rootDir)
        file("app/build.gradle") << """
            plugins {
                id("test.plugin")
                id("java-library")
            }
        """

        expect:
        2.times {
            succeeds(":app:assemble", "-x", ":app:processResources")
            result.assertTaskNotExecuted(":app:processResources")
        }
    }
}
