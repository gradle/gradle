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

import org.gradle.integtests.fixtures.publish.maven.AbstractMavenPublishIntegTest
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.maven.MavenFileModule
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.spockframework.util.TextUtil
import spock.lang.Issue

import static org.gradle.util.internal.TextUtil.normaliseFileSeparators

/**
 * Tests for bugfixes to maven publishing scenarios
 */
class MavenPublishIssuesIntegTest extends AbstractMavenPublishIntegTest {

    @Issue("GRADLE-2456")
    def "generates SHA1 file with leading zeros"() {
        given:
        def module = mavenRepo.module("org.gradle", "publish", "2")
        byte[] jarBytes = [0, 0, 0, 5]
        def artifactFile = file("testfile.bin")
        artifactFile << jarBytes
        def artifactPath = TextUtil.escape(artifactFile.path)

        and:
        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
    apply plugin: 'maven-publish'

    group = "org.gradle"
    version = '2'

    publishing {
        repositories {
            maven { url "${mavenRepo.uri}" }
        }
        publications {
            pub(MavenPublication) {
                artifact file("${artifactPath}")
            }
        }
    }
    """

        when:
        succeeds 'publish'

        then:
        def shaOneFile = module.moduleDir.file("publish-2.bin.sha1")
        shaOneFile.exists()
        shaOneFile.text == "00e14c6ef59816760e2c9b5a57157e8ac9de4012"
    }

    @Issue("GRADLE-2681")
    def "gradle ignores maven mirror configuration for uploading archives"() {
        given:
        m2.globalSettingsFile << """
<settings>
  <mirrors>
    <mirror>
      <id>ACME</id>
      <name>ACME Central</name>
      <url>http://acme.maven.org/maven2</url>
      <mirrorOf>*</mirrorOf>
    </mirror>
  </mirrors>
</settings>
"""

        and:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
apply plugin: 'java'
apply plugin: 'maven-publish'
group = 'group'
version = '1.0'

publishing {
    repositories {
        maven { url "${mavenRepo.uri}" }
    }
    publications {
        maven(MavenPublication) {
            from components.java
        }
    }
}
   """
        when:
        using m2

        then:
        succeeds "publish"
    }

    @Issue("GRADLE-2837")
    def "project is properly configured when it is the target of a project dependency"() {
        given:
        mavenRepo.module("org.gradle", "dep", "1.1").publish()

        and:
        settingsFile << "include ':main', ':util'"

        buildFile << """
subprojects {
    apply plugin: 'java-library'
    apply plugin: 'maven-publish'
    group = 'my.org'
    version = '1.0'
    repositories {
        maven { url "${mavenRepo.uri}" }
    }
    publishing {
        repositories {
            maven { url "${mavenRepo.uri}" }
        }
        publications {
            mavenJava(MavenPublication) {
                from components.java
            }
        }
    }
}
"""
        file("main", "build.gradle") << """
    dependencies {
        api project(':util')
    }
"""

        file("util", "build.gradle") << """
    dependencies {
        api 'org.gradle:dep:1.1'
    }
"""

        when:
        succeeds "publish"

        then:
        def mainPom = mavenRepo.module('my.org', 'main', '1.0').parsedPom
        mainPom.scopes.compile.expectDependency('my.org:util:1.0')

        def utilPom = mavenRepo.module('my.org', 'util', '1.0').parsedPom
        utilPom.scopes.compile.expectDependency('org.gradle:dep:1.1')
    }

