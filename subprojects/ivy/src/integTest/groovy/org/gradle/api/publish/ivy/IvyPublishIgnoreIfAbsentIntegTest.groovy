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

package org.gradle.api.publish.ivy

import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.resource.ExternalResourceReadBuildOperationType
import org.gradle.internal.resource.ExternalResourceWriteBuildOperationType
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.IvyHttpModule
import org.gradle.test.fixtures.server.http.IvyHttpRepository

class IvyPublishIgnoreIfAbsentIntegTest extends AbstractIvyPublishIntegTest {
    def server = new HttpServer()
    def buildOperations = new BuildOperationsFixture(executer, temporaryFolder)
    def repo = new IvyHttpRepository(server, ivyRepo)

    def "non-existing artifact causes failing while publishing"() {
        server.start()

        settingsFile << "rootProject.name = 'test'"
        buildFile << """
            apply plugin: 'ivy-publish'
            version = '1.2'
            group = 'org.test'

            publishing {
                repositories {
                    ivy {
                        url "${repo.uri}"
                    }
                }
                publications {
                    ivy(IvyPublication) {
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
            apply plugin: 'ivy-publish'
            version = '1.2'
            group = 'org.test'

            publishing {
                repositories {
                    ivy {
                        url "${repo.uri}"
                    }
                }
                publications {
                    ivy(IvyPublication) {
                        artifact(file('not_exist.txt')) {
                            ignoreIfAbsent = true
                        }
                    }
                }
            }
"""

        when:
        def m1 = repo.module("org.test", "test", "1.2")
        m1.ivy.expectPublish()
        m1.moduleMetadata.expectPublish()

        succeeds("publish")

        then:
        def writes = buildOperations.all(ExternalResourceWriteBuildOperationType)
        writes.size() == 1 * 2 // (ivy) * (origin file, sha1)
        writes[0].details.location == m1.ivy.uri.toString()
        writes[0].result.bytesWritten == m1.ivy.file.length()

        def reads = buildOperations.all(ExternalResourceReadBuildOperationType)
        reads.size() == 0
    }

    def "existing artifact can be published"() {
        server.start()

        settingsFile << "rootProject.name = 'test'"
        buildFile << """
            apply plugin: 'ivy-publish'
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
                    ivy {
                        url "${repo.uri}"
                    }
                }
                publications {
                    ivy(IvyPublication) {
                        artifact(f) {
                            ignoreIfAbsent = true
                            builtBy buildSimpleFile
                        }
                    }
                }
            }
"""

        when:
        IvyHttpModule m1 = repo.module("org.test", "test", "1.2")
        m1.getArtifact(ext: 'txt').expectPublish()
        m1.ivy.expectPublish()

        succeeds("publish")

        then:
        def writes = buildOperations.all(ExternalResourceWriteBuildOperationType)
        writes.size() == 2 * 2 // (ivy, file) * (origin file, sha1)

        writes.findAll {
            it.details.location == m1.ivy.uri.toString() && it.result.bytesWritten == m1.ivy.file.length()
        }.size() == 1

        writes.findAll {
            it.details.location == m1.getArtifact(type: 'txt').uri.toString() && it.result.bytesWritten == m1.getArtifact(type: 'txt').file.length()
        }.size() == 1

        def reads = buildOperations.all(ExternalResourceReadBuildOperationType)
        reads.size() == 0
    }
}
