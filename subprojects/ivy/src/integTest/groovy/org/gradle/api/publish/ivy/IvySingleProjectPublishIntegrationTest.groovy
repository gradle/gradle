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

class IvySingleProjectPublishIntegrationTest extends AbstractIntegrationSpec {

    def "publish multiple artifacts in single configuration"() {
        settingsFile << "rootProject.name = 'publishTest'"
        file("file1") << "some content"
        file("file2") << "other content"

        buildFile << """
            apply plugin: "base"
            apply plugin: "ivy-publish"

            group = "org.gradle.test"
            version = 1.9

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
        def ivyModule = ivyRepo.module("org.gradle.test", "publishTest", "1.9")
        ivyModule.assertArtifactsPublished("ivy-1.9.xml", "jar1-1.9.jar", "jar2-1.9.jar")
        ivyModule.moduleDir.file("jar1-1.9.jar").bytes == file("build/libs/jar1-1.9.jar").bytes
        ivyModule.moduleDir.file("jar2-1.9.jar").bytes == file("build/libs/jar2-1.9.jar").bytes

        and:
        def ivyDescriptor = ivyModule.ivy
        // TODO:DAZ Should not be 'runtime'
        ivyDescriptor.expectArtifact("jar1").conf == ["runtime"]
        ivyDescriptor.expectArtifact("jar2").conf == ["runtime"]
    }
}
