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

import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.vcs.fixtures.GitFileRepository
import org.junit.Rule

class VcsMappingsIntegrationTest extends AbstractVcsIntegrationTest {
    @Rule
    GitFileRepository repo = new GitFileRepository('dep', temporaryFolder.getTestDirectory())
    def commit

    def setup() {
        commit = repo.commit('initial')
    }

    def "can define source repositories in root of composite build when child build has classpath dependencies"() {
        settingsFile << """
            includeBuild 'child'

            sourceControl {
                vcsMappings {
                    withModule("org.test:dep") {
                        from(GitVersionControlSpec) {
                            url = "${repo.url}"
                        }
                    }
                }
            }
        """

        def mavenRepo = new MavenFileRepository(file("maven-repo"))
        mavenRepo.module("other", "other", "1.0").publish()

        file("child").createDir()
        file("child/build.gradle").text = """
            buildscript {
                repositories {
                    maven { url '${mavenRepo.uri}' }
                }
                dependencies {
                    classpath "other:other:1.0"
                }
            }
        """

        expect:
        succeeds("help")
        assertRepoNotCheckedOut()
    }

    def "can use source dependency in build script classpath"() {
        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("org.test:dep") {
                        from(GitVersionControlSpec) {
                            url = "${repo.url}"
                        }
                    }
                }
            }
        """
        file("build.gradle").text = """
            buildscript { 
                dependencies {
                    classpath "org.test:dep:latest.integration"
                }
            }
            def dep = new Dep() 
        """

        when:
        run("help")

        then:
        result.assertTasksExecuted(":dep:compileJava", ":dep:processResources", ":dep:classes", ":dep:jar", ":help")
        assertRepoCheckedOut()
    }

    def 'emits sensible error when bad code is in vcsMappings block'() {
        settingsFile << """
            sourceControl {
                vcsMappings {
                    all { details ->
                        foo()
                    }
                }
            }
        """
        expect:
        fails('assemble')
        failure.assertHasDescription("Could not determine the dependencies of task ':compileJava'.")
        failure.assertHasFileName("Settings file '$settingsFile.path'")
        failure.assertHasLineNumber(5)
        failure.assertHasCause("Could not resolve all dependencies for configuration ':compileClasspath'.")
        failure.assertHasCause("Could not find method foo()")
    }

    def 'emits sensible error when bad vcs url in vcsMappings block'() {
        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("org.test:dep") {
                        from(GitVersionControlSpec) {
                            url = 'https://bad.invalid'
                        }
                    }
                }
            }
        """

        expect:
        fails('assemble')
        failure.assertHasDescription("Could not determine the dependencies of task ':compileJava'.")
        failure.assertHasCause("Could not resolve all dependencies for configuration ':compileClasspath'.")
        failure.assertHasCause("Could not locate default branch for Git repository at https://bad.invalid.")
    }

    def "can define and use source repositories with all {}"() {
        settingsFile << """
            sourceControl {
                vcsMappings {
                    all { details ->
                        if (details.requested.group == "org.test") {
                            from(GitVersionControlSpec) {
                                url = "${repo.url}"
                            }
                        }
                    }
                }
            }
        """
        expect:
        succeeds("assemble")
        assertRepoCheckedOut()
    }

    def "can define unused vcs mappings"() {
        settingsFile << """
            // include the missing dep as a composite
            includeBuild 'dep'
            
            sourceControl {
                vcsMappings {
                    withModule("unused:dep") {
                        from(GitVersionControlSpec) {
                            url = "does-not-exist"
                        }
                    }
                    all { details ->
                        if (details instanceof ModuleVersionSelector && details.requested.group == "unused") {
                            from(GitVersionControlSpec) {
                                url = "does-not-exist"
                            }
                        }
                    }
                }
            }
        """
        expect:
        succeeds("assemble")
        assertRepoNotCheckedOut()
    }

    def "last vcs mapping rule wins"() {
        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("org.test:dep") {
                        from(GitVersionControlSpec) {
                            url = "does-not-exist"
                        }
                    }
                    withModule("org.test:dep") {
                        from(GitVersionControlSpec) {
                            url = "${repo.url}"
                        }
                    }
                }
            }
        """
        expect:
        succeeds("assemble")
        assertRepoCheckedOut()
    }

    def 'main build can request plugins to be applied to source dependency build'() {
        singleProjectBuild("buildSrc") {
            file("src/main/groovy/MyPlugin.groovy") << """
                import org.gradle.api.*
                import org.gradle.api.initialization.*
                
                class MyPlugin implements Plugin<Settings> {
                    void apply(Settings settings) {
                        settings.gradle.allprojects {
                            apply plugin: 'java'
                            group = 'org.test'
                            version = '1.0'
                        }
                    }
                }
            """
            file("src/main/resources/META-INF/gradle-plugins/com.example.MyPlugin.properties") << """
                implementation-class=MyPlugin
            """
        }

        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("org.test:dep") {
                        from(GitVersionControlSpec) {
                            url = "${repo.url}"
                            plugins {
                                id "com.example.MyPlugin"
                            }
                        }
                    }
                }
            }
        """
        expect:
        succeeds('assemble')
        assertRepoCheckedOut()
    }

    def 'injected plugin can apply other plugins to source dependency build'() {
        singleProjectBuild("buildSrc") {
            file("src/main/groovy/MyProjectPlugin.groovy") << """
                import org.gradle.api.*
                
                class MyProjectPlugin implements Plugin<Project> {
                    void apply(Project project) {
                        project.apply plugin: 'java'
                        project.group = 'org.test'
                        project.version = '1.0'
                    }
                }
            """
            file("src/main/resources/META-INF/gradle-plugins/com.example.MyPlugin.properties") << """
                implementation-class=MyPlugin
            """
            file("src/main/groovy/MyPlugin.groovy") << """
                import org.gradle.api.*
                import org.gradle.api.initialization.*
                
                class MyPlugin implements Plugin<Settings> {
                    void apply(Settings settings) {
                        settings.gradle.allprojects {
                            apply plugin: MyProjectPlugin
                        }
                    }
                }
            """
            file("src/main/resources/META-INF/gradle-plugins/com.example.MyPlugin.properties") << """
                implementation-class=MyPlugin
            """
        }

        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("org.test:dep") {
                        from(GitVersionControlSpec) {
                            url = "${repo.url}"
                            plugins {
                                id "com.example.MyPlugin"
                            }
                        }
                    }
                }
            }
        """
        expect:
        succeeds('assemble')
        assertRepoCheckedOut()
    }

    def 'produces reasonable message when injected plugin does not exist'() {
        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("org.test:dep") {
                        from(GitVersionControlSpec) {
                            url = "${repo.url}"
                            plugins {
                                id "com.example.DoesNotExist"
                            }
                        }
                    }
                }
            }
        """
        expect:
        fails('assemble')
        assertRepoCheckedOut()
        failure.assertHasCause("Plugin with id 'com.example.DoesNotExist' not found.")
    }

    def 'can build from sub-directory of repository'() {
        def subdir = repo.file("subdir")
        repo.workTree.listFiles().each {
            if (it.name == '.git') {
                return
            }
            it.copyTo(subdir.file(it.name))
            it.delete()
        }
        commit = repo.commit('updated')
        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule('org.test:dep') {
                        from(GitVersionControlSpec) {
                            url = "${repo.url}"
                            rootDir = 'subdir'
                        }
                    }
                }
            }
        """
        expect:
        succeeds('assemble')
        assertRepoCheckedOut()
    }

    def 'fails with a reasonable message if rootDir is invalid'() {
        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule('org.test:dep') {
                        from(GitVersionControlSpec) {
                            url = "${repo.url}"
                            rootDir = null
                        }
                    }
                }
            }
        """
        expect:
        fails('assemble')
        failure.assertHasCause("rootDir should be non-null")
    }

    void assertRepoCheckedOut() {
        def checkout = checkoutDir(repo.name, commit.id.name, repo.id)
        checkout.file('.git').assertExists()
    }

    void assertRepoNotCheckedOut() {
        def checkout = checkoutDir(repo.name, commit.id.name, repo.id)
        checkout.assertDoesNotExist()
    }
}
