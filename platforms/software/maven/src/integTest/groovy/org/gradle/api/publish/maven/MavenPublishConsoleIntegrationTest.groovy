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

package org.gradle.api.publish.maven

import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.integtests.fixtures.RichConsoleStyling
import org.gradle.integtests.fixtures.publish.maven.AbstractMavenPublishIntegTest
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule

class MavenPublishConsoleIntegrationTest extends AbstractMavenPublishIntegTest {
    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    def setup() {
        server.start()
    }

    def "shows work-in-progress during publication"() {
        def m1 = mavenRepo.module("org.test", "test", "1.2")

        settingsFile << "rootProject.name = 'test'"
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'maven-publish'
            version = '1.2'
            group = 'org.test'

            publishing {
                repositories {
                    maven {
                        url = "${server.uri}"
                    }
                }
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
"""

        when:
        def putJar = server.expectAndBlock(server.put(m1.artifact.path))
        def putJarSha = server.expectAndBlock(server.put(m1.artifact.path + ".sha1"))
        server.expect(server.put(m1.artifact.path + ".md5"))
        server.expect(server.put(m1.artifact.path + ".sha256"))
        server.expect(server.put(m1.artifact.path + ".sha512"))
        def putPom = server.expectAndBlock(server.put(m1.pom.path))
        def putPomSha = server.expectAndBlock(server.put(m1.pom.path + ".sha1"))
        server.expect(server.put(m1.pom.path + ".md5"))
        server.expect(server.put(m1.pom.path + ".sha256"))
        server.expect(server.put(m1.pom.path + ".sha512"))
        def putModule = server.expectAndBlock(server.put(m1.moduleMetadata.path))
        server.expect(server.put(m1.moduleMetadata.path + ".sha1"))
        server.expect(server.put(m1.moduleMetadata.path + ".md5"))
        server.expect(server.put(m1.moduleMetadata.path + ".sha256"))
        server.expect(server.put(m1.moduleMetadata.path + ".sha512"))
        def getMetaData = server.expectAndBlock(server.get(m1.rootMetaData.path).missing())
        def putMetaData = server.expectAndBlock(server.put(m1.rootMetaData.path))
        server.expect(server.put(m1.rootMetaData.path + ".sha1"))
        server.expect(server.put(m1.rootMetaData.path + ".md5"))
        server.expect(server.put(m1.rootMetaData.path + ".sha256"))
        server.expect(server.put(m1.rootMetaData.path + ".sha512"))

        executer.withTestConsoleAttached()
        executer.withConsole(ConsoleOutput.Rich)
        def build = executer.withTasks("publish").withArguments("--max-workers=2").start()
        putJar.waitForAllPendingCalls()

        then:
        ConcurrentTestUtil.poll {
            RichConsoleStyling.assertHasWorkInProgress(build, "> :publishMavenPublicationToMavenRepository > test-1.2.jar")
        }

        when:
        putJar.releaseAll()
        putJarSha.waitForAllPendingCalls()

        then:
        ConcurrentTestUtil.poll {
            RichConsoleStyling.assertHasWorkInProgress(build, "> :publishMavenPublicationToMavenRepository > test-1.2.jar.sha1")
        }

        when:
        putJarSha.releaseAll()
        putPom.waitForAllPendingCalls()

        then:
        ConcurrentTestUtil.poll {
            RichConsoleStyling.assertHasWorkInProgress(build, "> :publishMavenPublicationToMavenRepository > test-1.2.pom")
        }

        when:
        putPom.releaseAll()
        putPomSha.waitForAllPendingCalls()

        then:
        ConcurrentTestUtil.poll {
            RichConsoleStyling.assertHasWorkInProgress(build, "> :publishMavenPublicationToMavenRepository > test-1.2.pom.sha1")
        }

        when:
        putPomSha.releaseAll()
        putModule.waitForAllPendingCalls()

        then:
        ConcurrentTestUtil.poll {
            RichConsoleStyling.assertHasWorkInProgress(build, "> :publishMavenPublicationToMavenRepository > test-1.2.module > 1.8 KiB/1.8 KiB uploaded")
        }

        when:
        putModule.releaseAll()
        getMetaData.waitForAllPendingCalls()

        then:
        ConcurrentTestUtil.poll {
            // TODO - where did this one go?
//            RichConsoleStyling.assertHasWorkInProgress(build, "> :publishMavenPublicationToMavenRepository > maven-metadata.xml")
        }

        when:
        getMetaData.releaseAll()
        putMetaData.waitForAllPendingCalls()

        then:
        ConcurrentTestUtil.poll {
            RichConsoleStyling.assertHasWorkInProgress(build, "> :publishMavenPublicationToMavenRepository > maven-metadata.xml")
        }

        putMetaData.releaseAll()
        build.waitForFinish()
    }
}
