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
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.maven.M2Installation
import org.spockframework.util.TextUtil
import spock.lang.Issue

import static org.gradle.util.TextUtil.normaliseFileSeparators

/**
 * Tests for bugfixes to maven publishing scenarios
 */
class MavenPublishIssuesIntegTest extends AbstractIntegrationSpec {

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
        TestFile m2Home = temporaryFolder.createDir("m2_home");
        M2Installation m2Installation = new M2Installation(m2Home)

        m2Installation.globalSettingsFile << """
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
        using m2Installation

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
    apply plugin: 'java'
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
        compile project(':util')
    }
"""

        file("util", "build.gradle") << """
    dependencies {
        compile 'org.gradle:dep:1.1'
    }
"""

        when:
        succeeds "publish"

        then:
        def mainPom = mavenRepo.module('my.org', 'main', '1.0').parsedPom
        mainPom.scopes.runtime.expectDependency('my.org:util:1.0')

        def utilPom = mavenRepo.module('my.org', 'util', '1.0').parsedPom
        utilPom.scopes.runtime.expectDependency('org.gradle:dep:1.1')
    }

    @Issue("GRADLE-2945")
    def "maven-publish plugin adds excludes to pom"() {

        given:
        mavenRepo.module("org.gradle", "pom-excludes", "0.1").publish()

        and:
        settingsFile << 'rootProject.name = "root"'
        buildFile << """
    apply plugin: "java"
    apply plugin: "maven-publish"

    group = "org.gradle"
    version = "1.0"

    repositories {
        maven { url "${mavenRepo.uri}" }
    }
    dependencies {
        compile ("org.gradle:pom-excludes:0.1"){
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
        def dependency = mainPom.scopes.runtime.expectDependency('org.gradle:pom-excludes:0.1')
        dependency.exclusions.size() == 3
        dependency.exclusions[0].groupId == "org.opensource1"
        dependency.exclusions[0].artifactId == "dep1"
        dependency.exclusions[1].groupId == "org.opensource2"
        dependency.exclusions[1].artifactId == "*"
        dependency.exclusions[2].groupId == "*"
        dependency.exclusions[2].artifactId == "dep2"
    }

    @Issue("GRADLE-3318")
    def "can reference rule-source tasks from sub-projects"() {
        given:
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
}