    @Issue("GRADLE-2945")
    def "maven-publish plugin adds excludes to pom"() {

        given:
        mavenRepo.module("org.gradle", "pom-excludes", "0.1").publish()

        and:
        settingsFile << 'rootProject.name = "root"'
        buildFile << """
    apply plugin: "java-library"
    apply plugin: "maven-publish"

    group = "org.gradle"
    version = "1.0"

    repositories {
        maven { url "${mavenRepo.uri}" }
    }
    dependencies {
        api("org.gradle:pom-excludes:0.1"){
           exclude group: "org.opensource1", module: "dep1"
           exclude group: "org.opensource2"
           exclude module: "dep2"
        }
    }
    publishing {
        repositories {
            maven { url "${mavenRepo.uri}" }
        }
        publications {
            pub(MavenPublication) {
                from components.java
            }
        }
    }
    """

        when:
        succeeds 'publish'

        then:
        def mainPom = mavenRepo.module('org.gradle', 'root', '1.0').parsedPom
        def dependency = mainPom.scopes.compile.expectDependency('org.gradle:pom-excludes:0.1')
        dependency.exclusions.size() == 3
        def sorted = dependency.exclusions.sort { it.groupId }
        sorted[0].groupId == "*"
        sorted[0].artifactId == "dep2"
        sorted[1].groupId == "org.opensource1"
        sorted[1].artifactId == "dep1"
        sorted[2].groupId == "org.opensource2"
        sorted[2].artifactId == "*"

    }

    @Issue("GRADLE-3318")
    def "can reference rule-source tasks from sub-projects"() {
        given:
        using m2
        def repo = file("maven").createDir()
        settingsFile << """
        include 'sub1'
        include 'sub2'
        """

        [file("sub1/build.gradle"), file("sub2/build.gradle")].each { File f ->
            f << """
            apply plugin: "java"
            apply plugin: "maven-publish"

            publishing {
                repositories{ maven{ url '${normaliseFileSeparators(repo.getAbsolutePath())}'}}
                publications {
                    maven(MavenPublication) {
                        groupId 'org.gradle.sample'
                        version '1.1'
                        from components.java
                    }
                }
            }"""
        }

        buildFile << """
        apply plugin: "maven-publish"

        task customPublish(dependsOn: subprojects.collect { Project p -> p.tasks.withType(PublishToMavenLocal)})"""
        when:
        succeeds('customPublish')

        then:
        output.contains(":sub1:generatePomFileForMavenPublication")
        output.contains(":sub1:publishMavenPublicationToMavenLocal")
        output.contains(":sub2:generatePomFileForMavenPublication")
        output.contains(":sub2:publishMavenPublicationToMavenLocal")
        output.contains(":customPublish")
    }

    @Issue("https://github.com/gradle/gradle/issues/5136")
    void "doesn't publish if main artifact is missing"() {
        settingsFile << 'rootProject.name = "test"'
        buildFile << """
            apply plugin: "java-library"
            apply plugin: "maven-publish"

            group = "org.gradle"
            version = "1.0"

            jar {
                enabled = Boolean.parseBoolean(project.getProperty("jarEnabled"))
            }

            publishing {
                repositories {
                    maven { url "\${buildDir}/repo" }
                }
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
        """
        file("src/main/java/hello/Hello.java") << """package hello;
            public class Hello {}
        """

        when:
        succeeds "publish", "-PjarEnabled=true"

        then:
        file("build/repo/org/gradle/test/1.0/test-1.0.jar").exists()
        file("build/repo/org/gradle/test/1.0/test-1.0.jar.md5").exists()
        file("build/repo/org/gradle/test/1.0/test-1.0.jar.sha1").exists()
        file("build/repo/org/gradle/test/1.0/test-1.0.jar.sha256").exists()
        file("build/repo/org/gradle/test/1.0/test-1.0.jar.sha512").exists()

        when:
        fails "publish", "-PjarEnabled=false"

        then:
        skipped(":jar")
        failure.assertHasCause("Artifact test-1.0.jar wasn't produced by this build.")
    }

