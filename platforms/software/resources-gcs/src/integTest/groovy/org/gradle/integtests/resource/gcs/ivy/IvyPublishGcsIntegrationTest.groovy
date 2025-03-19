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

package org.gradle.integtests.resource.gcs.ivy

import org.gradle.api.publish.ivy.AbstractIvyPublishIntegTest
import org.gradle.integtests.resource.gcs.fixtures.GcsServer
import org.junit.Rule

import static org.gradle.internal.resource.transport.gcp.gcs.GcsConnectionProperties.GCS_DISABLE_AUTH_PROPERTY
import static org.gradle.internal.resource.transport.gcp.gcs.GcsConnectionProperties.GCS_ENDPOINT_PROPERTY
import static org.gradle.internal.resource.transport.gcp.gcs.GcsConnectionProperties.GCS_SERVICE_PATH_PROPERTY

class IvyPublishGcsIntegrationTest extends AbstractIvyPublishIntegTest {
    @Rule
    public GcsServer server = new GcsServer(temporaryFolder)

    def setup() {
        executer.withArgument("-D${GCS_ENDPOINT_PROPERTY}=${server.uri}")
        executer.withArgument("-D${GCS_SERVICE_PATH_PROPERTY}=/")
        executer.withArgument("-D${GCS_DISABLE_AUTH_PROPERTY}=true")
    }

    def "can publish to a Gcs Ivy repository"() {
        given:
        def ivyRepo = server.remoteIvyRepo

        settingsFile << 'rootProject.name = "publishGcsTest"'
        buildFile << """
apply plugin: 'java'
apply plugin: 'ivy-publish'

group = 'org.gradle.test'
version = '1.0'

publishing {
    repositories {
        ivy {
            url = "${ivyRepo.uri}"
        }
    }
    publications {
        ivy(IvyPublication) {
            from components.java
        }
    }
}
"""

        when:
        def module = ivyRepo.module('org.gradle.test', 'publishGcsTest', '1.0')
        module.jar.expectUpload()
        module.jar.sha1.expectUpload()
        module.jar.sha256.expectUpload()
        module.jar.sha512.expectUpload()
        module.ivy.expectUpload()
        module.ivy.sha1.expectUpload()
        module.ivy.sha256.expectUpload()
        module.ivy.sha512.expectUpload()
        module.moduleMetadata.expectUpload()
        module.moduleMetadata.sha1.expectUpload()
        module.moduleMetadata.sha256.expectUpload()
        module.moduleMetadata.sha512.expectUpload()

        succeeds 'publish'

        then:
        javaLibrary(module.backingModule).assertPublishedAsJavaModule()
    }
}
