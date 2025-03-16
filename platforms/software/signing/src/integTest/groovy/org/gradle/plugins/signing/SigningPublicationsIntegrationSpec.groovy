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

import org.gradle.test.fixtures.file.TestFile
import spock.lang.Issue

class SigningPublicationsIntegrationSpec extends SigningIntegrationSpec {

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
                        artifactId = '$artifactId'
                        artifact sourceJar {
                            archiveClassifier = "sources"
                        }
                    }
                }
                repositories {
                    maven {
                        name = "m2"
                        url = layout.buildDirectory.dir("m2Repo")
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

    def "publishes signature files for Ivy publication with #layout pattern layout"() {
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
                        module = '$artifactId'
                        artifact(sourceJar) {
                            type = "source"
                            conf = "compile"
                        }
                    }
                }
                repositories {
                    ivy {
                        url = layout.buildDirectory.dir("ivyRepo")
                        $declaration
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
        expectedFiles(this).forEach { it.assertExists() }
        unexpectedFiles(this).forEach { it.assertDoesNotExist() }

        where:
        layout     | declaration | expectedFiles                               | unexpectedFiles
        "standard" | ""          | this.&expectedFilesIvyPublishStandardLayout | { [] }
        "custom"   | """
                     patternLayout {
                       artifact "[artifact]-[revision](-[classifier])(.[ext])"
                       ivy "[artifact]-[revision](-[classifier])(.[ext])"
                     }"""


                                 | this.&expectedFilesIvyPublishCustomLayout   | this.&unexpectedFilesIvyPublishCustomLayout
    }

    private static List<TestFile> expectedFilesIvyPublishStandardLayout(SigningPublicationsIntegrationSpec spec) {

        def standardIvyRepoFile = { String... path ->
            spec.file("build", "ivyRepo", "sign", *path)
        }

        [
            standardIvyRepoFile(spec.artifactId, spec.version, spec.jarFileName),
            standardIvyRepoFile(spec.artifactId, spec.version, "${spec.jarFileName}.asc"),
            standardIvyRepoFile(spec.artifactId, spec.version, "${spec.jarFileName}.asc"),
            standardIvyRepoFile(spec.artifactId, spec.version, "ivy-${spec.version}.xml"),
            standardIvyRepoFile(spec.artifactId, spec.version, "ivy-${spec.version}.xml.asc"),
            standardIvyRepoFile(spec.artifactId, spec.version, "$spec.artifactId-${spec.version}-source.jar"),
            standardIvyRepoFile(spec.artifactId, spec.version, "$spec.artifactId-${spec.version}-source.jar.asc"),
            standardIvyRepoFile(spec.artifactId, spec.version, "$spec.artifactId-${spec.version}.module"),
            standardIvyRepoFile(spec.artifactId, spec.version, "$spec.artifactId-${spec.version}.module.asc")
        ]
    }

    private static List<TestFile> expectedFilesIvyPublishCustomLayout(SigningPublicationsIntegrationSpec spec) {
        [
            spec.ivyRepoFile(spec.jarFileName),
            spec.ivyRepoFile("${spec.jarFileName}.asc"),
            spec.ivyRepoFile("ivy-${spec.version}.xml"),
            spec.ivyRepoFile("ivy-${spec.version}.xml.asc"),
            spec.ivyRepoFile("$spec.artifactId-${spec.version}-source.jar"),
            spec.ivyRepoFile("$spec.artifactId-${spec.version}-source.jar.asc")
        ]
    }

    private static List<TestFile> unexpectedFilesIvyPublishCustomLayout(SigningPublicationsIntegrationSpec spec) {
        [
            spec.ivyRepoFile("$spec.artifactId-${spec.version}.module"),
            spec.ivyRepoFile("$spec.artifactId-${spec.version}.module.asc")
        ]
    }

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

    def "publish task takes into account configuration changes"() {
        given:
        buildFile << """
            apply plugin: 'maven-publish'
            ${keyInfo.addAsPropertiesScript()}

            publishing {
                publications {
                    mavenJava(MavenPublication) {
                        from components.java
                        artifactId = '$artifactId'
                    }
                }
                repositories {
                    maven {
                        name = "m2"
                        url = layout.buildDirectory.dir("m2Repo")
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
                        module = '$artifactId'
                    }
                    maven(MavenPublication) {
                        from components.java
                        artifactId = '$artifactId'
                    }
                }
                repositories {
                    maven {
                        url = layout.buildDirectory.dir("m2Repo")
                    }
                    ivy {
                        url = layout.buildDirectory.dir("ivyRepo")
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
        succeeds ":publishIvyPublicationToIvyRepository"
        then:
        skipped(":signIvyPublication")
        executedAndNotSkipped(":publishIvyPublicationToIvyRepository")
        and:
        ivyRepoFile(jarFileName).assertExists()
        ivyRepoFile("${jarFileName}.asc").assertDoesNotExist()
        ivyRepoFile("ivy-${version}.xml").assertExists()
        ivyRepoFile("ivy-${version}.xml.asc").assertDoesNotExist()

        when:
        succeeds(":publishMavenPublicationToMavenRepository")
        then:
        skipped(":signMavenPublication")
        executedAndNotSkipped(":publishMavenPublicationToMavenRepository")
        and:
        pom().assertExists()
        pomSignature().assertDoesNotExist()
        m2RepoFile(jarFileName).assertExists()
        m2RepoFile("${jarFileName}.asc").assertDoesNotExist()
    }

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
                        module = '$artifactId'
                    }
                    maven(MavenPublication) {
                        from components.java
                        artifactId = '$artifactId'
                    }
                }
                repositories {
                    maven {
                        url = layout.buildDirectory.dir("m2Repo")
                    }
                    ivy {
                        url = layout.buildDirectory.dir("ivyRepo")
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
                def m2Repo = layout.buildDirectory.dir("m2Repo").get().asFile
                def ivyRepo = layout.buildDirectory.dir("ivyRepo").get().asFile
                doLast {
                    m2Repo.deleteDir()
                    ivyRepo.deleteDir()
                }
            }
            def sign = project.getProperty("sign")
            if (sign == 'skip') {
                tasks.withType(Sign)*.onlyIf { false }
            } else {
                tasks.withType(Sign)*.enabled = Boolean.parseBoolean(sign)
            }

        """

        when:
        succeeds "publishIvyPublicationToIvyRepository", "-Psign=true"
        then:
        executedAndNotSkipped(":signIvyPublication", ":publishIvyPublicationToIvyRepository")
        and:
        ivyRepoFile(jarFileName).assertExists()
        ivyRepoFile("${jarFileName}.asc").assertExists()
        ivyRepoFile("ivy-${version}.xml").assertExists()
        ivyRepoFile("ivy-${version}.xml.asc").assertExists()

        when:
        succeeds "publishMavenPublicationToMavenRepository", "-Psign=true"
        then:
        executedAndNotSkipped(":signMavenPublication", ":publishMavenPublicationToMavenRepository")

        and:
        pom().assertExists()
        pomSignature().assertExists()
        module().assertExists()
        moduleSignature().assertExists()
        m2RepoFile(jarFileName).assertExists()
        m2RepoFile("${jarFileName}.asc").assertExists()

        when:
        succeeds "cleanRepo", "publishIvyPublicationToIvyRepository", "-Psign=false"

        then:
        skipped(":signIvyPublication")
        executedAndNotSkipped(":publishIvyPublicationToIvyRepository")
        and:
        ivyRepoFile(jarFileName).assertExists()
        ivyRepoFile("${jarFileName}.asc").assertDoesNotExist()
        ivyRepoFile("ivy-${version}.xml").assertExists()
        ivyRepoFile("ivy-${version}.xml.asc").assertDoesNotExist()

        when:
        succeeds "cleanRepo", "publishMavenPublicationToMavenRepository", "-Psign=false"
        then:
        skipped(":signMavenPublication")
        executedAndNotSkipped(":publishMavenPublicationToMavenRepository")
        and:
        pom().assertExists()
        pomSignature().assertDoesNotExist()
        module().assertExists()
        moduleSignature().assertDoesNotExist()
        m2RepoFile(jarFileName).assertExists()
        m2RepoFile("${jarFileName}.asc").assertDoesNotExist()

        when:
        succeeds "cleanRepo", "publishIvyPublicationToIvyRepository", "-Psign=skip"
        then:
        skipped(":signIvyPublication")
        executedAndNotSkipped(":publishIvyPublicationToIvyRepository")
        and:
        ivyRepoFile(jarFileName).assertExists()
        ivyRepoFile("${jarFileName}.asc").assertDoesNotExist()
        ivyRepoFile("ivy-${version}.xml").assertExists()
        ivyRepoFile("ivy-${version}.xml.asc").assertDoesNotExist()

        when:
        succeeds "cleanRepo", "publishMavenPublicationToMavenRepository", "-Psign=skip"
        then:
        skipped(":signMavenPublication")
        executedAndNotSkipped(":publishMavenPublicationToMavenRepository")
        and:
        pom().assertExists()
        pomSignature().assertDoesNotExist()
        module().assertExists()
        moduleSignature().assertDoesNotExist()
        m2RepoFile(jarFileName).assertExists()
        m2RepoFile("${jarFileName}.asc").assertDoesNotExist()
    }

    @Issue("https://github.com/gradle/gradle/issues/20166")
    def "signs single Maven publication with similar artifacts"() {
        given:
        // Two with same filename, mind the directory names
        file("res", "a", "same.txt") << "Base filename"
        file("res", "b", "same.txt") << "Base filename, different directory"
        // Third, different filename, different base directory case
        file("res", "c", "different.txt") << "Different filename"

        buildFile << """
            apply plugin: 'maven-publish'
            ${keyInfo.addAsPropertiesScript()}

            publishing {
                publications {
                    maven(MavenPublication) {
                        group = 'sign'
                        artifactId = '$artifactId'
                        version = '$version'

                        // Base filename
                        artifact("res/a/same.txt") {
                            classifier = 'a'
                            extension = 'txt'
                        }
                        // Base filename + Different classifier
                        // Original problem with #20166
                        artifact("res/b/same.txt") {
                            classifier = 'b'
                            extension = 'txt'
                        }
                        // Different filename
                        artifact("res/c/different.txt") {
                            classifier = 'c'
                            extension = 'txt'
                        }
                    }
                }
                repositories {
                    maven {
                        name = "m2"
                        url = layout.buildDirectory.dir("m2Repo")
                    }
                }
            }

            signing {
                ${signingConfiguration()}
                sign publishing.publications.maven
            }
        """

        when:
        run "publishMavenPublicationToM2Repository"

        then:
        executedAndNotSkipped(":publishMavenPublicationToM2Repository")

        and:
        m2RepoFile("$artifactId-${version}-a.txt").assertExists()
        m2RepoFile("$artifactId-${version}-a.txt.asc").assertExists()
        m2RepoFile("$artifactId-${version}-b.txt").assertExists()
        m2RepoFile("$artifactId-${version}-b.txt.asc").assertExists()
        m2RepoFile("$artifactId-${version}-c.txt").assertExists()
        m2RepoFile("$artifactId-${version}-c.txt.asc").assertExists()
    }

    @Issue([
        "https://github.com/gradle/gradle/issues/21857",
        "https://github.com/gradle/gradle/issues/22375"
    ])
    def "sign publication should be idempotent"() {
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
}
