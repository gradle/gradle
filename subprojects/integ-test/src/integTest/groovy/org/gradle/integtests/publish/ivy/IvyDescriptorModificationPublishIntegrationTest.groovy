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

package org.gradle.integtests.publish.ivy

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import groovy.util.slurpersupport.GPathResult

class IvyDescriptorModificationPublishIntegrationTest extends AbstractIntegrationSpec {

    def module = ivyRepo.module("org.gradle", "publish", "2")

    def setup() {
        settingsFile << """
            rootProject.name = "${module.module}"
        """
        buildFile << """
            apply plugin: 'java'
            version = '${module.revision}'
            group = '${module.organisation}'
            uploadArchives {
                repositories {
                    ivy {
                        url "${ivyRepo.uri}"
                    }
                }
            }
        """
    }

    def "can modify descriptor during publication"() {
        when:
        succeeds 'uploadArchives'

        then:
        asXml(module.ivyFile).info[0].@revision == "2"

        when:
        buildFile << """
            uploadArchives {
                setDescriptorModification {
                    it.asNode().info[0].@revision = "3"
                }
            }
        """
        succeeds 'uploadArchives'

        then:
        asXml(module.ivyFile).info[0].@revision == "3"
    }

    GPathResult asXml(File file) {
        new XmlSlurper().parse(file)
    }
}
