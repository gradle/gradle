/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.smoketests

import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.util.Requires

import static org.gradle.util.TestPrecondition.JDK12_OR_EARLIER
import static org.gradle.util.TestPrecondition.LINUX

// Works on MacOS, but let's test on linux only where we know docker is available
// Does not work on Java 13 as a dependency of the docker plugin (httpclient) is busted on 13
@Requires([LINUX, JDK12_OR_EARLIER])
class ArtifactoryAndDockerSmokeTest extends AbstractSmokeTest {

    @ToBeFixedForInstantExecution
    def 'artifactory with docker and plugin upload'() {
        when:
        buildFile << """
            plugins {
                id 'java-library'
                id 'maven-publish'
                id 'com.bmuschko.docker-remote-api' version '${TestedVersions.docker}'
                id 'com.jfrog.artifactory' version '${TestedVersions.artifactoryPlugin}'
            }

            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStopContainer
            import com.bmuschko.gradle.docker.tasks.container.extras.DockerLivenessContainer

            group = 'org.test'
            version = '0.0.1'

            task pullArtifactoryImage(type: DockerPullImage) {
                image = 'docker.bintray.io/jfrog/artifactory-oss:${TestedVersions.artifactoryRepoOSSVersion}'
            }


            task createArtifactory(type: DockerCreateContainer) {
                dependsOn pullArtifactoryImage
                targetImageId(pullArtifactoryImage.getImage())
                hostConfig.portBindings = ['8081:8081']
                hostConfig.autoRemove = true
            }

            task stopArtifactory(type: DockerStopContainer) {
                targetContainerId(createArtifactory.getContainerId())
            }

            task startArtifactory(type: DockerStartContainer) {
                dependsOn createArtifactory
                targetContainerId(createArtifactory.getContainerId())
                finalizedBy stopArtifactory
            }

            task awaitArtifactoryStart(type: DockerLivenessContainer) {
                dependsOn startArtifactory
                // So this is a first check, but not enough
                livenessProbe(60000, 5000, '### Artifactory successfully started')
                targetContainerId(createArtifactory.getContainerId())
                finalizedBy stopArtifactory
                doLast {
                    // Just need to wait till it tells us we are not authenticated
                    def pingUrl = new URL("http://localhost:8081/artifactory/api/system/ping")
                    def connection = pingUrl.openConnection()
                    while (connection.responseCode != 401) {
                        // retry
                        Thread.sleep(500)
                        connection = pingUrl.openConnection()
                    }
                }
            }

            publishing {
                publications {
                    mavenJava(MavenPublication) {
                        from components.java
                    }
                }
            }

            artifactory {
                contextUrl = 'http://localhost:8081/artifactory'
                publish {
                    repository {
                        repoKey = 'example-repo-local'
                        username = 'admin'
                        password = 'password'
                        maven = true

                    }
                    defaults {
                        publications('mavenJava')
                    }
                }
            }

            artifactoryDeploy.dependsOn awaitArtifactoryStart
            artifactoryDeploy.finalizedBy stopArtifactory
        """

        then:
        runner('artifactoryPublish').build()
    }
}
