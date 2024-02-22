/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.classpath

import org.gradle.api.internal.artifacts.ivyservice.CacheLayout
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.cache.FileAccessTimeJournalFixture
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Issue

import java.nio.file.Files
import java.util.function.Supplier
import java.util.regex.Pattern
import java.util.stream.Collectors

import static org.gradle.util.internal.TextUtil.normaliseFileSeparators

class BuildScriptClasspathInstrumentationIntegrationTest extends AbstractIntegrationSpec implements FileAccessTimeJournalFixture {

    def "buildSrc and included builds should be cached in global cache"() {
        given:
        // We test content in the global cache
        requireOwnGradleUserHomeDir()
        withBuildSrc()
        withIncludedBuild()
        buildFile << """
            buildscript {
                dependencies {
                    classpath "org.test:included"
                }
            }
        """

        when:
        run("tasks")

        then:
        gradleUserHomeOutput("original/buildSrc.jar").exists()
        gradleUserHomeOutput("instrumented/instrumented-buildSrc.jar").exists()
        gradleUserHomeOutput("original/included-1.0.jar").exists()
        gradleUserHomeOutput("instrumented/instrumented-included-1.0.jar").exists()
    }

    def "buildSrc and included build should be just instrumented and not upgraded"() {
        given:
        withBuildSrc()
        withIncludedBuild()
        buildFile << """
            buildscript {
                dependencies {
                    classpath "org.test:included"
                }
            }
        """

        when:
        run("tasks", "--info")

        then:
        allTransformsFor("buildSrc.jar") == ["ProjectDependencyInstrumentingArtifactTransform"]
        allTransformsFor("included-1.0.jar") == ["ProjectDependencyInstrumentingArtifactTransform"]
    }

    def "external dependencies should not be copied to the global artifact transform cache"() {
        given:
        // We test content in the global cache
        requireOwnGradleUserHomeDir()
        buildFile << """
            buildscript {
                ${mavenCentralRepository()}
                dependencies {
                    classpath "org.apache.commons:commons-lang3:3.8.1"
                }
            }
        """

        when:
        run("tasks", "--info")

        then:
        allTransformsFor("commons-lang3-3.8.1.jar") == ["ExternalDependencyInstrumentingArtifactTransform"]
        gradleUserHomeOutputs("original/commons-lang3-3.8.1.jar").isEmpty()
        gradleUserHomeOutput("instrumented/instrumented-commons-lang3-3.8.1.jar").exists()
    }

    def "directories should be instrumented"() {
        given:
        withIncludedBuild("first")
        withIncludedBuild("second")
        buildFile << """
            buildscript {
                dependencies {
                    classpath(files("./first/build/classes/java/main"))
                    classpath(files("./second/build/classes/java/main"))
                }
            }
        """

        when:
        executer.inDirectory(file("first")).withTasks("classes").run()
        executer.inDirectory(file("second")).withTasks("classes").run()
        run("tasks", "--info")

        then:
        allTransformsFor("main") == [
            // Only the folder name is reported, so we cannot distinguish first and second
            "ExternalDependencyInstrumentingArtifactTransform",
            "ExternalDependencyInstrumentingArtifactTransform"
        ]
    }

    def "order of entries in the effective classpath stays the same as in the original classpath"() {
        given:
        withIncludedBuild()
        mavenRepo.module("org", "commons", "3.2.1").publish()
        buildFile << """
            import java.nio.file.Paths

            buildscript {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                dependencies {
                    classpath "${first[0]}"
                    classpath "${second[0]}"
                }
            }

            Thread.currentThread().getContextClassLoader().getURLs()
                .eachWithIndex { artifact, idx -> println "classpath[\$idx]==\${Paths.get(artifact.toURI()).toFile().name}" }
        """

        when:
        run("help")

        then:
        outputContains("classpath[0]==${first[1]}")
        outputContains("classpath[1]==${second[1]}")

        where:
        first                                      | second
        ["org.test:included", "included-1.0.jar"]  | ["org:commons:3.2.1", "commons-3.2.1.jar"]
        ["org:commons:3.2.1", "commons-3.2.1.jar"] | ["org.test:included", "included-1.0.jar"]
    }

