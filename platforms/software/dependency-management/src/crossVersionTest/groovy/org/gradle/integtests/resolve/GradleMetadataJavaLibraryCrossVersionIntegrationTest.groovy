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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.CrossVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetVersions
import org.gradle.util.GradleVersion

import static org.gradle.integtests.fixtures.AbstractIntegrationSpec.mavenCentralRepository

@TargetVersions("5.2.1+")
class GradleMetadataJavaLibraryCrossVersionIntegrationTest extends CrossVersionIntegrationSpec {

    // The version in which Gradle metadata became "stable"
    private static final GradleVersion STABLE_METADATA_VERSION = GradleVersion.version("5.3")

    def setup() {
        settingsFile << """
            rootProject.name = 'test'
            if (org.gradle.util.GradleVersion.current().nextMajor == '6.0') {
                enableFeaturePreview('GRADLE_METADATA')
            }
            include 'consumer'
            include 'producer'
        """
        buildFile << """
            allprojects {
               apply plugin: 'java-library'

                group = 'com.acme'
                version = '1.0'

                repositories {
                    maven { url "\${rootProject.buildDir}/repo" }
                    ${mavenCentralRepository()}
                }
            }
        """

        file('producer/build.gradle') << """
            apply plugin: 'maven-publish'

            dependencies {
                constraints {
                    api 'org.apache.commons:commons-lang3:3.8.1'
                }
                implementation('org.apache.commons:commons-lang3') {
                    version {
                        strictly '[3.8, 3.9['
                        because "Doesn't work with other versions than 3.8"
                    }
                }
                implementation 'com.google.inject:guice:4.2.2:no_aop'
            }

            java {
                if (JavaPluginExtension.metaClass.respondsTo(delegate, 'registerFeature')) {
                    registerFeature("hibernateSupport") {
                        usingSourceSet(sourceSets.main)
                        capability("com.acme", "producer-hibernate-support", "1.0")
                    }
                }
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
                api 'com.acme:producer:1.0'
            }

            task resolve {
                dependsOn configurations.runtimeClasspath
                doLast {
                    assert configurations.runtimeClasspath.files*.name.contains("producer-1.0.jar")
                }
            }
        """
    }

    def "can consume library published with previous version of Gradle"() {
        expect:
        version(previous).withTasks(':producer:publish').run()
        version(current).withTasks(":consumer:resolve").run()
    }

    def "previous Gradle can consume library published with current version of Gradle"() {
        expect:
        version(current).withTasks(':producer:publish').run()
        if (previous.version >= STABLE_METADATA_VERSION) {
            version(previous).withTasks(':consumer:resolve').run()
        }
    }

}
