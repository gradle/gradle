/*
 * Copyright 2013 the original author or authors.
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

class IvyPublishArtifactCustomisationIntegTest extends AbstractIntegrationSpec {

    def module = ivyRepo.module("org.gradle.test", "ivyPublish", "2.4")

    public void "can publish custom artifacts"() {
        given:
        createBuildScripts("""
            publications {
                ivy(IvyPublication) {
                    configurations {
                        runtime {
                            artifact "customFile.txt"
                            artifact customDocsTask.outputFile
                        }
                        custom {
                            artifact customJar
                        }
                    }
                }
            }
""")

        when:
        succeeds 'publish'

        then:
        module.assertPublished()
        module.assertArtifactsPublished("ivy-2.4.xml", "customFile-2.4.txt", "customDocs-2.4.html", "customJar-2.4.jar")
        // TODO:DAZ Validate configurations
    }

    def "can configure custom artifacts when creating"() {
        given:
        createBuildScripts("""
            publications {
                ivy(IvyPublication) {
                    configurations {
                        custom {
                            artifact("customFile.txt") {
                                name "changedFile"
                                extension "customExt"
                            }
                            artifact(customDocsTask.outputFile) {
                                name "changedDocs"
                                type "htm"
                            }
                        }
                        other {
                            artifact customJar {
                                extension "war"
                            }
                        }
                    }
                }
            }
""")
        when:
        succeeds 'publish'

        then:
        module.assertPublished()
        module.assertArtifactsPublished("ivy-2.4.xml", "changedFile-2.4.customExt", "changedDocs-2.4.html", "customJar-2.4.war")
        // TODO:DAZ Validate configurations
    }

    def "can publish custom file artifacts with map notation"() {
        given:
        createBuildScripts("""
            publications {
                ivy(IvyPublication) {
                    configurations {
                        custom {
                            artifact file: "customFile.txt", extension: "customExt"
                            artifact file: customDocsTask.outputFile, name: "changedDocs", extension: "htm"
                        }
                    }
                }
            }
""")
        when:
        succeeds 'publish'

        then:
        module.assertPublished()
        module.assertArtifactsPublished("ivy-2.4.xml", "customFile-2.4.customExt", "changedDocs-2.4.htm")
        // TODO:DAZ Validate configurations
    }

    def "can set custom artifacts to override component artifacts"() {
        given:
        createBuildScripts("""
            publications {
                ivy(IvyPublication) {
                    from components.java
                    configurations {
                        runtime {
                            artifacts = ["customFile.txt", customDocsTask.outputFile, customJar]
                        }
                    }
                }
            }
""")
        when:
        succeeds 'publish'

        then:
        module.assertPublished()
        module.assertArtifactsPublished("ivy-2.4.xml", "customFile-2.4.txt", "customDocs-2.4.html", "customJar-2.4.jar")
    }

    def "can configure custom artifacts post creation"() {
        given:
        createBuildScripts("""
            publications {
                ivy(IvyPublication) {
                    configurations {
                        runtime {
                            artifact "customFile.txt"
                            artifact customDocsTask.outputFile
                        }
                        custom {
                            artifact customJar
                        }
                    }
                }
            }
""", """
            publishing.publications.ivy.configurations.each {
                it.artifacts.each {
                    it.extension = "mod"
                }
            }
""")
        when:
        succeeds 'publish'

        then:
        module.assertPublished()
        module.assertArtifactsPublished("ivy-2.4.xml", "customFile-2.4.mod", "customDocs-2.4.mod", "customJar-2.4.mod")
    }

    def "can publish artifact with no extension"() {
        given:
        file("no-extension") << "some content"
        createBuildScripts("""
            publications {
                ivy(IvyPublication) {
                    configurations {
                        custom {
                            artifact file('no-extension')
                        }
                    }
                }
            }
""")
        when:
        succeeds 'publish'

        then:
        module.assertPublished()
        module.assertArtifactsPublished("ivy-2.4.xml", "no-extension-2.4")
    }

    def "reports failure publishing when validation fails"() {
        given:
        file("a-directory").createDir()

        createBuildScripts("""
            publications {
                ivy(IvyPublication) {
                    configurations {
                        custom {
                            artifact "a-directory"
                        }
                    }
                }
            }
""")
        when:
        fails 'publish'

        then:
        failure.assertHasDescription("Execution failed for task ':publishIvyPublicationToIvyRepository'.")
        failure.assertHasCause("Failed to publish publication 'ivy' to repository 'ivy'")
        failure.assertHasCause("Cannot publish ivy publication 'ivy': artifact file is a directory")
    }

    private createBuildScripts(def publications, def append = "") {
        file("customFile.txt") << "some content"
        settingsFile << "rootProject.name = 'ivyPublish'"
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'ivy-publish'

            group = 'org.gradle.test'
            version = '2.4'

            task customDocsTask {
                ext.outputFile = file('customDocs.html')
                doLast {
                    outputFile << '<html/>'
                }
            }

            task customJar(type: Jar) {
                from file("customFile.txt")
                baseName "customJar"
            }

            publishing {
                repositories {
                    ivy { url "${ivyRepo.uri}" }
                }
                $publications
            }

            publishIvyPublicationToIvyRepository.dependsOn(customDocsTask)

            $append
        """
    }
}
