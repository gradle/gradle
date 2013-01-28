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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class IvyPublishEarIntegTest extends AbstractIntegrationSpec {
    public void "can publish EAR only for mixed java and WAR and EAR project"() {
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

            repositories {
                mavenCentral()
            }

            dependencies {
                compile "commons-collections:commons-collections:3.2.1"
                runtime "commons-io:commons-io:1.4"
            }

            publishing {
                repositories {
                    ivy {
                        url '${ivyRepo.uri}'
                    }
                }
                publications {
                    ivyEar(IvyPublication) {
                        configurations {
                            runtime {
                                artifact ear
                            }
                            "default" {
                                extend configurations.runtime
                            }
                        }
                    }
                }
            }
        """

        when:
        run "publish"

        then: "module is published with artifacts"
        def ivyModule = ivyRepo.module("org.gradle.test", "publishEar", "1.9")
        ivyModule.assertPublished()
        ivyModule.assertArtifactsPublished("ivy-1.9.xml", "publishEar-1.9.ear")

        and: "correct configurations declared"
        ivyModule.ivy.configurations.keySet() == ["default", "runtime"] as Set
        ivyModule.ivy.configurations.runtime.extend == [] as Set
        ivyModule.ivy.configurations.default.extend == ["runtime"] as Set

        and: "artifact correctly declared"
        with (ivyModule.ivy.artifacts.publishEar) {
            type == "ear"
            ext == "ear"
            conf == ["runtime"]
        }

        and: "no dependencies declared"
        ivyModule.ivy.dependencies.isEmpty()
    }
}
