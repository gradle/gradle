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
import org.spockframework.util.TextUtil
import spock.lang.Issue
import org.gradle.test.fixtures.ivy.IvyDescriptor

public class IvyLocalPublishIntegrationTest extends AbstractIntegrationSpec {
    public void canPublishToLocalFileRepository() {
        given:
        def module = ivyRepo.module("org.gradle", "publish", "2")

        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'ivy-publish'

            version = '2'
            group = 'org.gradle'

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
        module.assertChecksumPublishedFor(module.ivyFile)

        module.jarFile.assertIsCopyOf(file('build/libs/publish-2.jar'))
        module.assertChecksumPublishedFor(module.jarFile)
    }

    @Issue("GRADLE-2456")
    public void generatesSHA1FileWithLeadingZeros() {
        given:
        def module = ivyRepo.module("org.gradle", "publish", "2")
        byte[] jarBytes = [0, 0, 0, 5]
        def artifactFile = file("testfile.bin")
        artifactFile << jarBytes
        def artifactPath = TextUtil.escape(artifactFile.path)
        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'ivy-publish'

            group = "org.gradle"
            version = '2'

            artifacts {
                archives file: file("${artifactPath}"), name: 'testfile', type: 'bin'
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
        def shaOneFile = module.moduleDir.file("testfile-2.bin.sha1")
        shaOneFile.exists()
        shaOneFile.text == "00e14c6ef59816760e2c9b5a57157e8ac9de4012"
    }

    @Issue("GRADLE-1811")
    public void canGenerateTheIvyXmlWithoutPublishing() {
        given:
        def module = ivyRepo.module("org.gradle", "generateIvy", "2")

        settingsFile << "rootProject.name = 'generateIvy'"
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'ivy-publish'

            version = '2'
            group = 'org.gradle'

            publishing {
                repositories {
                    ivy {
                        url "${ivyRepo.uri}"
                    }
                }
            }

            generateIvyModuleDescriptor {
                destination = 'generated-ivy.xml'
            }
        """

        when:
        succeeds 'generateIvyModuleDescriptor'

        then:
        file('generated-ivy.xml').assertIsFile()
        IvyDescriptor ivy = new IvyDescriptor(file('generated-ivy.xml'))
        with (ivy.artifacts['generateIvy']) {
            name == 'generateIvy'
            ext == 'jar'
            conf == ['archives', 'runtime']
        }

        and:
        module.ivyFile.assertDoesNotExist()
    }

    def "can publish with non-ascii characters"() {
        def organisation = 'group-©¨¿¬¹'
        def moduleName = 'artifact-Œ¨ ??'
        def version = 'version-???Æ'
        def description = 'description-Ãº'

        given:
        settingsFile << "rootProject.name = '${moduleName}'"
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'ivy-publish'

            group = '${organisation}'
            version = '${version}'

            publishing {
                repositories {
                    ivy { url "${ivyRepo.uri}" }
                }
                publications {
                    ivy {
                        descriptor.withXml {
                            asNode().info[0].appendNode('description', "${description}")
                        }
                    }
                }
            }
        """
        when:
        succeeds 'publish'

        then:
        def module = ivyRepo.module(organisation, moduleName, version)
        def ivyXmlInfo = module.ivyXml.info[0]
        ivyXmlInfo.@organisation == organisation
        ivyXmlInfo.@module == moduleName
        ivyXmlInfo.@revision == version
        ivyXmlInfo.description == description
    }


}
