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

import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.vcs.fixtures.GitFileRepository
import org.junit.Rule
import spock.lang.Issue

class GitVcsIntegrationTest extends AbstractVcsIntegrationTest {
    @Rule
    GitFileRepository repo = new GitFileRepository('dep', temporaryFolder.getTestDirectory())

    @Rule
    GitFileRepository deeperRepo = new GitFileRepository('deeperDep', temporaryFolder.getTestDirectory())

    @Rule
    GitFileRepository evenDeeperRepo = new GitFileRepository('evenDeeperDep', temporaryFolder.getTestDirectory())

    def 'can define and use source repositories'() {
        given:
        def commit = repo.commit('initial commit')

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
        expect:
        succeeds('assemble')
        // Git repo is cloned
        def gitCheckout = checkoutDir(repo.name, commit.id.name, repo.id)
        gitCheckout.file('.git').assertExists()
    }

    def 'can define and use source repositories with submodules'() {
        given:
        // Populate submodule origin
        evenDeeperRepo.file('foo').text = "baz"
        evenDeeperRepo.commit('initial commit', "foo")
        deeperRepo.file('foo').text = "bar"
        deeperRepo.commit("initial commit", "foo")
        // Add submodule to repo
        deeperRepo.addSubmodule(evenDeeperRepo)
        repo.addSubmodule(deeperRepo)
        def commit = repo.commit('initial commit')

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

        when:
        succeeds('assemble')

        then:
        // Git repo is cloned
        def gitCheckout = checkoutDir(repo.name, commit.id.name, repo.id)
        gitCheckout.file('.git').assertExists()
        // Submodule is cloned
        gitCheckout.file('deeperDep/.git').assertExists()
        gitCheckout.file('deeperDep/foo').text == "bar"
        gitCheckout.file('deeperDep/evenDeeperDep/.git').assertExists()
        gitCheckout.file('deeperDep/evenDeeperDep/foo').text == "baz"

        when:
        // Update submodule origin
        evenDeeperRepo.file('foo').text = "buzz"
        evenDeeperRepo.commit("update file", "foo")
        deeperRepo.file('foo').text = "baz"
        deeperRepo.commit("update file", "foo")
        // Update parent repository
        deeperRepo.updateSubmodulesToLatest()
        commit = repo.updateSubmodulesToLatest()
        gitCheckout = checkoutDir(repo.name, commit.id.name, repo.id)
        succeeds('assemble')

        then:
        // Submodule is updated
        gitCheckout.file('deeperDep/foo').text == "baz"
        gitCheckout.file('deeperDep/evenDeeperDep/foo').text == "buzz"
    }

    @Issue('gradle/gradle-native#206')
    def 'can define and use source repositories with initscript resolution present'() {
        given:
        def commit = repo.commit('initial commit')
        temporaryFolder.file('initialize.gradle') << """
        initscript {            
            dependencies {
                classpath files('classpath.file')
            }
        }
        allprojects { p ->
            println "Initialization of \${p}"
        }
        """
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
        expect:
        succeeds('assemble', '-I', 'initialize.gradle')
        // Git repo is cloned
        def gitCheckout = checkoutDir(repo.name, commit.id.name, repo.id)
        gitCheckout.file('.git').assertExists()
    }

    @Issue('gradle/gradle-native#207')
    def 'can use repositories even when clean is run'() {
        given:
        def commit = repo.commit('initial commit')

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

        expect:
        succeeds('assemble')

        when:
        succeeds('clean', 'assemble')

        then:
        // Git repo is cloned
        def gitCheckout = checkoutDir(repo.name, commit.id.name, repo.id)
        gitCheckout.file('.git').assertExists()
    }

    def 'handle missing version by adding tag to git repository'() {
        given:
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
        def commit = repo.commit('initial commit')
        repo.createLightWeightTag('1.3.0')

        def javaFile = file('dep/src/main/java/Dep.java')
        javaFile.replace('class', 'interface')
        repo.commit('Changed Dep to an interface')

        buildFile.replace('latest.integration', '1.4.0')

        when:
        fails('assemble')

        then:
        failureCauseContains("Could not resolve org.test:dep:1.4.0. Git Repository at file:")
        failureCauseContains("does not contain a version matching 1.4.0")

        when:
        javaFile.replace('interface', 'class')
        repo.commit('Switch it back to a class.')
        repo.createLightWeightTag('1.4.0')

        then:
        succeeds('assemble')
    }

