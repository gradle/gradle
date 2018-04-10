/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.plugins.signing

import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.fixtures.file.TestFile
import spock.lang.IgnoreIf;

class SigningPublicationsIntegrationSpec extends SigningIntegrationSpec {

    private TestFile enableGradleMetadata() {
        settingsFile << "enableFeaturePreview('GRADLE_METADATA')"
    }

    def "signs single Maven publication"() {
        given:
        buildFile << """
            apply plugin: 'maven-publish'
            ${keyInfo.addAsPropertiesScript()}

            publishing {
                publications {
                    mavenJava(MavenPublication) {
                        from components.java
                    }
                }
            }

            signing {
                ${signingConfiguration()}
                sign publishing.publications.mavenJava
            }
        """

        when:
        run "signMavenJavaPublication"

        then:
        ":signMavenJavaPublication" in nonSkippedTasks

        and:
        file("build", "libs", "sign-1.0.jar.asc").text
        file("build", "publications", "mavenJava", "pom-default.xml.asc").text
    }

    def "signs single Ivy publication"() {
        given:
        buildFile << """
            apply plugin: 'ivy-publish'
            ${keyInfo.addAsPropertiesScript()}

            publishing {
                publications {
                    ivyJava(IvyPublication) {
                        from components.java
                    }
                }
            }

            signing {
                ${signingConfiguration()}
                sign publishing.publications.ivyJava
            }
        """

        when:
        run "signIvyJavaPublication"

        then:
        ":signIvyJavaPublication" in nonSkippedTasks

        and:
        file("build", "libs", "sign-1.0.jar.asc").text
        file("build", "publications", "ivyJava", "ivy.xml.asc").text
    }

    def "signs Gradle metadata"() {
        given:
        buildFile << """
            apply plugin: 'ivy-publish'
            apply plugin: 'maven-publish'
            ${keyInfo.addAsPropertiesScript()}

            publishing {
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }

            signing {
                ${signingConfiguration()}
                sign publishing.publications.ivy, publishing.publications.maven
            }
        """

        when:
        enableGradleMetadata()

        and:
        run "signIvyPublication", "signMavenPublication"

        then:
        ":signIvyPublication" in nonSkippedTasks
        ":signMavenPublication" in nonSkippedTasks

        and:
        file("build", "libs", "sign-1.0.jar.asc").text
        file("build", "publications", "maven", "module.json.asc").text
        file("build", "publications", "ivy", "module.json.asc").text
    }

    @IgnoreIf({GradleContextualExecuter.parallel})
    def "publishes signature files for Maven publication"() {
        given:
        buildFile << """
            apply plugin: 'maven-publish'
            ${keyInfo.addAsPropertiesScript()}

            publishing {
                publications {
                    mavenJava(MavenPublication) {
                        from components.java
                        artifactId '$artifactId'
                    }
                }
                repositories {
                    maven {
                        name "m2"
                        url "file://\$buildDir/m2Repo/"
                    }
                }
            }

            signing {
                ${signingConfiguration()}
                sign publishing.publications.mavenJava
            }
        """

        when:
        succeeds "publishMavenJavaPublicationToM2Repository"

        then:
        ":publishMavenJavaPublicationToM2Repository" in nonSkippedTasks

        and:
        m2RepoFile(jarFileName).assertExists()
        m2RepoFile("${jarFileName}.asc").assertExists()
        pom().assertExists()
        pomSignature().assertExists()
    }
}
