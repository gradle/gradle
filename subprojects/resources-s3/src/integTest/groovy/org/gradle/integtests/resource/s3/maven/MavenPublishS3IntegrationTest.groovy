/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.integtests.resource.s3.maven

import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.integtests.fixtures.publish.maven.AbstractMavenPublishIntegTest
import org.gradle.integtests.resource.s3.fixtures.MavenS3Repository
import org.gradle.integtests.resource.s3.fixtures.S3Artifact
import org.gradle.integtests.resource.s3.fixtures.S3IntegrationTestPrecondition
import org.gradle.integtests.resource.s3.fixtures.S3Server
import org.junit.Rule
import spock.lang.Requires

@Requires({ S3IntegrationTestPrecondition.fulfilled })
class MavenPublishS3IntegrationTest extends AbstractMavenPublishIntegTest {
    @Rule
    public S3Server server = new S3Server(temporaryFolder)

    def setup() {
        executer.withArgument("-Dorg.gradle.s3.endpoint=${server.getUri()}")
        .withArgument("-Daws.accessKeyId=someKey")
        .withArgument("-Daws.secretKey=someSecret");
    }

    @ToBeFixedForInstantExecution
    def "can publish to a S3 Maven repository"() {
        given:
        def mavenRepo = new MavenS3Repository(server, file("repo"), "/maven", "tests3Bucket")
        settingsFile << 'rootProject.name = "publishS3Test"'
        buildFile << """
apply plugin: 'java'
apply plugin: 'maven-publish'

group = 'org.gradle.test'
version = '1.0'

publishing {
    repositories {
        maven {
            url "${mavenRepo.uri}"
            credentials(AwsCredentials) {
                accessKey "someKey"
                secretKey "someSecret"
            }
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
        def module = mavenRepo.module('org.gradle.test', 'publishS3Test', '1.0').withModuleMetadata()
        expectPublish(module.artifact)
        expectPublish(module.pom)
        expectPublish(module.moduleMetadata)
        module.rootMetaData.expectDownloadMissing()
        expectPublish(module.rootMetaData)

        succeeds 'publish'

        then:
        module.assertPublishedAsJavaModule()
        module.parsedPom.scopes.isEmpty()
    }


    @ToBeFixedForInstantExecution
    def "can publish to a S3 Maven repository with IAM"() {
        given:
        def mavenRepo = new MavenS3Repository(server, file("repo"), "/maven", "tests3Bucket")
        settingsFile << 'rootProject.name = "publishS3Test"'
        buildFile << """
apply plugin: 'java'
apply plugin: 'maven-publish'

group = 'org.gradle.test'
version = '1.0'

publishing {
    repositories {
        maven {
            url "${mavenRepo.uri}"
            authentication {
               awsIm(AwsImAuthentication)
            }
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
        def module = mavenRepo.module('org.gradle.test', 'publishS3Test', '1.0').withModuleMetadata()
        expectPublish(module.artifact)
        expectPublish(module.pom)
        expectPublish(module.moduleMetadata)
        module.rootMetaData.expectDownloadMissing()
        expectPublish(module.rootMetaData)

        succeeds 'publish'

        then:
        module.assertPublishedAsJavaModule()
        module.parsedPom.scopes.isEmpty()
    }

    private static void expectPublish(S3Artifact artifact) {
        artifact.expectUpload()
        artifact.sha1.expectUpload()
        artifact.sha256.expectUpload()
        artifact.sha512.expectUpload()
        artifact.md5.expectUpload()
    }
}
