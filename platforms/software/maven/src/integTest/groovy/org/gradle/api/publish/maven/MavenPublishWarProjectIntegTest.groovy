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

import org.gradle.integtests.fixtures.publish.maven.AbstractMavenPublishIntegTest

class MavenPublishWarProjectIntegTest extends AbstractMavenPublishIntegTest {
    void "publishes war and meta-data for web component with external dependencies"() {
        def webModule = mavenRepo.module("org.gradle.test", "project1", "1.9").withModuleMetadata()

        given:
        settingsFile << "rootProject.name = 'project1'"

        and:
        buildFile << """
            apply plugin: 'war'
            apply plugin: 'maven-publish'

            group = 'org.gradle.test'
            version = '1.9'

            ${mavenCentralRepository()}

            dependencies {
                implementation "commons-collections:commons-collections:3.2.2"
                runtimeOnly "commons-io:commons-io:1.4"
                testRuntimeOnly "junit:junit:4.13"
            }

            publishing {
                repositories {
                    maven { url = "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        from components.web
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
        webModule.parsedModuleMetadata.variant("master").dependencies.isEmpty()

        and:
        resolveArtifacts(webModule) { expectFiles "project1-1.9.war" }
    }

    void "publishes war and meta-data for web component with project dependencies"() {
        given:
        createDirs("projectWeb", "depProject1", "depProject2")
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
                    maven { url = "${mavenRepo.uri}" }
                }
            }
        }

        project(":projectWeb") {
            dependencies {
                implementation project(":depProject1")
                implementation project(":depProject2")
            }

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.web
                    }
                }
            }
         }

        project(":depProject1") {
            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
        }

        project(":depProject2") {
            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.web
                    }
                }
            }
        }
"""

        when:
        run "publish"

        then:
        javaLibrary(mavenRepo.module("org.gradle.test", "depProject1", "1.9")).assertPublished()
        mavenRepo.module("org.gradle.test", "depProject2", "1.9").assertPublished()

        def webModule = mavenRepo.module("org.gradle.test", "projectWeb", "1.9").withModuleMetadata()
        webModule.assertPublishedAsWebModule()
        webModule.parsedPom.scopes.isEmpty()
        webModule.parsedModuleMetadata.variant("master").dependencies.isEmpty()

        and:
        resolveArtifacts(webModule) { expectFiles "projectWeb-1.9.war" }
    }

}
