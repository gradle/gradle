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

import org.gradle.test.fixtures.ivy.IvyDescriptorArtifact

class IvyPublishArtifactCustomisationIntegTest extends AbstractIvyPublishIntegTest {

    def module = ivyRepo.module("org.gradle.test", "ivyPublish", "2.4")

    public void "can publish custom artifacts"() {
        given:
        createBuildScripts("""
            publications {
                ivy(IvyPublication) {
                    artifact "customFile.txt"
                    artifact customDocsTask.outputFile
                    artifact customJar
                }
            }
""", """
            publishIvyPublicationToIvyRepository.dependsOn(customDocsTask)
""")

        when:
        succeeds 'publish'

        then:
        module.assertPublished()
        module.assertArtifactsPublished("ivy-2.4.xml", "customFile-2.4.txt", "customDocs-2.4.html", "customJar-2.4.jar")

        and:
        def ivy = module.ivy
        ivy.artifacts["customFile"].hasAttributes("txt", "txt", null)
        ivy.artifacts["customDocs"].hasAttributes("html", "html", null)
        ivy.artifacts["customJar"].hasAttributes("jar", "jar", null)

        and:
        resolveArtifacts(module) == ["customDocs-2.4.html", "customFile-2.4.txt", "customJar-2.4.jar"]
    }

    def "can configure custom artifacts when creating"() {
        given:
        createBuildScripts("""
            publications {
                ivy(IvyPublication) {
                    configurations {
                        foo
                        bar
                        "default" {
                            extend "foo"
                        }
                    }
                    artifact("customFile.txt") {
                        name "changedFile"
                        extension "customExt"
                        conf "foo,bar"
                    }
                    artifact(customDocsTask.outputFile) {
                        name "changedDocs"
                        type "htm"
                        builtBy customDocsTask
                    }
                    artifact(customJar) {
                        extension "war"
                        conf "*"
                    }
                }
            }
""")
        when:
        succeeds 'publish'

        then:
        module.assertPublished()
        module.assertArtifactsPublished("ivy-2.4.xml", "changedDocs-2.4.html", "changedFile-2.4.customExt", "customJar-2.4.war")

        and:
        def ivy = module.ivy
        ivy.artifacts["changedFile"].hasAttributes("customExt", "txt", ["foo", "bar"])
        ivy.artifacts["changedDocs"].hasAttributes("html", "htm", null)
        ivy.artifacts["customJar"].hasAttributes("war", "jar", ["*"])

        and:
        resolveArtifacts(module) == ["changedDocs-2.4.html", "changedFile-2.4.customExt", "customJar-2.4.war"]
    }

    def "can publish custom file artifacts with map notation"() {
        given:
        createBuildScripts("""
            publications {
                ivy(IvyPublication) {
                    configurations {
                        foo
                        bar
                        "default" {
                            extend "foo"
                        }
                    }
                    artifact source: "customFile.txt", extension: "customExt", conf: "foo,bar"
                    artifact source: customDocsTask.outputFile, name: "changedDocs", extension: "htm", builtBy: customDocsTask
                    artifact source: customJar, name: "changedJar", extension: "war", type: "web-archive", conf: "*"
                }
            }
""")
        when:
        succeeds 'publish'

        then:
        module.assertPublished()
        module.assertArtifactsPublished("ivy-2.4.xml", "changedDocs-2.4.htm", "customFile-2.4.customExt", "changedJar-2.4.war")

        and:
        def ivy = module.ivy
        ivy.artifacts["customFile"].hasAttributes("customExt", "txt", ["foo", "bar"])
        ivy.artifacts["changedDocs"].hasAttributes("htm", "html", null)
        ivy.artifacts["changedJar"].hasAttributes("war", "web-archive", ["*"])

        and:
        resolveArtifacts(module) == ["changedDocs-2.4.htm", "changedJar-2.4.war", "customFile-2.4.customExt"]
    }

    def "can set custom artifacts to override component artifacts"() {
        given:
        createBuildScripts("""
            publications {
                ivy(IvyPublication) {
                    from components.java
                    artifacts = ["customFile.txt", customDocsTask.outputFile, customJar]
                }
            }
""", """
            publishIvyPublicationToIvyRepository.dependsOn(customDocsTask)
""")
        when:
        succeeds 'publish'

        then:
        module.assertPublished()
        module.assertArtifactsPublished("ivy-2.4.xml", "customFile-2.4.txt", "customDocs-2.4.html", "customJar-2.4.jar")
        module.ivy.artifacts.keySet() == ["customFile", "customDocs", "customJar"] as Set
    }

    def "can configure custom artifacts post creation"() {
        given:
        createBuildScripts("""
            publications {
                ivy(IvyPublication) {
                    artifact "customFile.txt"
                    artifact(customDocsTask.outputFile) { builtBy customDocsTask }
                    artifact customJar
                }
            }
""", """
            publishing.publications.ivy.artifacts.each {
                it.extension = "mod"
                it.conf = "mod-conf"
            }
""")
        when:
        succeeds 'publish'

        then:
        module.assertPublished()
        module.assertArtifactsPublished("ivy-2.4.xml", "customFile-2.4.mod", "customDocs-2.4.mod", "customJar-2.4.mod")

        for (IvyDescriptorArtifact artifact : module.ivy.artifacts.values()) {
            artifact.ext == "mod"
            artifact.conf == "mod-conf"
        }
    }

    def "can publish artifact with no extension"() {
        given:
        file("no-extension") << "some content"
        createBuildScripts("""
            publications {
                ivy(IvyPublication) {
                    artifact file('no-extension')
                }
            }
""")
        when:
        succeeds 'publish'

        then:
        module.assertPublished()
        module.assertArtifactsPublished("ivy-2.4.xml", "no-extension-2.4")
        module.ivy.artifacts["no-extension"].hasAttributes(null, null, null)

        // TODO:DAZ Fix publication with empty extension so it can be resolved
//        and:
//        resolveArtifacts(module) == ["no-extension-2.4"]
    }

    def "can publish artifact with classifier"() {
        given:
        file("no-extension") << "some content"
        createBuildScripts("""
            publications {
                ivy(IvyPublication) {
                    artifact source: customJar, classifier: "classy"
                }
            }
""")
        when:
        succeeds 'publish'

        then:
        module.assertPublished()
        module.assertArtifactsPublished("ivy-2.4.xml", "customJar-2.4-classy.jar")
        module.ivy.artifacts["customJar"].hasAttributes("jar", "jar", null, "classy")

        and:
        resolveArtifacts(module) == ["customJar-2.4-classy.jar"]
    }

    def "can add custom configurations"() {
        given:
        createBuildScripts("""
            publications {
                ivy(IvyPublication) {
                    configurations {
                        runtime
                        base {}
                        custom {
                            extend "runtime"
                            extend "base"
                        }
                    }
                }
            }
""")

        when:
        succeeds 'publish'

        then:
        module.assertPublished()
        def ivy = module.ivy
        ivy.configurations.keySet() == ["base", "custom", "runtime"] as Set
        ivy.configurations["runtime"].extend == null
        ivy.configurations["base"].extend == null
        ivy.configurations["custom"].extend == ["runtime", "base"] as Set
    }

    def "reports failure publishing when validation fails"() {
        given:
        file("a-directory").createDir()

        createBuildScripts("""
            publications {
                ivy(IvyPublication) {
                    artifact "a-directory"
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

            $append
        """
    }
}
