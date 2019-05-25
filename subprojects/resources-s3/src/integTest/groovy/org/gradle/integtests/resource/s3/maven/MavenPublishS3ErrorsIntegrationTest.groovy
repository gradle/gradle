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

import org.gradle.integtests.fixtures.publish.maven.AbstractMavenPublishIntegTest
import org.gradle.integtests.resource.s3.fixtures.MavenS3Repository
import org.gradle.integtests.resource.s3.fixtures.S3Server
import org.junit.Rule

class MavenPublishS3ErrorsIntegrationTest extends AbstractMavenPublishIntegTest {

    String mavenVersion = "1.45"
    String projectName = "publishS3Test"
    String bucket = 'tests3bucket'
    String repositoryPath = '/maven/release/'

    @Rule
    public final S3Server server = new S3Server(temporaryFolder)

    def setup() {
        disableModuleMetadataPublishing()
        executer.withArgument('-i')
        executer.withArgument("-Dorg.gradle.s3.endpoint=${server.uri}")
    }

    def "should fail with an authentication error"() {
        setup:
        settingsFile << "rootProject.name = '${projectName}'"

        buildFile << """
    apply plugin: 'java'
    apply plugin: 'maven-publish'

    group = "org.gradle"
    version = '${mavenVersion}'

    publishing {
        repositories {
                maven {
                   url "${mavenS3Repo.uri}"
                    credentials(AwsCredentials) {
                        accessKey "someKey"
                        secretKey "someSecret"
                    }
                }
            }
        publications {
            pub(MavenPublication) {
                from components.java
            }
        }
    }
    """

        when:
        def module = mavenS3Repo.module("org.gradle", "publishS3Test", "1.45")
        module.artifact.expectPutAuthenticationError()

        then:
        fails 'publish'

        failure.assertHasDescription("Execution failed for task ':publishPubPublicationToMavenRepository'.")
        failure.assertHasCause("Failed to publish publication 'pub' to repository 'maven'")
        failure.assertHasCause("Could not write to resource '${module.artifact.uri}'.")
        failure.assertHasCause("The AWS Access Key Id you provided does not exist in our records.")
    }

    MavenS3Repository getMavenS3Repo() {
        new MavenS3Repository(server, file(getTestDirectory()), getRepositoryPath(), getBucket())
    }
}
