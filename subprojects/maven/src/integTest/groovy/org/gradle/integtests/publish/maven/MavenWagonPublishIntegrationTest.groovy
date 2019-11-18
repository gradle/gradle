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

package org.gradle.integtests.publish.maven

import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.integtests.fixtures.publish.maven.AbstractMavenPublishIntegTest

class MavenWagonPublishIntegrationTest extends AbstractMavenPublishIntegTest {

    @ToBeFixedForInstantExecution
    def "uses provided wagon type to perform publication"() {
        executer.expectDeprecationWarnings(2)

        given:
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'maven'
            version = '1.0'
            group = 'org.group.name'
            ${mavenCentralRepository()}
            configurations {
                deployerJars
            }
            dependencies {
                deployerJars "org.apache.maven.wagon:wagon-ssh:2.2"
            }

            uploadArchives {
                repositories.mavenDeployer {
                    addProtocolProviderJars configurations.deployerJars.files
                    repository(url: 'sftp://iamnotansshserverandidontexistandwontdealwithsftptransfers:22')
                }
            }
        """

        when:
        fails 'uploadArchives'

        then:
        failureCauseContains("Reason: java.net.UnknownHostException: iamnotansshserverandidontexistandwontdealwithsftptransfers")
        failure.assertHasErrorOutput("Reason: java.net.UnknownHostException: iamnotansshserverandidontexistandwontdealwithsftptransfers")
    }
}
