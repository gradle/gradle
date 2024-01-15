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
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.vcs.fixtures.GitHttpRepository
import org.junit.Rule

abstract class AbstractSourceDependencyMultiprojectIntegrationTest extends AbstractIntegrationSpec implements SourceDependencies {
    @Rule
    BlockingHttpServer httpServer = new BlockingHttpServer()
    @Rule
    GitHttpRepository repo = new GitHttpRepository(httpServer, "repo", testDirectory)
    @Rule
    GitHttpRepository repo2 = new GitHttpRepository(httpServer, "repo2", testDirectory)
    BuildTestFile buildB

    void mappingFor(GitHttpRepository gitRepo, String coords) {
        mappingFor(gitRepo.url.toString(), coords, "")
    }

    abstract void mappingFor(String gitRepo, String coords, String repoDef = "")

    def setup() {
        httpServer.start()
        buildB = new BuildTestFile(repo.workTree, "B")
        buildB.createDirs("foo", "bar")
        // git doesn't track directories so we need to create files in them
        buildB.file("foo/.gitkeepdir").touch()
        buildB.file("bar/.gitkeepdir").touch()
        buildB.settingsFile << """
            rootProject.name = 'B'
            include 'foo', 'bar'
        """
        buildB.buildFile << """
            allprojects {
                apply plugin: 'java'
                group = 'org.test'
                version = '1.0'
            }
        """
        repo.commit('initial')
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

    @ToBeFixedForConfigurationCache
    def "can resolve subproject of multi-project source dependency"() {
        mappingFor(repo, "org.test:foo")
        buildFile << """
            dependencies {
                conf 'org.test:foo:latest.integration'
            }
        """
        expect:
        repo.expectListVersions()
        repo.expectCloneSomething()
        assertResolvesTo("foo-1.0.jar")

        repo.expectListVersions()
        assertResolvesTo("foo-1.0.jar")
    }

    @ToBeFixedForConfigurationCache
    def "can resolve root of multi-project source dependency"() {
        mappingFor(repo, "org.test:B")
        buildFile << """
            dependencies {
                conf 'org.test:B:latest.integration'
            }
        """
        expect:
        repo.expectListVersions()
        repo.expectCloneSomething()
        assertResolvesTo("B-1.0.jar")

        repo.expectListVersions()
        assertResolvesTo("B-1.0.jar")
    }

    @ToBeFixedForConfigurationCache
    def "can resolve multiple projects of multi-project source dependency"() {
        mappingFor(repo, "org.test:foo")
        mappingFor(repo, "org.test:bar")
        buildFile << """
            dependencies {
                conf 'org.test:foo:latest.integration'
                conf 'org.test:bar:latest.integration'
            }
        """
        expect:
        repo.expectListVersions()
        repo.expectCloneSomething()
        assertResolvesTo("foo-1.0.jar", "bar-1.0.jar")

        repo.expectListVersions()
        assertResolvesTo("foo-1.0.jar", "bar-1.0.jar")
    }

    @ToBeFixedForConfigurationCache
    def "only resolves a single project of multi-project source dependency"() {
        mavenRepo.module("org.test", "bar", "1.0-SNAPSHOT").withNonUniqueSnapshots().publish()
        mappingFor(repo, "org.test:foo")
        buildFile << """
            dependencies {
                conf 'org.test:foo:latest.integration'
                conf 'org.test:bar:latest.integration'
            }
        """
        expect:
        // foo should be from the source dependencies and bar should be from the external repo
        repo.expectListVersions()
        repo.expectCloneSomething()
        assertResolvesTo("foo-1.0.jar", "bar-1.0-SNAPSHOT.jar")

        repo.expectListVersions()
        assertResolvesTo("foo-1.0.jar", "bar-1.0-SNAPSHOT.jar")
    }

    @ToBeFixedForConfigurationCache
    def "uses included build subproject of multi-project source dependency"() {
        mavenRepo.module("org.test", "bar", "1.0-SNAPSHOT").withNonUniqueSnapshots().publish()
        buildB.buildFile << """
            project(":foo") {
                dependencies {
                    implementation project(":bar")
                }
            }
        """
        repo.commit('updated')
        mappingFor(repo, "org.test:foo")
        buildFile << """
            dependencies {
                conf 'org.test:foo:latest.integration'
                conf 'org.test:bar:latest.integration'
            }
        """
        expect:
        repo.expectListVersions()
        repo.expectCloneSomething()
        assertResolvesTo("foo-1.0.jar", "bar-1.0.jar")

        repo.expectListVersions()
        assertResolvesTo("foo-1.0.jar", "bar-1.0.jar")
    }

    @ToBeFixedForConfigurationCache
    def "uses root mapping for duplicate subproject of multi-project source dependency"() {
        buildB.buildFile << """
            project(":foo") {
                dependencies {
                    implementation project(":bar")
                }
            }
        """
        repo.commit("update")
        repo2.file("settings.gradle") << """
            rootProject.name = "bar"
        """
        repo2.file("build.gradle") << """
            apply plugin: 'java'
            group = "org.test"
            version = "2.0"
        """
        repo2.commit("initial")

        mappingFor(repo, "org.test:foo")
        mappingFor(repo2, "org.test:bar")
        buildFile << """
            dependencies {
                conf 'org.test:foo:latest.integration'
                conf 'org.test:bar:latest.integration'
            }
        """
        expect:
        repo.expectListVersions()
        repo.expectCloneSomething()
        repo2.expectListVersions()
        repo2.expectCloneSomething()
        assertResolvesTo("foo-1.0.jar", "bar-2.0.jar")

        repo.expectListVersions()
        repo2.expectListVersions()
        assertResolvesTo("foo-1.0.jar", "bar-2.0.jar")
    }

    def "reasonable error when VCS mapping does not match underlying build"() {
        mavenRepo.module("org.test", "bar", "1.0-SNAPSHOT").withNonUniqueSnapshots().publish()
        buildB.buildFile << """
            allprojects {
                group = "new.group"
            }
        """
        repo.commit("updated")
        mappingFor(repo, "org.test:foo")
        buildFile << """
            dependencies {
                conf 'org.test:foo:latest.integration'
            }
        """
        expect:
        repo.expectListVersions()
        repo.expectCloneSomething()
        fails("resolve")
        failure.assertHasDescription("Could not determine the dependencies of task ':resolve'.")
        failure.assertHasCause("Could not resolve all task dependencies for configuration ':conf'.")
        failure.assertHasCause("Git repository at ${repo.url} did not contain a project publishing the specified dependency.")
    }

    void assertResolvesTo(String... files) {
        def result = "-Presult=" + files.join(',')
        succeeds("resolve", result)
    }
}
