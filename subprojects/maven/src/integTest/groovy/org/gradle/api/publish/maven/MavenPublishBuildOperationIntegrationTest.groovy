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

import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.publish.maven.AbstractMavenPublishIntegTest
import org.gradle.internal.resource.ExternalResourceReadBuildOperationType
import org.gradle.internal.resource.ExternalResourceWriteBuildOperationType
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.MavenHttpRepository

class MavenPublishBuildOperationIntegrationTest extends AbstractMavenPublishIntegTest {
    def server = new HttpServer()
    def buildOperations = new BuildOperationsFixture(executer, temporaryFolder)
    def repo = new MavenHttpRepository(server, mavenRepo)

    def "generates build operation events while publishing"() {
        server.start()

        settingsFile << "rootProject.name = 'test'"
        buildFile << """
            apply plugin: 'java'
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
                        from components.java
                    }
                }
            }
"""

        when:
        def m1 = repo.module("org.test", "test", "1.2")
        m1.artifact.expectPublish()
        m1.pom.expectPublish()
        m1.moduleMetadata.expectPublish()
        m1.rootMetaData.expectGetMissing()
        m1.rootMetaData.expectPublish()

        succeeds("publish")

        then:
        def writes1 = buildOperations.all(ExternalResourceWriteBuildOperationType)
        writes1.size() == 20
        writes1[0].details.location == m1.artifact.uri.toString()
        writes1[0].result.bytesWritten == m1.artifact.file.length()

        def reads1 = buildOperations.all(ExternalResourceReadBuildOperationType)
        reads1.size() == 1

        when:
        // Publish again
        buildFile << """
            version = '1.3'
"""

        def m2 = repo.module("org.test", "test", "1.3")
        m2.artifact.expectPublish()
        m2.pom.expectPublish()
        m2.moduleMetadata.expectPublish()
        m2.rootMetaData.expectGet()
        m2.rootMetaData.sha1.expectGet()
        m2.rootMetaData.md5.expectGet()
        m2.rootMetaData.expectPublish()

        succeeds("publish")

        then:
        def writes2 = buildOperations.all(ExternalResourceWriteBuildOperationType)
        writes2.size() == 20

        def reads2 = buildOperations.all(ExternalResourceReadBuildOperationType)
        reads2.size() == 1
    }

}
