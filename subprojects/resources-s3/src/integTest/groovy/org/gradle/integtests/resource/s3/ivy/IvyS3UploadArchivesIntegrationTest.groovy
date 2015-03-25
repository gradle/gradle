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

package org.gradle.integtests.resource.s3.ivy

import org.gradle.integtests.ivy.AbstractIvyPublishIntegTest
import org.gradle.integtests.resource.s3.fixtures.S3FileBackedServer
import org.junit.Rule

class IvyS3UploadArchivesIntegrationTest extends AbstractIvyPublishIntegTest {

    String bucket = 'tests3bucket'

    @Rule
    public S3FileBackedServer server = new S3FileBackedServer(file())

    def setup() {
        executer.withArgument("-Dorg.gradle.s3.endpoint=${server.getUri()}")
    }

    def "can publish archives to ivy repository"() {
        given:
        def ivyRepo = ivy(server.getBackingDir(bucket))

        settingsFile << "rootProject.name = 'publishTest' "
        buildFile << """
apply plugin: 'java'

group = 'org.gradle.test'
version = '1.9'

repositories {
    mavenCentral()
}

dependencies {
    compile "commons-collections:commons-collections:3.2.1"
    runtime "commons-io:commons-io:1.4"
}

uploadArchives {
    repositories {
        ivy {
            url "${ivyRepo.uri}"
            credentials(AwsCredentials) {
                accessKey "someKey"
                secretKey "someSecret"
            }
        }
    }
}
"""

        when:
        run "uploadArchives"

        then:
        def ivyModule = ivyRepo.module("org.gradle.test", "publishTest", "1.9")
        ivyModule.assertPublished()
        ivyModule.assertArtifactsPublished("ivy-1.9.xml", "publishTest-1.9.jar")
        ivyModule.parsedIvy.expectArtifact(ivyModule.module, "jar").hasAttributes("jar", "jar", ["archives", "runtime"], null)

        with (ivyModule.parsedIvy) {
            dependencies.size() == 2
            dependencies["commons-collections:commons-collections:3.2.1"].hasConf("compile->default")
            dependencies["commons-io:commons-io:1.4"].hasConf("runtime->default")
        }
    }
}
