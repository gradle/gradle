/*
 * Copyright 2015 the original author or authors.
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
import spock.lang.Ignore

class IvyPublishGcsIntegrationTest extends AbstractIvyPublishIntegTest {
    @Rule
    public GcsServer server = new GcsServer(temporaryFolder)

    def setup() {
        executer.withArgument("-Dorg.gradle.gcs.endpoint=${server.getUri()}")
    }

    @Ignore
    def "can publish to an Gcs Ivy repository"() {
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
            url "${ivyRepo.uri}"
            credentials(AwsCredentials) {
                accessKey "someKey"
                secretKey "someSecret"
            }
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
        module.ivy.expectUpload()
        module.ivy.sha1.expectUpload()

        succeeds 'publish'

        then:
        module.assertPublishedAsJavaModule()
    }
}
