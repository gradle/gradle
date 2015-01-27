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


package org.gradle.api.publish.maven

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.resource.transport.aws.s3.S3ConnectionProperties
import org.gradle.test.fixtures.server.s3.S3StubServer
import org.gradle.test.fixtures.server.s3.S3StubSupport
import org.junit.Rule

import static org.hamcrest.Matchers.startsWith

class MavenPublishS3ErrorsIntegrationTest extends AbstractIntegrationSpec {

    String mavenVersion = "1.45"
    String projectName = "publishS3Test"
    String bucket = 'tests3bucket'
    String repositoryPath = '/maven/release/'


    @Rule
    public final S3StubServer server = new S3StubServer()
    final S3StubSupport s3StubSupport = new S3StubSupport(server)

    def setup() {
        executer.withArgument('-i')
        executer.withArgument("-D${S3ConnectionProperties.S3_ENDPOINT_PROPERTY}=${s3StubSupport.endpoint.toString()}")
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
                   url "s3://${bucket}${repositoryPath}"
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
        s3StubSupport.stubPutFileAuthFailure("/${getBucket()}/maven/release/org/gradle/publishS3Test/1.45/publishS3Test-1.45.jar")

        then:
        fails 'publish'

        failure.assertHasDescription("Execution failed for task ':publishPubPublicationToMavenRepository'.")
        failure.assertThatCause(startsWith("Failed to publish publication 'pub' to repository 'maven'"))
                .assertHasCause("Could not put s3 resource: [s3://tests3bucket/maven/release/org/gradle/publishS3Test/1.45/publishS3Test-1.45.jar]. " +
                "The AWS Access Key Id you provided does not exist in our records.")
    }
}
