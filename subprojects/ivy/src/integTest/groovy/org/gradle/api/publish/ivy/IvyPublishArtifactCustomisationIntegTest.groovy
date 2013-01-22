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
import spock.lang.Ignore

class IvyPublishArtifactCustomisationIntegTest extends AbstractIntegrationSpec {

    def module = ivyRepo.module("org.gradle.test", "publish", "2")

    def setup() {
        file('artifact.txt') << "some content"
        settingsFile << 'rootProject.name = "publish"'
    }

    public void "can publish custom artifact"() {
        given:
        buildFile << """
            apply plugin: 'ivy-publish'

            version = '2'
            group = 'org.gradle.test'

            publishing {
                repositories {
                    ivy {
                        url "${ivyRepo.uri}"
                    }
                }
                publications {
                    ivy(IvyPublication) {
                        artifact file: "artifact.txt", name: "foo", extension: "txt"
                    }
                }
            }
        """

        when:
        succeeds 'publish'

        then:
        module.ivyFile.assertIsFile()
        module.assertArtifactsPublished("ivy-2.xml", "foo-2.txt")
        with(module.ivy.artifacts.foo) {
            name == "foo"
            ext == "txt"
            "runtime" in conf
        }
    }

    def "publish multiple artifacts in single configuration"() {
        file("file1") << "some content"
        file("file2") << "other content"

        buildFile << """
            apply plugin: "base"
            apply plugin: "ivy-publish"

            group = "org.gradle.test"
            version = 2

            task jar1(type: Jar) {
                baseName = "jar1"
                from "file1"
            }

            task jar2(type: Jar) {
                baseName = "jar2"
                from "file2"
            }

            publishing {
                repositories {
                    ivy {
                        url "${ivyRepo.uri}"
                    }
                }
                publications {
                    ivy(IvyPublication) {
                        artifact jar1
                        artifact jar2
                    }
                }
            }
        """

        when:
        run "publish"

        then:
        module.assertPublished()
        module.assertArtifactsPublished("ivy-2.xml", "jar1-2.jar", "jar2-2.jar")
        module.moduleDir.file("jar1-2.jar").assertIsCopyOf(file("build/libs/jar1-2.jar"))
        module.moduleDir.file("jar2-2.jar").assertIsCopyOf(file("build/libs/jar2-2.jar"))

        and:
        with(module.ivy.artifacts.jar1) {
            name == "jar1"
            ext == "jar"
            conf == ["runtime"]
        }
    }

    @Ignore // Need to add ability to specify configurations
    public void "can publish custom configurations"() {
        given:
        buildFile << """
            apply plugin: 'ivy-publish'

            version = '2'
            group = 'org.gradle.test'

            configurations { custom }
            artifacts {
                custom file("artifact.txt"), {
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
        succeeds 'publish'

        then:
        module.ivyFile.assertIsFile()
        module.assertArtifactsPublished("ivy-2.xml", "foo-2.txt")
        with(module.ivy.artifacts.foo) {
            name == "foo"
            ext == "txt"
            "custom" in conf
        }
    }

}
