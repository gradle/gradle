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

import groovy.util.slurpersupport.GPathResult
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class IvyPublishingIntegTest extends AbstractIntegrationSpec {

    def module = ivyRepo.module("org.gradle", "publish", "2")

    def setup() {
        settingsFile << """
            rootProject.name = "${module.module}"
        """

        buildFile << """
            apply plugin: 'java'
            apply plugin: 'ivy-publish'

            version = '${module.revision}'
            group = '${module.organisation}'

            publishing {
                repositories {
                    ivy { url "${ivyRepo.uri}" }
                }
            }

            task publishIvy(type: IvyPublish) {
                publication publishing.publications.ivy
                repository publishing.repositories.ivy
            }
        """
    }

    def "can modify descriptor during publication"() {
        when:
        succeeds 'publishIvy'

        then:
        ":jar" in executedTasks

        and:
        asXml(module.ivyFile).info[0].@revision == "2"

        when:
        buildFile << """
            publishing {
                publications {
                    ivy {
                        ivy.withXml {
                            it.asNode().info[0].@revision = "3"
                        }
                    }
                }
            }
        """
        succeeds 'publishIvy'


        then:
        ":jar" in skippedTasks

        and:
        asXml(module.ivyFile).info[0].@revision == "3"
    }

    GPathResult asXml(File file) {
        new XmlSlurper().parse(file)
    }
}
