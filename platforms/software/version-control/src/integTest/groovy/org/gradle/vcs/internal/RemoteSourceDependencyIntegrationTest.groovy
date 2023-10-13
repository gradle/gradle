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
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.vcs.fixtures.GitHttpRepository
import org.junit.Rule

class RemoteSourceDependencyIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    BlockingHttpServer httpServer = new BlockingHttpServer()
    @Rule
    GitHttpRepository repoA = new GitHttpRepository(httpServer, 'testA', temporaryFolder.getTestDirectory())
    @Rule
    GitHttpRepository repoB = new GitHttpRepository(httpServer, 'testB', temporaryFolder.getTestDirectory())
    @Rule
    GitHttpRepository repoC = new GitHttpRepository(httpServer, 'testC', temporaryFolder.getTestDirectory())

    def setup() {
        httpServer.start()
        settingsFile << """
            rootProject.name = 'consumer'
            gradle.rootProject {
                allprojects {
                    configurations {
                        compile
                    }
                    tasks.register('resolve') {
                        inputs.files configurations.compile
                        doLast { configurations.compile.each { } }
                    }
                }
            }
            sourceControl.vcsMappings.withModule("test:testA") {
                from(GitVersionControlSpec) {
                    url = uri('${repoA.url}')
                }
            }
            sourceControl.vcsMappings.withModule("test:testB") {
                from(GitVersionControlSpec) {
                    url = uri('${repoB.url}')
                }
            }
        """

        def repoASettingsFile = repoA.file("settings.gradle")
        repoASettingsFile << """
            rootProject.name = 'testA'
            gradle.rootProject {
                def c = configurations.create('compile')
                def d = configurations.create('default')
                group = 'test'
                version = '1.2'
                def jar = tasks.create("jar", Jar) {
                    dependsOn c
                    archiveBaseName = "test"
                    destinationDirectory = buildDir
                    archiveVersion = project.version
                }
                d.outgoing.artifact(jar)
            }
            sourceControl.vcsMappings.withModule("test:testB") {
                from(GitVersionControlSpec) {
                    url = uri('${repoB.url}')
                }
            }
            sourceControl.vcsMappings.withModule("test:testC") {
                from(GitVersionControlSpec) {
                    url = uri('${repoC.url}')
                }
            }
        """
        repoA.commit('initial version')

        def repoBSettingsFile = repoB.file("settings.gradle")
        repoBSettingsFile << """
            rootProject.name = 'testB'
            gradle.rootProject {
                def c = configurations.create('compile')
                def d = configurations.create('default')
                group = 'test'
                version = '1.2'
                def jar = tasks.create("jar", Jar) {
                    dependsOn c
                    archiveBaseName = "test"
                    destinationDirectory = buildDir
                    archiveVersion = project.version
                }
                d.outgoing.artifact(jar)
            }
            sourceControl.vcsMappings.withModule("test:testC") {
                from(GitVersionControlSpec) {
                    url = uri('${repoC.url}')
                }
            }
        """
        repoB.commit('initial version')

        def repoCSettingsFile = repoC.file("settings.gradle")
        repoCSettingsFile << '''
            rootProject.name = 'testC'
            gradle.rootProject {
                def d = configurations.create('default')
                group = 'test'
                version = '1.2'
                def jar = tasks.create("jar", Jar) {
                    archiveBaseName = "test"
                    destinationDirectory = buildDir
                    archiveVersion = project.version
                }
                d.outgoing.artifact(jar)
            }
        '''
        repoC.commit('initial version')
    }

    @ToBeFixedForConfigurationCache
    def "git version lookup and checkout is performed once per version selector per build invocation"() {
        repoA.file("build.gradle") << """
            dependencies {
                compile 'test:testB:1.2'
                compile 'test:testC:1.2'
            }
        """
        repoA.commit('version 1.2')
        repoA.createLightWeightTag('1.2')
        repoB.file("build.gradle") << """
            dependencies {
                compile 'test:testC:1.2'
            }
        """
        repoB.commit('version 1.2')
        repoB.createLightWeightTag('1.2')
        repoC.commit('version 1.2')
        repoC.createLightWeightTag('1.2')
        settingsFile << """
            include 'a', 'b'
        """
        buildFile << """
            allprojects {
                dependencies {
                    compile 'test:testA:1.2'
                    compile 'test:testB:1.2'
                }
            }
        """

        when:
        repoA.expectListVersions()
        repoA.expectCloneSomething()
        repoB.expectListVersions()
        repoB.expectCloneSomething()
        repoC.expectListVersions()
        repoC.expectCloneSomething()

        then:
        succeeds('resolve')
        result.assertTasksExecuted(':resolve', ':a:resolve', ':b:resolve', ':testA:jar', ':testB:jar', ':testC:jar')

        when:
        repoA.expectListVersions()
        repoB.expectListVersions()
        repoC.expectListVersions()

        then:
        succeeds('resolve')
        result.assertTasksExecuted(':resolve', ':a:resolve', ':b:resolve', ':testA:jar', ':testB:jar', ':testC:jar')
    }

    @ToBeFixedForConfigurationCache
    def "git version lookup and checkout is performed once per branch selector per build invocation"() {
        repoA.file("build.gradle") << """
            dependencies {
                compile('test:testB') {
                    versionConstraint.branch = 'release'
                }
                compile('test:testC') {
                    versionConstraint.branch = 'release'
                }
            }
        """
        repoA.createBranch('release')
        repoA.checkout('release')
        repoA.commit('version 1.2')
        repoA.createLightWeightTag('1.2')
        repoB.file("build.gradle") << """
            dependencies {
                compile('test:testC') {
                    versionConstraint.branch = 'release'
                }
            }
        """
        repoB.createBranch('release')
        repoB.checkout('release')
        repoB.commit('version 1.2')
        repoB.createLightWeightTag('1.2')
        repoC.createBranch('release')
        repoC.checkout('release')
        repoC.commit('version 1.2')
        repoC.createLightWeightTag('1.2')
        settingsFile << """
            include 'a', 'b'
        """
        buildFile << """
            allprojects {
                dependencies {
                    compile('test:testA') {
                        versionConstraint.branch = 'release'
                    }
                    compile('test:testB') {
                        versionConstraint.branch = 'release'
                    }
                }
            }
        """

        when:
        repoA.expectListVersions()
        repoA.expectCloneSomething()
        repoB.expectListVersions()
        repoB.expectCloneSomething()
        repoC.expectListVersions()
        repoC.expectCloneSomething()

        then:
        succeeds('resolve')
        result.assertTasksExecuted(':resolve', ':a:resolve', ':b:resolve', ':testA:jar', ':testB:jar', ':testC:jar')

        when:
        repoA.expectListVersions()
        repoB.expectListVersions()
        repoC.expectListVersions()

        then:
        succeeds('resolve')
        result.assertTasksExecuted(':resolve', ':a:resolve', ':b:resolve', ':testA:jar', ':testB:jar', ':testC:jar')
    }
}
