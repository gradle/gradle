/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.publish.maven.AbstractMavenPublishIntegTest
import org.gradle.internal.resource.ExternalResourceReadBuildOperationType
import org.gradle.internal.resource.ExternalResourceWriteBuildOperationType
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.MavenHttpRepository

class MavenPublishIgnoreIfAbsentIntegTest extends AbstractMavenPublishIntegTest {
    def server = new HttpServer()
    def buildOperations = new BuildOperationsFixture(executer, temporaryFolder)
    def repo = new MavenHttpRepository(server, mavenRepo)

    def "non-existing artifact causes failing while publishing"() {
        server.start()

        settingsFile << "rootProject.name = 'test'"
        buildFile << """
            apply plugin: 'maven-publish'
            version = '1.2'
            group = 'org.test'

            publishing {
                repositories {
                    maven {
                        url "${repo.uri}"
                    }
                }
                publications {
                    maven(MavenPublication) {
                        artifact(file('not_exist.txt'))
                    }
                }
            }
"""

        when:
        fails("publish")

        then:
        failureCauseContains("artifact file does not exist:")
    }

    def "skip non-existing artifacts if ignoreIfAbsent is true while publishing"() {
        server.start()

        settingsFile << "rootProject.name = 'test'"
        buildFile << """
            apply plugin: 'maven-publish'
            version = '1.2'
            group = 'org.test'

            publishing {
                repositories {
                    maven {
                        url "${repo.uri}"
                    }
                }
                publications {
                    maven(MavenPublication) {
                        artifact(file('not_exist.txt')) {
                            ignoreIfAbsent = true
                        }
                    }
                }
            }
"""

        when:
        def m1 = repo.module("org.test", "test", "1.2")
        m1.pom.expectPublish()
        m1.moduleMetadata.expectPublish()
        m1.rootMetaData.expectGetMissing()
        m1.rootMetaData.expectPublish()

        succeeds("publish")

        then:
        def writes = buildOperations.all(ExternalResourceWriteBuildOperationType)
        writes.size() == 2 * 3 // (pom, meta) * ( origin file, sha1, md5)
        writes[0].details.location == m1.pom.uri.toString()
        writes[0].result.bytesWritten == m1.pom.file.length()

        def reads = buildOperations.all(ExternalResourceReadBuildOperationType)
        reads.size() == 1
    }

    def "existing artifact can be published"() {
        server.start()

        settingsFile << "rootProject.name = 'test'"
        buildFile << """
            apply plugin: 'maven-publish'
            version = '1.2'
            group = 'org.test'

def f = file('exist.txt')
task buildSimpleFile() {
    doLast {
        f.createNewFile()
    }
}

            publishing {
                repositories {
                    maven {
                        url "${repo.uri}"
                    }
                }
                publications {
                    maven(MavenPublication) {
                        artifact(f) {
                            ignoreIfAbsent = true
                            builtBy buildSimpleFile
                        }
                    }
                }
            }
"""

        when:
        def m1 = repo.module("org.test", "test", "1.2")
        m1.artifact(type: 'txt').expectPublish()
        m1.pom.expectPublish()
        m1.moduleMetadata.expectPublish()
        m1.rootMetaData.expectGetMissing()
        m1.rootMetaData.expectPublish()

        succeeds("publish")

        then:
        def writes = buildOperations.all(ExternalResourceWriteBuildOperationType)
        writes.size() == 3 * 3 // (pom, txt, meta) * ( origin file, sha1, md5)

        writes.findAll {
            it.details.location == m1.pom.uri.toString() && it.result.bytesWritten == m1.pom.file.length()
        }.size() == 1

        writes.findAll {
            it.details.location == m1.artifact(type: 'txt').uri.toString() && it.result.bytesWritten == m1.artifact(type: 'txt').file.length()
        }.size() == 1

        def reads = buildOperations.all(ExternalResourceReadBuildOperationType)
        reads.size() == 1
    }
}