    @Issue("https://github.com/gradle/gradle/issues/28114")
    def "buildSrc can monkey patch external plugins even after instrumentation"() {
        given:
        withExternalPlugin("myPlugin", "my.plugin") {
            """throw new RuntimeException("A bug in a plugin");"""
        }
        withBuildSrc()
        file("buildSrc/src/main/java/test/gradle/MyPlugin.java") << """
            package test.gradle;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;

            public class MyPlugin implements Plugin<Project> {
                public void apply(Project project) {
                    System.out.println("MyPlugin patched from buildSrc");
                }
            }
        """
        settingsFile << """
            pluginManagement {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
            }

        """
        buildFile << """
            plugins {
                id("my.plugin") version "1.0"
            }
        """

        when:
        executer.inDirectory(file("external-plugin")).withTasks("publish").run()
        run("help")

        then:
        outputContains("MyPlugin patched from buildSrc")
    }

    def withBuildSrc() {
        file("buildSrc/src/main/java/Thing.java") << "class Thing { }"
        file("buildSrc/settings.gradle") << "\n"
    }

    def withIncludedBuild(String folderName = "included") {
        file("$folderName/src/main/java/Thing.java") << "class Thing {}"
        file("$folderName/build.gradle") << """
            plugins {
                id("java-library")
            }
            group = "org.test"
            version = "1.0"
        """
        file("$folderName/settings.gradle") << "rootProject.name = 'included'"
        settingsFile << """
            includeBuild("./$folderName")
        """
    }

    def withExternalPlugin(String name, String pluginId, Supplier<String> implementationBody = {}) {
        def implementationClass = name.capitalize()
        def folderName = "external-plugin"
        file("$folderName/build.gradle") << """
            plugins {
                id("java-gradle-plugin")
                id("maven-publish")
            }

            group = "$pluginId"
            version = "1.0"

            publishing {
                repositories {
                    maven {
                        url '${mavenRepo.uri}'
                    }
                }
            }

            gradlePlugin {
                plugins {
                    ${name} {
                        id = '${pluginId}'
                        implementationClass = 'test.gradle.$implementationClass'
                    }
                }
            }
        """
        file("$folderName/src/main/java/test/gradle/${implementationClass}.java") << """
            package test.gradle;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;

            public class $implementationClass implements Plugin<Project> {
                public void apply(Project project) {
                    ${implementationBody.get()}
                }
            }
        """
        file("$folderName/settings.gradle") << "rootProject.name = '$folderName'"
    }

    List<String> allTransformsFor(String fileName) {
        List<String> transforms = []
        def pattern = Pattern.compile("Transforming " + fileName + ".* with (.*)")
        for (def line : output.readLines()) {
            def matcher = pattern.matcher(line)
            if (matcher.matches()) {
                transforms.add(matcher.group(1))
            }
        }
        return transforms
    }

    TestFile gradleUserHomeOutput(String outputEndsWith, File cacheDir = getCacheDir()) {
        findOutput(outputEndsWith, cacheDir)
    }

    TestFile findOutput(String outputEndsWith, File cacheDir) {
        def dirs = findOutputs(outputEndsWith, cacheDir)
        if (dirs.size() == 1) {
            return dirs.first()
        }
        throw new AssertionError("Could not find exactly one output directory for $outputEndsWith: $dirs")
    }

    Set<TestFile> gradleUserHomeOutputs(String outputEndsWith, File cacheDir = getCacheDir()) {
        findOutputs(outputEndsWith, cacheDir)
    }

    Set<TestFile> findOutputs(String outputEndsWith, File cacheDir) {
        return Files.find(cacheDir.toPath(), 4, (path, attributes) -> normaliseFileSeparators(path.toString()).endsWith(outputEndsWith))
            .map { new TestFile(it.toFile()) }
            .collect(Collectors.toSet())
    }

    TestFile getCacheDir() {
        return getUserHomeCacheDir().file(CacheLayout.TRANSFORMS.getKey())
    }
}
