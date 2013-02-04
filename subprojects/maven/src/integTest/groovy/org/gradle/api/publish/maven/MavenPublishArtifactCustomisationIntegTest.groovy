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

package org.gradle.api.publish.maven

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class MavenPublishArtifactCustomisationIntegTest extends AbstractIntegrationSpec {

    def "can attach custom artifacts"() {
        given:
        createBuildScripts("""
            publications {
                mavenCustom(MavenPublication) {
                    artifact "customFile.txt"
                    artifact customFileTask.outputFile
                    artifact customJar
                }
            }
""", """
            publishMavenCustomPublicationToMavenRepository.dependsOn(customFileTask)
""")
        when:
        succeeds 'publish'

        then:
        def module = mavenRepo.module("group", "projectText", "1.0")
        module.assertPublished()
        module.parsedPom.packaging == "txt"
        module.assertArtifactsPublished("projectText-1.0.pom", "projectText-1.0.txt", "projectText-1.0-docs.html", "projectText-1.0-customjar.jar")
    }

    def "can set custom artifacts to override component artifacts"() {
        given:
        createBuildScripts("""
            publications {
                mavenCustom(MavenPublication) {
                    from components.java
                    artifacts = ["customFile.txt", customFileTask.outputFile, customJar]
                }
            }

""", """
            publishMavenCustomPublicationToMavenRepository.dependsOn(customFileTask)
""")
        when:
        succeeds 'publish'

        then:
        def module = mavenRepo.module("group", "projectText", "1.0")
        module.assertPublished()
        module.parsedPom.packaging == "txt"
        module.assertArtifactsPublished("projectText-1.0.pom", "projectText-1.0.txt", "projectText-1.0-docs.html", "projectText-1.0-customjar.jar")
    }

    def "can configure custom artifacts when creating"() {
        given:
        createBuildScripts("""
            publications {
                mavenCustom(MavenPublication) {
                    artifact("customFile.txt") {
                        classifier "output"
                    }
                    artifact(customFileTask.outputFile) {
                        extension "htm"
                        classifier "documentation"
                        builtBy customFileTask
                    }
                    artifact customJar {
                        classifier ""
                        extension "war"
                    }
                }
            }
""")
        when:
        succeeds 'publish'

        then:
        def module = mavenRepo.module("group", "projectText", "1.0")
        module.assertPublished()
        module.parsedPom.packaging == "war"
        module.assertArtifactsPublished("projectText-1.0.pom", "projectText-1.0.war", "projectText-1.0-documentation.htm", "projectText-1.0-output.txt")
    }

    def "can attach custom file artifacts with map notation"() {
        given:
        createBuildScripts("""
            publications {
                mavenCustom(MavenPublication) {
                    artifact file: "customFile.txt", classifier: "output"
                    artifact file: customFileTask.outputFile, extension: "htm", classifier: "documentation", builtBy: customFileTask
                    artifact source: customJar, extension: "war", classifier: ""
                }
            }
""")
        when:
        succeeds 'publish'

        then:
        def module = mavenRepo.module("group", "projectText", "1.0")
        module.assertPublished()
        module.parsedPom.packaging == "war"
        module.assertArtifactsPublished("projectText-1.0.pom", "projectText-1.0.war", "projectText-1.0-documentation.htm", "projectText-1.0-output.txt")
    }

    def "can configure custom artifacts post creation"() {
        given:
        createBuildScripts("""
            publications {
                mavenCustom(MavenPublication) {
                    artifact "customFile.txt"
                    artifact customFileTask.outputFile
                    artifact customJar
                }
            }
""", """
            publishing.publications.mavenCustom.artifacts.each {
                it.extension = "mod"
            }
            publishMavenCustomPublicationToMavenRepository.dependsOn(customFileTask)
""")
        when:
        succeeds 'publish'

        then:
        def module = mavenRepo.module("group", "projectText", "1.0")
        module.assertPublished()
        module.parsedPom.packaging == "mod"
        module.assertArtifactsPublished("projectText-1.0.pom", "projectText-1.0.mod", "projectText-1.0-docs.mod", "projectText-1.0-customjar.mod")
    }

    def "can attach artifact with no extension"() {
        given:
        file("no-extension-1.0-classifier") << "some content"
        createBuildScripts("""
            publications {
                mavenCustom(MavenPublication) {
                    artifact "customFile.txt"
                    artifact "no-extension-1.0-classifier"
                }
            }
""")
        when:
        succeeds 'publish'

        then:
        def module = mavenRepo.module("group", "projectText", "1.0")
        module.assertPublished()
        module.parsedPom.packaging == "txt"
        module.assertArtifactsPublished("projectText-1.0.pom", "projectText-1.0.txt", "projectText-1.0-classifier")
    }

    def "reports failure publishing when validation fails"() {
        given:
        file("a-directory").createDir()

        createBuildScripts("""
            publications {
                mavenCustom(MavenPublication) {
                    artifact "a-directory"
                }
            }
""")
        when:
        fails 'publish'

        then:
        failure.assertHasDescription("Execution failed for task ':publishMavenCustomPublicationToMavenRepository'.")
        failure.assertHasCause("Failed to publish publication 'mavenCustom' to repository 'maven'")
        failure.assertHasCause("Cannot publish maven publication 'mavenCustom': artifact file is a directory")
    }

    private createBuildScripts(def publications, def append = "") {
        settingsFile << "rootProject.name = 'projectText'"
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'maven-publish'

            group = 'group'
            version = '1.0'

            file("customFile.txt") << 'some content'

            task customFileTask {
                ext.outputFile = file('customFile-1.0-docs.html')
                doLast {
                    outputFile << '<html/>'
                }
            }

            task customJar(type: Jar) {
                from file("customFile.txt")
                classifier "customjar"
            }

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                $publications
            }

            $append
        """
    }
}
