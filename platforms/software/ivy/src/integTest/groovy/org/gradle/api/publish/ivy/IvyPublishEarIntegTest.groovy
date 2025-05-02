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

package org.gradle.api.publish.ivy

class IvyPublishEarIntegTest extends AbstractIvyPublishIntegTest {
    void "can publish EAR only for mixed java and WAR and EAR project"() {
        given:
        file("settings.gradle") << "rootProject.name = 'publishEar' "

        and:
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'war'
            apply plugin: 'ear'
            apply plugin: 'ivy-publish'

            group = 'org.gradle.test'
            version = '1.9'

            ${mavenCentralRepository()}

            dependencies {
                implementation "commons-collections:commons-collections:3.2.2"
                runtimeOnly "commons-io:commons-io:1.4"
            }

            publishing {
                repositories {
                    ivy {
                        url = "${ivyRepo.uri}"
                    }
                }
                publications {
                    ivyEar(IvyPublication) {
                        configurations {
                            master {}
                            "default" {
                                extend "master"
                            }
                        }
                        artifact source: ear, conf: "master"
                    }
                }
            }
        """

        when:
        run "publish"

        then: "module is published with artifacts"
        def ivyModule = ivyRepo.module("org.gradle.test", "publishEar", "1.9")
        ivyModule.assertPublishedAsEarModule()

        and: "correct configurations and dependencies declared"
        with(ivyModule.parsedIvy) {
            configurations.keySet() == ["default", "master"] as Set
            configurations.default.extend == ["master"] as Set
            configurations.master.extend == null

            dependencies.isEmpty()
        }

        and: "can resolve ear module"
        resolveArtifacts(ivyModule) {
            withoutModuleMetadata {
                expectFiles "publishEar-1.9.ear"
            }
            withModuleMetadata {
                noComponentPublished()
            }
        }
    }
}
