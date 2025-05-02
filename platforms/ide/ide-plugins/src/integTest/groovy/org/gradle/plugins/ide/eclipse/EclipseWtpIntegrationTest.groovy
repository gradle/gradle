/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.ide.eclipse

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.junit.Test
import spock.lang.Issue

class EclipseWtpIntegrationTest extends AbstractEclipseIntegrationTest {
    @Test
    @Issue("GRADLE-1415")
    @ToBeFixedForConfigurationCache
    void canUseSelfResolvingFiles() {
        def buildFile = """
apply plugin: "war"
apply plugin: "eclipse"

dependencies {
    implementation fileTree(dir: "libs", includes: ["*.jar"])
}
        """

        def libsDir = file("libs")
        libsDir.mkdir()
        libsDir.createFile("foo.jar")

        // when:
        runEclipseTask(buildFile)

        // then:
        libEntriesInClasspathFileHaveFilenames("foo.jar")
    }

    @Test
    @Issue("GRADLE-2526")
    @ToBeFixedForConfigurationCache
    void overwritesDependentModules() {
        generateEclipseFilesForWebProject()
        def projectModules = parseComponentFile(project: "web")
        assert getHandleFilenames(projectModules) == ["java1", "java2", "groovy"] as Set
        def classpath1 = classpath("web")
        classpath1.libs.size() == 2
        classpath1.lib("myartifact-1.0.jar")
        classpath1.lib("myartifactdep-1.0.jar")

        generateEclipseFilesForWebProject("1.2.3")
        def projectModules2 = parseComponentFile(project: "web")
        assert getHandleFilenames(projectModules2) == ["java1", "java2", "groovy"] as Set
        def classpath2 = classpath("web")
        classpath2.libs.size() == 2
        classpath2.lib("myartifact-1.2.3.jar")
        classpath2.lib("myartifactdep-1.0.jar")
    }

    @Test
    @ToBeFixedForConfigurationCache
    void "respects dependency substitution rules"() {
        // given:
        mavenRepo.module("gradle", "foo").publish()
        mavenRepo.module("gradle", "bar").publish()
        mavenRepo.module("gradle", "baz").publish()

        // when:
        createDirs("sub")
        runEclipseTask "include 'sub'",
        """apply plugin: 'java'
           apply plugin: 'war'
           apply plugin: 'eclipse-wtp'

           project(':sub') {
               apply plugin : 'java'
           }

           repositories {
               maven { url = "${mavenRepo.uri}" }
           }

           dependencies {
               implementation 'gradle:foo:1.0'
               implementation project(':sub')
           }

           configurations.all {
               resolutionStrategy.dependencySubstitution {
                   substitute module("gradle:foo") using module("gradle:bar:1.0")
                   substitute project(":sub") using module("gradle:baz:1.0")
               }
           }
        """

        // then:
        def classpath = getClasspath()

        classpath.assertHasLibs('bar-1.0.jar', 'baz-1.0.jar')
        classpath.lib('bar-1.0.jar').assertIsDeployedTo('/WEB-INF/lib')
        classpath.lib('baz-1.0.jar').assertIsDeployedTo('/WEB-INF/lib')
    }

    @Test
    @ToBeFixedForConfigurationCache
    void "included build"() {
        // given:
        createRootProject()
        createIncludedBuild()

        // when:
        def result = executer.withTasks("eclipse").run()

        // then:
        result.error == ""
    }

    private createIncludedBuild() {
        file("includedBuild/build.gradle.kts") << """
            plugins {
                id("java")
            }
            group = "org.example"
            version = "1.0-SNAPSHOT"

            repositories {
                mavenCentral()
            }

            dependencies {
                testImplementation(platform("org.junit:junit-bom:5.9.1"))
                testImplementation("org.junit.jupiter:junit-jupiter")
            }

            tasks.test {
                useJUnitPlatform()
            }
        """
        file("includedBuild/settings.gradle.kts") << """
            rootProject.name = "GradleEclipseWTPWithIncludedBuild_Included"
        """
    }

    private createRootProject() {
        file("build.gradle.kts") << """
            plugins {
                id("java")
                id("eclipse")
                id("ear")
            }

            group = "org.example"
            version = "1.0-SNAPSHOT"

            repositories {
                mavenCentral()
            }

            dependencies {
                implementation("org.example:GradleEclipseWTPWithIncludedBuild_Included:1.0-SNAPSHOT")
                earlib("org.example:GradleEclipseWTPWithIncludedBuild_Included:1.0-SNAPSHOT")
            }

            tasks.test {
                useJUnitPlatform()
            }
        """
        file("settings.gradle.kts") << """
            rootProject.name = "GradleEclipseWTPWithIncludedBuild"

            includeBuild("includedBuild")
        """
        file("src/main/java/org/example/Main.java") << """
            package org.example;

            public class Main {
                public static void main(String[] args) {
                    System.out.println("Hello World!");
                }
            }
        """
    }

    private generateEclipseFilesForWebProject(myArtifactVersion = "1.0") {
        def repoDir = file("repo")
        maven(repoDir).module("mygroup", "myartifact", myArtifactVersion).dependsOnModules("myartifactdep").publish()
        maven(repoDir).module("mygroup", "myartifactdep").publish()

        file("settings.gradle") << """
include("web")
include("java1")
include("java2")
include("groovy")
        """

        def webBuildFile = getFile(project: "web", "build.gradle")
        createJavaSourceDirs(webBuildFile)
        webBuildFile.parentFile.file("src/main/webapp").createDir()

        webBuildFile << """
apply plugin: "eclipse-wtp"
apply plugin: "war"

repositories {
    maven { url = "${repoDir.toURI()}" }
}

dependencies {
    implementation project(":java1")
    implementation project(":groovy")
    runtimeOnly "mygroup:myartifact:$myArtifactVersion"
}
        """

        def java1BuildFile = getFile(project: "java1", "build.gradle")
        createJavaSourceDirs(java1BuildFile)

        java1BuildFile << """
apply plugin: "eclipse-wtp"
apply plugin: "java"

repositories {
    maven { url = "${repoDir.toURI()}" }
}

dependencies {
    implementation project(":java2")
    runtimeOnly "mygroup:myartifact:$myArtifactVersion"
}
        """

        def java2BuildFile = getFile(project: "java2", "build.gradle")
        createJavaSourceDirs(java2BuildFile)

        java2BuildFile << """
apply plugin: "eclipse-wtp"
apply plugin: "java"

repositories {
    maven { url = "${repoDir.toURI()}" }
}

dependencies {
    runtimeOnly "mygroup:myartifact:$myArtifactVersion"
}
        """

        def groovyBuildFile = getFile(project: "groovy", "build.gradle")
        createJavaSourceDirs(groovyBuildFile)
        groovyBuildFile.parentFile.file("src/main/groovy").createDir()

        groovyBuildFile << """
apply plugin: "eclipse-wtp"
apply plugin: "groovy"
        """

        executer.withTasks("eclipse").run()
    }

	private static Set getHandleFilenames(projectModules) {
		projectModules."wb-module"."dependent-module".@handle*.text().collect { it.substring(it.lastIndexOf("/") + 1) } as Set
	}
}
