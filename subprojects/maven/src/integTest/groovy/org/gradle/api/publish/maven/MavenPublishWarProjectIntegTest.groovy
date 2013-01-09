/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.publish.maven

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class MavenPublishWarProjectIntegTest extends AbstractIntegrationSpec {
    public void "publishes war and meta-data for web component with external dependencies"() {
        def webModule = mavenRepo.module("org.gradle.test", "project1", "1.9")

        given:
        settingsFile << "rootProject.name = 'project1'"

        and:
        buildFile << """
            apply plugin: 'war'
            apply plugin: 'maven-publish'

            group = 'org.gradle.test'
            version = '1.9'

            repositories {
                mavenCentral()
            }

            dependencies {
                compile "commons-collections:commons-collections:3.2.1"
                runtime "commons-io:commons-io:1.4"
                testRuntime "junit:junit:4.11"
            }

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                publications {
                    add('mavenWeb', org.gradle.api.publish.maven.MavenPublication) {
                        from components['web']
                    }
                }
            }
"""
        when:
        succeeds 'assemble'

        then: "war is built but not published"
        webModule.assertNotPublished()
        file('build/libs/project1-1.9.war').assertExists()

        when:
        run "publish"

        then:
        webModule.assertPublishedAsWebModule()
        webModule.parsedPom.scopes.isEmpty()
    }

    public void "publishes war and meta-data for web component with project dependencies"() {
        given:
        settingsFile << "include 'projectWeb', 'depProject1', 'depProject2'"

        and:
        buildFile << """
        subprojects {
            apply plugin: 'war'
            apply plugin: 'maven-publish'

            group = 'org.gradle.test'
            version = '1.9'

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
            }
        }

        project(":projectWeb") {
            dependencies {
                compile project(":depProject1")
                compile project(":depProject2")
            }

            publishing {
                publications {
                    add('mavenWeb', org.gradle.api.publish.maven.MavenPublication) {
                        from components['web']
                    }
                }
            }
         }

        project(":depProject1") {
            publishing {
                publications {
                    add('mavenJava', org.gradle.api.publish.maven.MavenPublication) {
                        from components['java']
                    }
                }
            }
        }

        project(":depProject2") {
            publishing {
                publications {
                    add('mavenJava', org.gradle.api.publish.maven.MavenPublication) {
                        from components['web']
                    }
                }
            }
        }
"""

        when:
        run "publish"

        then:
        mavenRepo.module("org.gradle.test", "depProject1", "1.9").assertPublishedAsJavaModule()
        mavenRepo.module("org.gradle.test", "depProject2", "1.9").assertPublishedAsWebModule()

        def webModule = mavenRepo.module("org.gradle.test", "projectWeb", "1.9")
        webModule.assertPublishedAsWebModule()

        webModule.parsedPom.scopes.isEmpty()
    }

}
