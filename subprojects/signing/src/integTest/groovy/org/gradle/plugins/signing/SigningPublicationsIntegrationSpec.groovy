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

import org.gradle.integtests.fixtures.FeaturePreviewsFixture
import spock.lang.Issue

import static org.gradle.integtests.fixtures.FeaturePreviewsFixture.enableGradleMetadata

class SigningPublicationsIntegrationSpec extends SigningIntegrationSpec {

    @Override
    def setup() {
        FeaturePreviewsFixture.enableStablePublishing(settingsFile)
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

    def "component can still be mutated after signing is configured for a Maven publication"() {
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

            version = 3.0
        """

        when:
        run "signMavenJavaPublication"

        then:
        ":signMavenJavaPublication" in nonSkippedTasks

        and:
        file("build", "libs", "sign-3.0.jar.asc").text
        file("build", "libs", "sign-3.0.jar").text
    }

    def "component can still be mutated after signing is configured for an Ivy publication"() {
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

            version = 3.0
        """

        when:
        run "signIvyJavaPublication"

        then:
        ":signIvyJavaPublication" in nonSkippedTasks

        and:
        file("build", "libs", "sign-3.0.jar.asc").text
        file("build", "libs", "sign-3.0.jar").text
    }

    def "artifacts can still be mutated after signing is configured"() {
        given:

        file("foo.txt") << "foo"

        buildFile << """
            apply plugin: 'maven-publish'
            ${keyInfo.addAsPropertiesScript()}

            task customJar(type:Jar) {
                with jar
                classifier = 'custom'
            }

            publishing {
                publications {
                    custom(MavenPublication) {
                        artifact customJar 
                    }
                }
            }

            signing {
                ${signingConfiguration()}
                sign publishing.publications.custom
            }

            customJar.classifier = 'custom2'

        """

        when:
        run "signCustomPublication"

        then:
        ":signCustomPublication" in nonSkippedTasks

        and:
        file("build", "libs", "sign-1.0-custom2.jar.asc").text
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
        enableGradleMetadata(settingsFile)

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

    def "publishes signature files for Maven publication"() {
        given:
        buildFile << """
            apply plugin: 'maven-publish'
            ${keyInfo.addAsPropertiesScript()}

            task sourceJar(type: Jar) {
                from sourceSets.main.allJava
            }

            publishing {
                publications {
                    mavenJava(MavenPublication) {
                        from components.java
                        artifactId '$artifactId'
                        artifact sourceJar {
                            classifier "sources"
                        }
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

        and:
        enableGradleMetadata(settingsFile)

        when:
        succeeds "publishMavenJavaPublicationToM2Repository"

        then:
        ":publishMavenJavaPublicationToM2Repository" in nonSkippedTasks

        and:
        pom().assertExists()
        pomSignature().assertExists()
        m2RepoFile(jarFileName).assertExists()
        m2RepoFile("${jarFileName}.asc").assertExists()
        m2RepoFile("$artifactId-${version}-sources.jar").assertExists()
        m2RepoFile("$artifactId-${version}-sources.jar.asc").assertExists()
        m2RepoFile("$artifactId-${version}.module").assertExists()
        m2RepoFile("$artifactId-${version}.module.asc").assertExists()
    }

    def "publishes signature files for Ivy publication"() {
        given:
        buildFile << """
            apply plugin: 'ivy-publish'
            ${keyInfo.addAsPropertiesScript()}

            task sourceJar(type: Jar) {
                from sourceSets.main.allJava
                classifier "source"
            }

            publishing {
                publications {
                    ivyJava(IvyPublication) {
                        from components.java
                        module '$artifactId'
                        artifact(sourceJar) {
                            type "source"
                            conf "compile"
                        }
                    }
                }
                repositories {
                    ivy {
                        url "file://\$buildDir/ivyRepo/"
                        layout "pattern"
                        artifactPattern "\$buildDir/ivyRepo/[artifact]-[revision](-[classifier])(.[ext])"
                        ivyPattern "\$buildDir/ivyRepo/[artifact]-[revision](-[classifier])(.[ext])"
                    }
                }
            }

            signing {
                ${signingConfiguration()}
                sign publishing.publications.ivyJava
            }
        """

        and:
        enableGradleMetadata(settingsFile)

        when:
        succeeds "publishIvyJavaPublicationToIvyRepository"

        then:
        ":publishIvyJavaPublicationToIvyRepository" in nonSkippedTasks

        and:
        ivyRepoFile(jarFileName).assertExists()
        ivyRepoFile("${jarFileName}.asc").assertExists()
        ivyRepoFile("ivy-${version}.xml").assertExists()
        ivyRepoFile("ivy-${version}.xml.asc").assertExists()
        ivyRepoFile("$artifactId-${version}-source.jar").assertExists()
        ivyRepoFile("$artifactId-${version}-source.jar.asc").assertExists()
        ivyRepoFile("$artifactId-${version}.module").assertExists()
        ivyRepoFile("$artifactId-${version}.module.asc").assertExists()
    }

    def "sign task takes into account configuration changes"() {
        given:
        buildFile << """
            apply plugin: 'maven-publish'
            ${keyInfo.addAsPropertiesScript()}

            task sourceJar(type: Jar) {
                from sourceSets.main.allJava
                classifier "source"
            }

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

            publishing.publications.mavenJava.artifacts = [] 
            publishing.publications.mavenJava.artifact(sourceJar)
        """

        when:
        run "signMavenJavaPublication"

        then:
        ":signMavenJavaPublication" in nonSkippedTasks

        and:
        file("build", "libs", "sign-1.0.jar.asc").assertDoesNotExist()
        file("build", "libs", "sign-1.0-source.jar.asc").assertExists()
        file("build", "publications", "mavenJava", "pom-default.xml.asc").text
    }

    def "publish task takes into account configuration changes"() {
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

            signMavenJavaPublication.signatures.all { signature ->
                if (signature.toSign.name.endsWith('.jar')) {
                    signMavenJavaPublication.signatures.remove signature
                }    
            }
        """

        when:
        succeeds "publishMavenJavaPublicationToM2Repository"

        then:
        ":publishMavenJavaPublicationToM2Repository" in nonSkippedTasks

        and:
        pomSignature().assertExists()
        m2RepoFile("${jarFileName}.asc").assertDoesNotExist()
    }

    def "signs all publications in container"() {
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
                sign publishing.publications
            }
        """

        when:
        run "signIvyPublication", "signMavenPublication"

        then:
        ":signIvyPublication" in nonSkippedTasks
        ":signMavenPublication" in nonSkippedTasks

        and:
        file("build", "libs", "sign-1.0.jar.asc").assertExists()
        file("build", "publications", "maven", "pom-default.xml.asc").assertExists()
        file("build", "publications", "ivy", "ivy.xml.asc").assertExists()
    }

    def "signs filtered publications of container"() {
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
                sign publishing.publications.matching { it.name == 'maven' }
            }
        """

        when:
        succeeds "signMavenPublication"

        then:
        fails "signIvyPublication"
        failureDescriptionContains "Task 'signIvyPublication' not found"
    }

    @Issue("https://github.com/gradle/gradle/issues/5099")
    def "disabling sign tasks skips uploading signature artifacts but does not break publishing"() {
        given:
        buildFile << """
            apply plugin: 'ivy-publish'
            apply plugin: 'maven-publish'
            ${keyInfo.addAsPropertiesScript()}

            publishing {
                publications {
                    ivy(IvyPublication) {
                        from components.java
                        module '$artifactId'
                    }
                    maven(MavenPublication) {
                        from components.java
                        artifactId '$artifactId'
                    }
                }
                repositories {
                    maven {
                        url "file://\$buildDir/m2Repo/"
                    }
                    ivy {
                        url "file://\$buildDir/ivyRepo/"
                        layout "pattern"
                        artifactPattern "\$buildDir/ivyRepo/[artifact]-[revision](-[classifier])(.[ext])"
                        ivyPattern "\$buildDir/ivyRepo/[artifact]-[revision](-[classifier])(.[ext])"
                    }
                }
            }

            signing {
                ${signingConfiguration()}
                sign publishing.publications
            }

            tasks.withType(Sign)*.enabled = false
        """

        when:
        succeeds "publish"

        then:
        ":signIvyPublication" in skippedTasks
        ":signMavenPublication" in skippedTasks
        ":publishIvyPublicationToIvyRepository" in nonSkippedTasks
        ":publishMavenPublicationToMavenRepository" in nonSkippedTasks

        and:
        pom().assertExists()
        pomSignature().assertDoesNotExist()
        m2RepoFile(jarFileName).assertExists()
        m2RepoFile("${jarFileName}.asc").assertDoesNotExist()
        ivyRepoFile(jarFileName).assertExists()
        ivyRepoFile("${jarFileName}.asc").assertDoesNotExist()
        ivyRepoFile("ivy-${version}.xml").assertExists()
        ivyRepoFile("ivy-${version}.xml.asc").assertDoesNotExist()
    }
}