    @Issue("https://github.com/gradle/gradle/issues/5136")
    void "doesn't publish stale files"() {
        MavenFileModule publishedModule

        settingsFile << 'rootProject.name = "test"'
        buildFile << """
            apply plugin: "java-library"
            apply plugin: "maven-publish"

            group = "org.gradle"
            version = "1.0"

            java {
                withJavadocJar()
            }

            javadocJar {
                enabled = Boolean.parseBoolean(project.getProperty("javadocEnabled"))
            }

            publishing {
                repositories {
                    maven { url "\${buildDir}/repo" }
                }
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
        """
        file("src/main/java/hello/Hello.java") << """package hello;
            public class Hello {}
        """

        when:
        succeeds "publish", "-PjavadocEnabled=true"
        publishedModule = new MavenFileRepository(new TestFile(file("build/repo"))).module("org.gradle", "test")

        then:
        file("build/repo/org/gradle/test/1.0/test-1.0-javadoc.jar").exists()
        file("build/repo/org/gradle/test/1.0/test-1.0-javadoc.jar.md5").exists()
        file("build/repo/org/gradle/test/1.0/test-1.0-javadoc.jar.sha1").exists()
        file("build/repo/org/gradle/test/1.0/test-1.0-javadoc.jar.sha256").exists()
        file("build/repo/org/gradle/test/1.0/test-1.0-javadoc.jar.sha512").exists()
        publishedModule.parsedModuleMetadata.variant("javadocElements") {
            assert files*.name == ['test-1.0-javadoc.jar']
        }

        when:
        file("build/repo").deleteDir()
        succeeds "publish", "-PjavadocEnabled=false"
        publishedModule = new MavenFileRepository(new TestFile(file("build/repo"))).module("org.gradle", "test")

        then:
        skipped(":javadocJar")
        !file("build/repo/org/gradle/test/1.0/test-1.0-javadoc.jar").exists()
        !file("build/repo/org/gradle/test/1.0/test-1.0-javadoc.jar.md5").exists()
        !file("build/repo/org/gradle/test/1.0/test-1.0-javadoc.jar.sha1").exists()
        !file("build/repo/org/gradle/test/1.0/test-1.0-javadoc.jar.sha256").exists()
        !file("build/repo/org/gradle/test/1.0/test-1.0-javadoc.jar.sha512").exists()
        publishedModule.parsedModuleMetadata.variant("javadocElements") {
            assert files*.name == []
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/20581")
    void "warn deprecated behavior when GMM is modified after a Maven publication is populated"() {
        given:
        buildKotlinFile << """
             plugins {
                java
                `maven-publish`
                kotlin("jvm") version "1.7.21"
            }
            repositories {
                mavenCentral()
            }
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
            }
            publishing {
                publications {
                    create<MavenPublication>("maven") {
                        from(components["java"])
                    }
                }
            }
            (publishing.publications["maven"] as MavenPublication).artifacts
            (components["java"] as org.gradle.api.plugins.internal.DefaultAdhocSoftwareComponent).apply {
                withVariantsFromConfiguration(configurations["apiElements"]) { skip() }
            }
        """

        when:
        executer.expectDocumentedDeprecationWarning(
            "Gradle Module Metadata is modified after an eagerly populated publication. " +
                "This behavior has been deprecated. This will fail with an error in Gradle 9.0. " +
                "Consult the upgrading guide for further information: " +
                "https://docs.gradle.org/current/userguide/upgrading_version_8.html#gmm_modification_after_publication_populated"
        )

        then:
        succeeds "help"
    }

    @Issue("https://github.com/gradle/gradle/issues/20581")
    void "warn deprecated behavior when GMM is modified after an Ivy publication is populated"() {
        given:
        buildKotlinFile << """
             plugins {
                java
                `ivy-publish`
                kotlin("jvm") version "1.7.21"
            }
            repositories {
                mavenCentral()
            }
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
            }
            publishing {
                publications {
                    create<IvyPublication>("ivy") {
                        from(components["java"])
                    }
                }
            }
            (publishing.publications["ivy"] as IvyPublication).artifacts
            (components["java"] as org.gradle.api.plugins.internal.DefaultAdhocSoftwareComponent).apply {
                withVariantsFromConfiguration(configurations["apiElements"]) { skip() }
            }
        """

        when:
        executer.expectDocumentedDeprecationWarning(
            "Gradle Module Metadata is modified after an eagerly populated publication. " +
                "This behavior has been deprecated. This will fail with an error in Gradle 9.0. " +
                "Consult the upgrading guide for further information: " +
                "https://docs.gradle.org/current/userguide/upgrading_version_8.html#gmm_modification_after_publication_populated"
        )

        then:
        succeeds "help"
    }
}
