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

import org.gradle.api.Action
import org.gradle.api.internal.artifacts.ivyservice.CacheLayout
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.cache.FileAccessTimeJournalFixture
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import spock.lang.Issue

import java.nio.file.Files
import java.util.function.Supplier
import java.util.regex.Pattern
import java.util.stream.Collectors

import static org.gradle.api.internal.initialization.transform.services.CacheInstrumentationDataBuildService.GENERATE_CLASS_HIERARCHY_WITHOUT_UPGRADES_PROPERTY
import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.ANALYSIS_OUTPUT_DIR
import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.DEPENDENCIES_FILE_NAME
import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.DEPENDENCIES_SUPER_TYPES_FILE_NAME
import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.FILE_NAME_PROPERTY_NAME
import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.MERGE_OUTPUT_DIR
import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.METADATA_FILE_NAME
import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.SUPER_TYPES_FILE_NAME
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
        allTransformsFor("commons-lang3-3.8.1.jar") ==~ ["InstrumentationAnalysisTransform", "MergeInstrumentationAnalysisTransform", "ExternalDependencyInstrumentingArtifactTransform"]
        gradleUserHomeOutputs("original/commons-lang3-3.8.1.jar").isEmpty()
        gradleUserHomeOutput("instrumented/instrumented-commons-lang3-3.8.1.jar").exists()
    }

    def "directories should be instrumented"() {
        given:
        requireOwnGradleUserHomeDir()
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
        allTransformsFor("main") ==~ [
            // Only the folder name is reported, so we cannot distinguish first and second
            "InstrumentationAnalysisTransform",
            "MergeInstrumentationAnalysisTransform",
            "ExternalDependencyInstrumentingArtifactTransform",
            "InstrumentationAnalysisTransform",
            "MergeInstrumentationAnalysisTransform",
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

    def "classpath can contain non-existing file"() {
        given:
        executer.requireOwnGradleUserHomeDir()
        buildFile << """
            buildscript { dependencies { classpath files("does-not-exist.jar") } }
        """

        when:
        succeeds()

        then:
        noExceptionThrown()
    }

    def "should analyze plugin artifacts"() {
        given:
        // We test content in the global cache
        requireOwnGradleUserHomeDir()
        multiProjectJavaBuild("subproject", "api", "animals") {
            file("$it/api/src/main/java/org/gradle/api/Plugin.java") << "package org.gradle.api; public interface Plugin {}"
            file("$it/api/src/main/java/org/gradle/api/Task.java") << "package org.gradle.api; public interface Task {}"
            file("$it/api/src/main/java/org/gradle/api/DefaultTask.java") << "package org.gradle.api; public class DefaultTask implements Task {}"
            file("$it/api/src/main/java/org/gradle/api/GradleException.java") << "package org.gradle.api; public class GradleException {}"
            file("$it/animals/src/main/java/test/gradle/test/Dogs.java").createFile().text = """
                package test.gradle.test;
                import org.gradle.api.*;

                class GermanShepherd extends Dog implements Animal {
                    public void plugin() {
                        ((Plugin) null).toString();
                        new GradleException();
                    }
                }
                abstract class Dog implements Mammal {
                    public void task() {
                        ((Task) null).toString();
                        ((DefaultTask) null).toString();
                    }
                }
                interface Mammal extends Animal {
                }
                interface Animal {
                }
            """
        }
        executer.inDirectory(file("subproject")).withTasks("jar").run()
        buildFile << """
            buildscript {
                 repositories {
                    maven { url "$mavenRepo.uri" }
                }
                dependencies {
                    classpath(files("./subproject/animals/build/libs/animals-1.0.jar"))
                }
            }
        """

        when:
        run("tasks", "--info")

        then:
        allTransformsFor("animals-1.0.jar") ==~ ["InstrumentationAnalysisTransform", "MergeInstrumentationAnalysisTransform", "ExternalDependencyInstrumentingArtifactTransform"]
        def analyzeDir = analyzeOutput("animals-1.0.jar")
        analyzeDir.exists()
        analyzeDir.file(SUPER_TYPES_FILE_NAME).readLines() == [
            "test/gradle/test/Dog=test/gradle/test/Mammal",
            "test/gradle/test/GermanShepherd=test/gradle/test/Animal,test/gradle/test/Dog",
            "test/gradle/test/Mammal=test/gradle/test/Animal"
        ]
        analyzeDir.file(DEPENDENCIES_FILE_NAME).readLines() == [
            "org/gradle/api/DefaultTask",
            "org/gradle/api/GradleException",
            "org/gradle/api/Plugin",
            "org/gradle/api/Task",
            "test/gradle/test/Animal",
            "test/gradle/test/Dog",
            "test/gradle/test/Mammal"
        ]
    }

    def "should output only org.gradle supertypes for class dependencies"() {
        given:
        requireOwnGradleUserHomeDir()
        multiProjectJavaBuild("subproject") {
            file("$it/impl/src/main/java/A.java") << "public class A extends B {}"
            file("$it/api/src/main/java/B.java") << "public class B extends C {}"
            file("$it/api/src/main/java/C.java") << "import org.gradle.D; public class C extends D {}"
            file("$it/api/src/main/java/org/gradle/D.java") << "package org.gradle; public class D extends E {}"
            file("$it/api/src/main/java/org/gradle/E.java") << "package org.gradle; public class E {}"
        }
        buildFile << """
            buildscript {
                dependencies {
                    // Add them as separate jars
                    classpath(files("./subproject/impl/build/libs/impl-1.0.jar"))
                    classpath(files("./subproject/api/build/libs/api-1.0.jar"))
                }
            }
        """

        when:
        executer.inDirectory(file("subproject")).withTasks("jar").run()
        run("tasks", "-D$GENERATE_CLASS_HIERARCHY_WITHOUT_UPGRADES_PROPERTY=true")

        then:
        mergeOutput("impl-1.0.jar").exists()
        mergeOutput("api-1.0.jar").exists()
        def implMergeDir = mergeOutput("impl-1.0.jar")
        implMergeDir.file(DEPENDENCIES_SUPER_TYPES_FILE_NAME).readLines() == [
            "B=org/gradle/D,org/gradle/E",
        ]
        def apiMergeDir = mergeOutput("api-1.0.jar")
        apiMergeDir.file(DEPENDENCIES_SUPER_TYPES_FILE_NAME).readLines() == [
            "C=org/gradle/D,org/gradle/E",
            "org/gradle/D=org/gradle/D,org/gradle/E",
            "org/gradle/E=org/gradle/E"
        ]
    }

    @Requires(
        value = IntegTestPreconditions.NotConfigCached,
        reason = "Cc doesn't get invalidated when file dependency changes"
    )
    def "should re-instrument jar if classpath changes and class starts extending a Gradle core class transitively"() {
        given:
        requireOwnGradleUserHomeDir()
        multiProjectJavaBuild("subproject") {
            file("$it/impl/src/main/java/A.java") << "public class A extends B {}"
            file("$it/api/src/main/java/B.java") << "public class B {}"
        }
        buildFile << """
            buildscript {
                dependencies {
                    // Add them as separate jars
                    classpath(files("./subproject/impl/build/libs/impl-1.0.jar"))
                    classpath(files("./subproject/api/build/libs/api-1.0.jar"))
                }
            }
        """

        when:
        executer.inDirectory(file("subproject")).withTasks("jar").run()
        run("tasks", "-D$GENERATE_CLASS_HIERARCHY_WITHOUT_UPGRADES_PROPERTY=true")

        then:
        gradleUserHomeOutputs("instrumented/instrumented-api-1.0.jar").size() == 1
        gradleUserHomeOutputs("instrumented/instrumented-impl-1.0.jar").size() == 1

        when:
        file("subproject/api/src/main/java/B.java").text = "import org.gradle.C; public class B extends C {}"
        file("subproject/api/src/main/java/org/gradle/C.java") << "package org.gradle; public class C {}"
        executer.inDirectory(file("subproject")).withTasks("jar").run()
        run("tasks", "-D$GENERATE_CLASS_HIERARCHY_WITHOUT_UPGRADES_PROPERTY=true")

        then:
        gradleUserHomeOutputs("instrumented/instrumented-api-1.0.jar").size() == 2
        gradleUserHomeOutputs("instrumented/instrumented-impl-1.0.jar").size() == 2
    }

    @Requires(
        value = IntegTestPreconditions.NotConfigCached,
        reason = "Cc doesn't get invalidated when file dependency changes"
    )
    def "should not re-instrument jar if classpath changes but class doesn't extend Gradle core class"() {
        given:
        requireOwnGradleUserHomeDir()
        multiProjectJavaBuild("subproject") {
            file("$it/impl/src/main/java/A.java") << "public class A extends B {}"
            file("$it/api/src/main/java/B.java") << "public class B {}"
        }
        buildFile << """
            buildscript {
                dependencies {
                    // Add them as separate jars
                    classpath(files("./subproject/impl/build/libs/impl-1.0.jar"))
                    classpath(files("./subproject/api/build/libs/api-1.0.jar"))
                }
            }
        """

        when:
        executer.inDirectory(file("subproject")).withTasks("jar").run()
        run("tasks", "-D$GENERATE_CLASS_HIERARCHY_WITHOUT_UPGRADES_PROPERTY=true")

        then:
        gradleUserHomeOutputs("instrumented/instrumented-api-1.0.jar").size() == 1
        gradleUserHomeOutputs("instrumented/instrumented-impl-1.0.jar").size() == 1

        when:
        file("subproject/api/src/main/java/B.java").text = "public class B extends C {}"
        file("subproject/api/src/main/java/C.java") << "public class C {}"
        executer.inDirectory(file("subproject")).withTasks("jar").run()
        run("tasks", "-D$GENERATE_CLASS_HIERARCHY_WITHOUT_UPGRADES_PROPERTY=true")

        then:
        gradleUserHomeOutputs("instrumented/instrumented-api-1.0.jar").size() == 2
        gradleUserHomeOutputs("instrumented/instrumented-impl-1.0.jar").size() == 1
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

    def javaBuild(String projectName = "included", Action<String> init) {
        file("$projectName/build.gradle") << """
            plugins {
                id("java-library")
            }
            group = "org.test"
            version = "1.0"
        """
        file("$projectName/settings.gradle") << """
            rootProject.name = '$projectName'
        """
        init(projectName)
    }

    def multiProjectJavaBuild(String projectName = "included", Action<String> init) {
        multiProjectJavaBuild(projectName, "api", "impl", init)
    }

    def multiProjectJavaBuild(String rootName = "included", String apiProjectName, String implProjectName, Action<String> init) {
        file("$rootName/$apiProjectName/build.gradle") << """
            plugins {
                id("java-library")
            }
            group = "org.test"
            version = "1.0"
        """
        file("$rootName/$implProjectName/build.gradle") << """
            plugins {
                id("java-library")
            }
            group = "org.test"
            version = "1.0"

            dependencies {
                implementation project(":$apiProjectName")
            }
        """
        file("$rootName/settings.gradle") << """
            rootProject.name = '$rootName'
            include("$apiProjectName", "$implProjectName")
        """
        init(rootName)
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

    Set<TestFile> analyzeOutputs(String artifactName, File cacheDir = getCacheDir()) {
        return findOutputs("$ANALYSIS_OUTPUT_DIR/$METADATA_FILE_NAME", cacheDir).findAll {
            it.text.contains("$FILE_NAME_PROPERTY_NAME=$artifactName")
        }.collect { it.parentFile } as Set<TestFile>
    }

    TestFile analyzeOutput(String artifactName, File cacheDir = getCacheDir()) {
        def dirs = analyzeOutputs(artifactName, cacheDir)
        if (dirs.size() == 1) {
            return dirs.first()
        }
        throw new AssertionError("Could not find exactly one analyze directory for $artifactName: $dirs")
    }

    Set<TestFile> mergeOutputs(String artifactName, File cacheDir = getCacheDir()) {
        return findOutputs("$MERGE_OUTPUT_DIR/$METADATA_FILE_NAME", cacheDir).findAll {
            it.text.contains("$FILE_NAME_PROPERTY_NAME=$artifactName")
        }.collect { it.parentFile } as Set<TestFile>
    }

    TestFile mergeOutput(String artifactName, File cacheDir = getCacheDir()) {
        def dirs = mergeOutputs(artifactName, cacheDir)
        if (dirs.size() == 1) {
            return dirs.first()
        }
        throw new AssertionError("Could not find exactly one merge directory for $artifactName: $dirs")
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
