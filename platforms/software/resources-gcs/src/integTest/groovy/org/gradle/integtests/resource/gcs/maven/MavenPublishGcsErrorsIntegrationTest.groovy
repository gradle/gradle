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

package org.gradle.integtests.resource.gcs.maven

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.publish.maven.AbstractMavenPublishIntegTest
import org.gradle.integtests.resource.gcs.fixtures.GcsServer
import org.gradle.integtests.resource.gcs.fixtures.MavenGcsRepository
import org.junit.Rule

import static org.gradle.internal.resource.transport.gcp.gcs.GcsConnectionProperties.GCS_DISABLE_AUTH_PROPERTY
import static org.gradle.internal.resource.transport.gcp.gcs.GcsConnectionProperties.GCS_ENDPOINT_PROPERTY
import static org.gradle.internal.resource.transport.gcp.gcs.GcsConnectionProperties.GCS_SERVICE_PATH_PROPERTY

class MavenPublishGcsErrorsIntegrationTest extends AbstractMavenPublishIntegTest {

    String mavenVersion = "1.45"
    String projectName = "publishGcsTest"
    String bucket = 'testGcsbucket'
    String repositoryPath = '/maven/release/'

    @Rule
    public final GcsServer server = new GcsServer(temporaryFolder)

    def setup() {
        executer.withArgument('-i')
        executer.withArgument("-D${GCS_ENDPOINT_PROPERTY}=${server.uri}")
        executer.withArgument("-D${GCS_SERVICE_PATH_PROPERTY}=/")
        executer.withArgument("-D${GCS_DISABLE_AUTH_PROPERTY}=true")
    }

    @ToBeFixedForConfigurationCache(skip = ToBeFixedForConfigurationCache.Skip.FAILS_TO_CLEANUP)
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
        module.artifact.expectPutAuthenticationError()

        then:
        fails 'publish'

        failure.assertHasDescription("Execution failed for task ':publishPubPublicationToMavenRepository'.")
        failure.assertHasCause("Failed to publish publication 'pub' to repository 'maven'")
        failure.assertHasCause("Could not write to resource '${module.artifact.uri}'.")
        failure.assertHasCause("403 Forbidden")
    }

    MavenGcsRepository getMavenGcsRepo() {
        new MavenGcsRepository(server, file(getTestDirectory()), getRepositoryPath(), getBucket())
    }
}
