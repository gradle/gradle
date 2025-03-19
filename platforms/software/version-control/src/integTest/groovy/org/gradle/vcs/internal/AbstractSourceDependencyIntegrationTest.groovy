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
import org.gradle.integtests.fixtures.build.BuildTestFile
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.vcs.fixtures.GitFileRepository
import org.junit.Rule

abstract class AbstractSourceDependencyIntegrationTest extends AbstractIntegrationSpec implements SourceDependencies {
    @Rule
    GitFileRepository repo = new GitFileRepository('dep', temporaryFolder.getTestDirectory())
    def commit
    BuildTestFile depProject

    void mappingFor(GitFileRepository gitRepo, String coords, String repoDef = "") {
        mappingFor(gitRepo.url.toString(), coords, repoDef)
    }

    abstract void mappingFor(String gitRepo, String coords, String repoDef = "")

    def setup() {
        buildFile << """
            apply plugin: 'java'
            group = 'org.gradle'
            version = '2.0'

            dependencies {
                implementation "org.test:dep:latest.integration"
            }
        """
        file("src/main/java/Main.java") << """
            public class Main {
                Dep dep = null;
            }
        """
        buildTestFixture.withBuildInSubDir()
        depProject = singleProjectBuild("dep") {
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                    group = 'org.test'
                }
            """
            file("src/main/java/Dep.java") << "public class Dep {}"
        }
        commit = repo.commit('initial')
    }

    @ToBeFixedForConfigurationCache(because = "source dependencies")
    def "can define source repositories in root of composite build when child build has classpath dependencies"() {
        settingsFile << """
            includeBuild 'child'
        """
        mappingFor(repo, "org.test:dep")

        def mavenRepo = new MavenFileRepository(file("maven-repo"))
        mavenRepo.module("other", "other", "1.0").publish()

        file("child").createDir()
        file("child/build.gradle").text = """
            buildscript {
                repositories {
                    maven { url = '${mavenRepo.uri}' }
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

    @ToBeFixedForConfigurationCache(because = "source dependencies")
    def "can use source dependency in build script classpath"() {
        mappingFor(repo, "org.test:dep")
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


    def 'emits sensible error when bad vcs url'() {
        mappingFor("https://bad.invalid", "org.test:dep")

        expect:
        fails('assemble')
        failure.assertHasDescription("Could not determine the dependencies of task ':compileJava'.")
        failure.assertHasCause("Could not resolve all dependencies for configuration ':compileClasspath'.")
        failure.assertHasCause("Could not locate default branch for Git repository at https://bad.invalid.")
    }

    @ToBeFixedForConfigurationCache
    def "can define unused vcs mappings"() {
        settingsFile << """
            // include the missing dep as a composite
            includeBuild 'dep'
        """
        mappingFor("does-not-exist", "unused:dep")
        expect:
        succeeds("assemble")
        assertRepoNotCheckedOut()
    }

    @ToBeFixedForConfigurationCache
    def "last vcs mapping rule wins"() {
        mappingFor("does-not-exist", "org.test:dep")
        mappingFor(repo, "org.test:dep")
        expect:
        succeeds("assemble")
        assertRepoCheckedOut()
    }

    @ToBeFixedForConfigurationCache
    def 'main build can request plugins to be applied to source dependency build'() {
        def pluginBuilder = new PluginBuilder(file("plugin"))
        pluginBuilder.addSettingsPlugin """
            settings.gradle.allprojects {
                apply plugin: 'java'
                group = 'org.test'
                version = '1.0'
            }
        """, "org.gradle.test.MyPlugin", "MyPlugin"
        pluginBuilder.prepareToExecute()

        settingsFile << """
            includeBuild("plugin")
        """

        mappingFor(repo, "org.test:dep", 'plugins { id("org.gradle.test.MyPlugin") }')

        expect:
        succeeds('assemble')
        assertRepoCheckedOut()
    }

    @ToBeFixedForConfigurationCache
    def 'injected plugin can apply other plugins to source dependency build'() {
        def pluginBuilder = new PluginBuilder(file("plugin"))
        pluginBuilder.addPlugin """
            project.apply plugin: 'java'
            project.group = 'org.test'
            project.version = '1.0'
        """, "org.gradle.test.MyProjectPlugin", "MyProjectPlugin"

        pluginBuilder.addSettingsPlugin """
            settings.gradle.allprojects {
                apply plugin: MyProjectPlugin
            }
        """, "org.gradle.test.MySettingsPlugin", "MySettingsPlugin"

        pluginBuilder.prepareToExecute()

        settingsFile << """
            includeBuild("plugin")
        """

        mappingFor(repo, "org.test:dep", 'plugins { id("org.gradle.test.MySettingsPlugin") }')

        expect:
        succeeds('assemble')
        assertRepoCheckedOut()
    }

    def 'produces reasonable message when injected plugin does not exist'() {
        mappingFor(repo, "org.test:dep", 'plugins { id ("com.example.DoesNotExist") }')

        expect:
        fails('assemble')
        assertRepoCheckedOut()
        failure.assertHasDescription("Plugin [id: 'com.example.DoesNotExist'] was not found in any of the following sources:")
    }

    @ToBeFixedForConfigurationCache
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
        mappingFor(repo, "org.test:dep", 'rootDir = "subdir"')

        expect:
        succeeds('assemble')
        assertRepoCheckedOut()
    }

    def 'fails with a reasonable message if rootDir is invalid'() {
        mappingFor(repo, "org.test:dep", 'rootDir = null')
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
