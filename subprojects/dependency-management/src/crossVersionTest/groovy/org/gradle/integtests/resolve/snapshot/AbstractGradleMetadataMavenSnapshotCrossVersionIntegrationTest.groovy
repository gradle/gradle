/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.resolve.snapshot

import org.gradle.integtests.fixtures.CrossVersionIntegrationSpec

import static org.gradle.integtests.fixtures.AbstractIntegrationSpec.mavenCentralRepository

abstract class AbstractGradleMetadataMavenSnapshotCrossVersionIntegrationTest extends CrossVersionIntegrationSpec {
    def setup() {
        settingsFile << """
            rootProject.name = 'test'
            include 'consumer'
            include 'producer'
        """
        buildFile << """
            allprojects {
               apply plugin: 'java-library'

                group = 'com.maven.snapshot'
                version = '1.0-SNAPSHOT'

                repositories {
                    maven { url "\${rootProject.buildDir}/repo" }
                }
                ${mavenCentralRepository()}
            }
        """

        file('producer/build.gradle') << """
            apply plugin: 'maven-publish'

            dependencies {
                implementation 'org.apache.commons:commons-lang3:3.8.1'
            }

            publishing {
                repositories {
                    maven { url "\${rootProject.buildDir}/repo" }
                }

                publications {
                    producerLib(MavenPublication) {
                        from components.java
                    }
                }
            }
        """

        file('consumer/build.gradle') << """
            dependencies {
                implementation 'com.maven.snapshot:producer:1.0-SNAPSHOT'
            }

            task resolve {
                doLast {
                    println configurations.runtimeClasspath.files
                }
            }
        """
    }

}
