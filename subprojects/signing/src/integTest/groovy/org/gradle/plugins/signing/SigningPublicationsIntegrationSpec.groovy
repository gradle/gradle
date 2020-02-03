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

import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import spock.lang.Issue

class SigningPublicationsIntegrationSpec extends SigningIntegrationSpec {

    @ToBeFixedForInstantExecution
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
        executedAndNotSkipped(":signMavenJavaPublication")

        and:
        file("build", "libs", "sign-1.0.jar.asc").text
        file("build", "publications", "mavenJava", "pom-default.xml.asc").text
    }

    @ToBeFixedForInstantExecution
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
        executedAndNotSkipped(":signMavenJavaPublication")

        and:
        file("build", "libs", "sign-3.0.jar.asc").text
        file("build", "libs", "sign-3.0.jar").text
    }

    @ToBeFixedForInstantExecution
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
        executedAndNotSkipped(":signIvyJavaPublication")

        and:
        file("build", "libs", "sign-3.0.jar.asc").text
        file("build", "libs", "sign-3.0.jar").text
    }

    @ToBeFixedForInstantExecution
    def "artifacts can still be mutated after signing is configured"() {
        given:

        file("foo.txt") << "foo"

        buildFile << """
            apply plugin: 'maven-publish'
            ${keyInfo.addAsPropertiesScript()}

            task customJar(type:Jar) {
                with jar
                archiveClassifier = 'custom'
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

            customJar.archiveClassifier = 'custom2'

        """

        when:
        run "signCustomPublication"

        then:
        executedAndNotSkipped(":signCustomPublication")

        and:
        file("build", "libs", "sign-1.0-custom2.jar.asc").text
    }

    @ToBeFixedForInstantExecution
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
        executedAndNotSkipped(":signIvyJavaPublication")

        and:
        file("build", "libs", "sign-1.0.jar.asc").text
        file("build", "publications", "ivyJava", "ivy.xml.asc").text
    }

    @ToBeFixedForInstantExecution
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
        run "signIvyPublication", "signMavenPublication"

        then:
        executedAndNotSkipped(":signIvyPublication")
        executedAndNotSkipped(":signMavenPublication")

        and:
        file("build", "libs", "sign-1.0.jar.asc").text
        file("build", "publications", "maven", "module.json.asc").text
        file("build", "publications", "ivy", "module.json.asc").text
    }

    @ToBeFixedForInstantExecution
    def "allows signing Gradle metadata if version is a snapshot"() {
        when:
        buildFile << """
            apply plugin: 'maven-publish'
            ${keyInfo.addAsPropertiesScript()}

            version = '1.0-SNAPSHOT'

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }

            signing {
                ${signingConfiguration()}
                sign publishing.publications.maven
            }
        """

        then:
        succeeds "signMavenPublication"
    }

    @ToBeFixedForInstantExecution
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
                            archiveClassifier = "sources"
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

        when:
        succeeds "publishMavenJavaPublicationToM2Repository"

        then:
        executedAndNotSkipped(":publishMavenJavaPublicationToM2Repository")

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

    @ToBeFixedForInstantExecution
    def "publishes signature files for Ivy publication"() {
        given:
        buildFile << """
            apply plugin: 'ivy-publish'
            ${keyInfo.addAsPropertiesScript()}

            task sourceJar(type: Jar) {
                from sourceSets.main.allJava
                archiveClassifier = "source"
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
                        patternLayout {
                            artifact "[artifact]-[revision](-[classifier])(.[ext])"
                            ivy "[artifact]-[revision](-[classifier])(.[ext])"
                        }
                    }
                }
            }

            signing {
                ${signingConfiguration()}
                sign publishing.publications.ivyJava
            }
        """

        when:
        succeeds "publishIvyJavaPublicationToIvyRepository"

        then:
        executedAndNotSkipped(":publishIvyJavaPublicationToIvyRepository")

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

    @ToBeFixedForInstantExecution
    def "sign task takes into account configuration changes"() {
        given:
        buildFile << """
            apply plugin: 'maven-publish'
            ${keyInfo.addAsPropertiesScript()}

            task sourceJar(type: Jar) {
                from sourceSets.main.allJava
                archiveClassifier = "source"
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
            generateMetadataFileForMavenJavaPublication.enabled = false
        """

        when:
        run "signMavenJavaPublication"

        then:
        executedAndNotSkipped(":signMavenJavaPublication")

        and:
        file("build", "libs", "sign-1.0.jar.asc").assertDoesNotExist()
        file("build", "libs", "sign-1.0-source.jar.asc").assertExists()
        file("build", "publications", "mavenJava", "pom-default.xml.asc").text
    }

    @ToBeFixedForInstantExecution
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
        executedAndNotSkipped(":publishMavenJavaPublicationToM2Repository")

        and:
        pomSignature().assertExists()
        m2RepoFile("${jarFileName}.asc").assertDoesNotExist()
    }

    @ToBeFixedForInstantExecution
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
        executedAndNotSkipped(":signIvyPublication")
        executedAndNotSkipped(":signMavenPublication")

        and:
        file("build", "libs", "sign-1.0.jar.asc").assertExists()
        file("build", "publications", "maven", "pom-default.xml.asc").assertExists()
        file("build", "publications", "ivy", "ivy.xml.asc").assertExists()
    }

    @ToBeFixedForInstantExecution
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
    @ToBeFixedForInstantExecution
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
                        patternLayout {
                            artifact "[artifact]-[revision](-[classifier])(.[ext])"
                            ivy "[artifact]-[revision](-[classifier])(.[ext])"
                        }
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
        skipped(":signIvyPublication")
        skipped(":signMavenPublication")
        executedAndNotSkipped(":publishIvyPublicationToIvyRepository")
        executedAndNotSkipped(":publishMavenPublicationToMavenRepository")

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

    @ToBeFixedForInstantExecution
    @Issue("https://github.com/gradle/gradle/issues/5136")
    def "doesn't publish stale signatures"() {
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
                        patternLayout {
                            artifact "[artifact]-[revision](-[classifier])(.[ext])"
                            ivy "[artifact]-[revision](-[classifier])(.[ext])"
                        }
                    }
                }
            }

            signing {
                ${signingConfiguration()}
                sign publishing.publications
            }

            tasks.register("cleanRepo") {
                doLast {
                    new File("\${buildDir}/m2Repo").deleteDir()
                    new File("\${buildDir}/ivyRepo").deleteDir()
                }
            }
            def sign = project.getProperty("sign")
            if (sign == 'skip') {
                tasks.withType(Sign)*.onlyIf { false }
            } else {
                tasks.withType(Sign)*.enabled = Boolean.valueOf(sign)
            }

        """

        when:
        succeeds "publish", "-Psign=true"

        then:
        executedAndNotSkipped(":signIvyPublication", ":publishIvyPublicationToIvyRepository")
        executedAndNotSkipped(":signMavenPublication", ":publishMavenPublicationToMavenRepository")

        and:
        pom().assertExists()
        pomSignature().assertExists()
        module().assertExists()
        moduleSignature().assertExists()
        m2RepoFile(jarFileName).assertExists()
        m2RepoFile("${jarFileName}.asc").assertExists()
        ivyRepoFile(jarFileName).assertExists()
        ivyRepoFile("${jarFileName}.asc").assertExists()
        ivyRepoFile("ivy-${version}.xml").assertExists()
        ivyRepoFile("ivy-${version}.xml.asc").assertExists()

        when:
        succeeds "cleanRepo", "publish", "-Psign=false"

        then:
        skipped(":signIvyPublication")
        skipped(":signMavenPublication")
        executedAndNotSkipped(":publishIvyPublicationToIvyRepository")
        executedAndNotSkipped(":publishMavenPublicationToMavenRepository")

        and:
        pom().assertExists()
        pomSignature().assertDoesNotExist()
        module().assertExists()
        moduleSignature().assertDoesNotExist()
        m2RepoFile(jarFileName).assertExists()
        m2RepoFile("${jarFileName}.asc").assertDoesNotExist()
        ivyRepoFile(jarFileName).assertExists()
        ivyRepoFile("${jarFileName}.asc").assertDoesNotExist()
        ivyRepoFile("ivy-${version}.xml").assertExists()
        ivyRepoFile("ivy-${version}.xml.asc").assertDoesNotExist()

        when:
        succeeds "cleanRepo", "publish", "-Psign=skip"

        then:
        skipped(":signIvyPublication")
        skipped(":signMavenPublication")
        executedAndNotSkipped(":publishIvyPublicationToIvyRepository")
        executedAndNotSkipped(":publishMavenPublicationToMavenRepository")

        and:
        pom().assertExists()
        pomSignature().assertDoesNotExist()
        module().assertExists()
        moduleSignature().assertDoesNotExist()
        m2RepoFile(jarFileName).assertExists()
        m2RepoFile("${jarFileName}.asc").assertDoesNotExist()
        ivyRepoFile(jarFileName).assertExists()
        ivyRepoFile("${jarFileName}.asc").assertDoesNotExist()
        ivyRepoFile("ivy-${version}.xml").assertExists()
        ivyRepoFile("ivy-${version}.xml.asc").assertDoesNotExist()
    }
}
