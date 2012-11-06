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

class IvyCustomPublishIntegrationTest extends AbstractIntegrationSpec {

    public void "can publish custom configurations"() {
        given:
        def module = ivyRepo.module("org.gradle", "publish", "2")
        def artifact = file("artifact.txt").createFile()

        settingsFile << 'rootProject.name = "publish"'

        buildFile << """
            apply plugin: 'java'
            apply plugin: 'ivy-publish'

            version = '2'
            group = 'org.gradle'

            configurations { custom }
            artifacts {
                custom file("${artifact.name}"), {
                    name "foo"
                    extension "txt"
                }
            }

            publishing {
                repositories {
                    ivy {
                        url "${ivyRepo.uri}"
                    }
                }
            }
        """

        when:
        succeeds 'publishIvyPublicationToIvyRepository'

        then:
        module.ivyFile.assertIsFile()
        module.assertArtifactsPublished("ivy-2.xml", "foo-2.txt", "publish-2.jar")
        with(module.ivy.artifacts.foo) {
            name == "foo"
            ext == "txt"
            "custom" in conf
        }
    }

}