    def 'can handle conflicting versions'() {
        given:
        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule('org.test:dep') {
                        from(GitVersionControlSpec) {
                            url = "${repo.url}"
                        }
                    }
                }
            }
        """

        buildFile << """
            apply plugin: 'java'
            group = 'org.gradle'
            version = '2.0'
            
            dependencies {
                compile "org.test:dep:1.3.0"
                compile "org.test:dep:1.4.0"
            }
        """
        def commit = repo.commit('initial commit')
        repo.createLightWeightTag('1.3.0')
        def javaFile = file('dep/src/main/java/Dep.java')
        javaFile.replace('class', 'interface')
        def commit2 = repo.commit('Changed Dep to an interface')
        repo.createLightWeightTag('1.4.0')

        when:
        succeeds('assemble')

        then:
        def gitCheckout = checkoutDir(repo.name, commit.id.name, repo.id)
        gitCheckout.file('.git').assertExists()
        def gitCheckout2 = checkoutDir(repo.name, commit2.id.name, repo.id)
        gitCheckout2.file('.git').assertExists()
    }

    def 'uses root project cache directory'() {
        given:
        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule('org.test:dep') {
                        from(GitVersionControlSpec) {
                            url = "${repo.url}"
                        }
                    }
                }
            }
        """

        singleProjectBuild("deeperDep") {
            buildFile << """
                apply plugin: 'java'
            """
            file("src/main/java/DeeperDep.java") << "public class DeeperDep {}"
        }

        depProject.buildFile << """
            dependencies {
                implementation "org.test:deeperDep:latest.integration"
            }
        """
        depProject.settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule('org.test:deeperDep') {
                        from(GitVersionControlSpec) {
                            url = "${deeperRepo.url}"
                        }
                    }
                }
             }
        """

        def depCommit = repo.commit('initial commit')
        def deeperCommit = deeperRepo.commit('initial commit')

        when:
        succeeds('assemble')

        then:
        def depCheckout = checkoutDir('dep', depCommit.id.name, "git-repo:${repo.url.toASCIIString()}")
        depCheckout.file('.git').assertExists()

        def depDeeperCheckout = checkoutDir('deeperDep', depCommit.id.name, "git-repo:${repo.url.toASCIIString()}", depCheckout)
        depDeeperCheckout.assertDoesNotExist()

        def deeperCheckout = checkoutDir('deeperDep', deeperCommit.id.name, "git-repo:${deeperRepo.url.toASCIIString()}")
        deeperCheckout.file('.git').assertExists()
    }

    def 'can resolve the same version for latest.integration within the same build session'() {
        given:
        BlockingHttpServer server = new BlockingHttpServer()
        server.start()
        settingsFile << """
        include 'bar'
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

        file('bar/src/main/java/Bar.java') << "public class Bar extends Dep {}"
        file('src/main/java/Foo.java') << """
            public class Foo extends Dep {
                Foo(Bar d) {}
            }
        """
        buildFile << """
            project(':bar') {
                apply plugin: 'java'
                dependencies {
                    compile 'org.test:dep:latest.integration'
                }
            }

            configurations.compileClasspath.incoming.afterResolve {
                ${server.callFromBuild("block")}
            }

            dependencies {
                compile project(':bar')
            }
        """
        def commit = repo.commit('initial commit')
        def block = server.expectAndBlock("block")

        when:
        // Start the build then wait until the first configuration is resolved.
        executer.withTasks("assemble")
        def build = executer.start()
        block.waitForAllPendingCalls()

        // Change the head of the repo
        def javaFile = file('dep/src/main/java/Dep.java')
        javaFile.replace('class', 'interface')
        repo.commit('Changed Dep to an interface')

        // Finish up build
        block.releaseAll()
        build.waitForFinish()

        then:
        def gitCheckout = checkoutDir(repo.name, commit.id.name, repo.id)
        gitCheckout.file('.git').assertExists()

        and:
        def hashedRepo = hashRepositoryId(repo.id)
        file(".gradle/vcsWorkingDirs/${hashedRepo}-${commit.id.name}").assertIsDir()

        cleanup:
        server.stop()
    }

    def "external modifications to source dependency directories are reset"() {
        given:
        repo.file('foo').text = "bar"
        def commit = repo.commit('initial commit')

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

        when:
        succeeds('assemble')

        then:
        // Git repo is cloned
        def gitCheckout = checkoutDir(repo.name, commit.id.name, repo.id)
        gitCheckout.file('foo').text == "bar"

        when:
        gitCheckout.file('foo').text = "baz"
        succeeds('assemble', "-i")

        then:
        gitCheckout.file('foo').text == "bar"
    }

    def "external modifications to source dependency submodule directories are reset"() {
        given:
        // Populate submodule origin
        evenDeeperRepo.file('foo').text = "baz"
        evenDeeperRepo.commit('initial commit', "foo")
        deeperRepo.file('foo').text = "bar"
        deeperRepo.commit("initial commit", "foo")
        // Add submodule to repo
        deeperRepo.addSubmodule(evenDeeperRepo)
        repo.addSubmodule(deeperRepo)
        def commit = repo.commit('initial commit')

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

        when:
        succeeds('assemble')

        then:
        def gitCheckout = checkoutDir(repo.name, commit.id.name, repo.id)
        gitCheckout.file('deeperDep/foo').text == "bar"
        gitCheckout.file('deeperDep/evenDeeperDep/foo').text == "baz"

        when:
        gitCheckout.file('deeperDep/foo').text = "baz"
        gitCheckout.file('deeperDep/evenDeeperDep/foo').text == "buzz"
        succeeds('assemble')

        then:
        gitCheckout.file('deeperDep/foo').text == "bar"
        gitCheckout.file('deeperDep/evenDeeperDep/foo').text == "baz"
    }

    // TODO: Use HTTP hosting for git repo
}
