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
import org.gradle.integtests.resource.gcs.fixtures.GcsArtifact
import org.gradle.integtests.resource.gcs.fixtures.GcsServer
import org.gradle.integtests.resource.gcs.fixtures.MavenGcsRepository
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.junit.Rule

import static org.gradle.internal.resource.transport.gcp.gcs.GcsConnectionProperties.GCS_DISABLE_AUTH_PROPERTY
import static org.gradle.internal.resource.transport.gcp.gcs.GcsConnectionProperties.GCS_ENDPOINT_PROPERTY
import static org.gradle.internal.resource.transport.gcp.gcs.GcsConnectionProperties.GCS_SERVICE_PATH_PROPERTY

@Requires(UnitTestPreconditions.NotWindows)
class MavenPublishGcsIntegrationTest extends AbstractMavenPublishIntegTest {
    @Rule
    public GcsServer server = new GcsServer(temporaryFolder)

    def setup() {
        executer.withArgument('-i')
        executer.withArgument("-D${GCS_ENDPOINT_PROPERTY}=${server.uri}")
        executer.withArgument("-D${GCS_SERVICE_PATH_PROPERTY}=/")
        executer.withArgument("-D${GCS_DISABLE_AUTH_PROPERTY}=true")
    }

    @ToBeFixedForConfigurationCache(skip = ToBeFixedForConfigurationCache.Skip.FAILS_TO_CLEANUP)
    def "can publish to a Gcs Maven repository"() {
        given:
        def mavenRepo = new MavenGcsRepository(server, file("repo"), "/maven", "testGcsBucket")
        settingsFile << 'rootProject.name = "publishGcsTest"'
        buildFile << """
apply plugin: 'java'
apply plugin: 'maven-publish'

group = 'org.gradle.test'
version = '1.0'

publishing {
    repositories {
        maven {
            url "${mavenRepo.uri}"
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
        def module = mavenRepo.module('org.gradle.test', 'publishGcsTest', '1.0').withModuleMetadata()
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

    private static void expectPublish(GcsArtifact artifact) {
        artifact.expectUpload()
        artifact.sha1.expectUpload()
        artifact.sha256.expectUpload()
        artifact.sha512.expectUpload()
        artifact.md5.expectUpload()
    }
}
