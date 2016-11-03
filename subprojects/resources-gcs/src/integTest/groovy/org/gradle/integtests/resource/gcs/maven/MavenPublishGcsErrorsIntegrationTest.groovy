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


package org.gradle.integtests.resource.gcs.maven

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.resource.gcs.fixtures.GcsServer
import org.gradle.integtests.resource.gcs.fixtures.MavenGcsRepository
import org.junit.Rule
import spock.lang.Ignore

class MavenPublishGcsErrorsIntegrationTest extends AbstractIntegrationSpec {

    String mavenVersion = "1.45"
    String projectName = "publishGcsTest"
    String bucket = 'testGcsbucket'
    String repositoryPath = '/maven/release/'

    @Rule
    public final GcsServer server = new GcsServer(temporaryFolder)

    def setup() {
        executer.withArgument('-i')
        executer.withArgument("-Dorg.gradle.gcs.endpoint=${server.uri}")
    }

    @Ignore
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
                   url "${mavenGcsRepo.uri}"
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
        def module = mavenGcsRepo.module("org.gradle", "publishGcsTest", "1.45")
        module.artifact.expectPutAuthencicationError()
        module.pom.expectPutAuthencicationError()

        then:
        fails 'publish'

        failure.assertHasDescription("Execution failed for task ':publishPubPublicationToMavenRepository'.")
        failure.assertHasCause("Failed to publish publication 'pub' to repository 'maven'")
        failure.assertHasCause("Could not write to resource '${module.artifact.uri}'.")
        failure.assertHasCause("The AWS Access Key Id you provided dGcss not exist in our records.")
    }

    MavenGcsRepository getMavenGcsRepo() {
        new MavenGcsRepository(server, file(getTestDirectory()), getRepositoryPath(), getBucket())
    }
}
