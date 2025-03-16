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
import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.initialization.transform.utils.DefaultInstrumentationAnalysisSerializer
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.cache.FileAccessTimeJournalFixture
import org.gradle.operations.execution.ExecuteWorkBuildOperationType
import org.gradle.test.fixtures.HttpRepository
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.gradle.test.fixtures.server.http.RepositoryHttpServer
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.util.internal.GFileUtils
import org.junit.Rule
import spock.lang.Issue

import java.nio.file.Files
import java.util.function.Supplier
import java.util.regex.Pattern
import java.util.stream.Collectors

import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.ANALYSIS_OUTPUT_DIR
import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.DEPENDENCY_ANALYSIS_FILE_NAME
import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.MERGE_OUTPUT_DIR
import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.TYPE_HIERARCHY_ANALYSIS_FILE_NAME
import static org.gradle.util.internal.TextUtil.normaliseFileSeparators

class BuildScriptClasspathInstrumentationIntegrationTest extends AbstractIntegrationSpec implements FileAccessTimeJournalFixture {

    @Rule
    public final RepositoryHttpServer server = new RepositoryHttpServer(temporaryFolder)
    def buildOperations = new BuildOperationsFixture(executer, testDirectoryProvider)

    def serializer = new DefaultInstrumentationAnalysisSerializer(new StringInterner())

    def setup() {
        requireOwnGradleUserHomeDir("We test content in the global cache")
    }

    def "buildSrc and included builds should be cached in global cache"() {
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
        run("tasks")

        then:
        allTransformsFor("buildSrc.jar") == ["ProjectDependencyInstrumentingArtifactTransform"]
        allTransformsFor("included-1.0.jar") == ["ProjectDependencyInstrumentingArtifactTransform"]
    }

    def "external dependencies should not be copied to the global artifact transform cache"() {
        given:
        buildFile << """
            buildscript {
                ${mavenCentralRepository()}
                dependencies {
                    classpath "org.apache.commons:commons-lang3:3.8.1"
                }
            }
        """

        when:
        run("tasks")

        then:
        allTransformsFor("commons-lang3-3.8.1.jar") ==~ ["InstrumentationAnalysisTransform", "MergeInstrumentationAnalysisTransform", "ExternalDependencyInstrumentingArtifactTransform"]
        gradleUserHomeOutputs("original/commons-lang3-3.8.1.jar").isEmpty()
        gradleUserHomeOutput("instrumented/instrumented-commons-lang3-3.8.1.jar").exists()
    }

    def "directories should be instrumented"() {
        given:
        withIncludedBuild("first")
        withIncludedBuild("second")
        // We need to create different classes,
        // since jar with the same content will be instrumented only once
        file("first/src/main/java/First.java").text = "class First { }"
        file("second/src/main/java/Second.java").text = "class Second { }"
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
        run("tasks")

        then:
        allTransformsFor("first/build/classes/java/main") ==~ [
            "InstrumentationAnalysisTransform",
            "MergeInstrumentationAnalysisTransform",
            "ExternalDependencyInstrumentingArtifactTransform"
        ]
        allTransformsFor("second/build/classes/java/main") ==~ [
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
                    maven { url = "${mavenRepo.uri}" }
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
                    maven { url = "${mavenRepo.uri}" }
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
                    maven { url = "$mavenRepo.uri" }
                }
                dependencies {
                    classpath(files("./subproject/animals/build/libs/animals-1.0.jar"))
                }
            }
        """

        when:
        run("tasks")

        then:
        allTransformsFor("animals-1.0.jar") ==~ ["InstrumentationAnalysisTransform", "MergeInstrumentationAnalysisTransform", "ExternalDependencyInstrumentingArtifactTransform"]
        def typeHierarchyAnalysis = typeHierarchyAnalysisOutput("animals-1.0.jar")
        typeHierarchyAnalysis.exists()
        serializer.readTypeHierarchyAnalysis(typeHierarchyAnalysis) == [
            "test/gradle/test/Dog": ["test/gradle/test/Mammal"] as Set<String>,
            "test/gradle/test/GermanShepherd": ["test/gradle/test/Animal", "test/gradle/test/Dog"] as Set<String>,
            "test/gradle/test/Mammal": ["test/gradle/test/Animal"] as Set<String>
        ]
        def dependencyAnalysis = dependencyAnalysisOutput("animals-1.0.jar")
        dependencyAnalysis.exists()
        serializer.readDependencyAnalysis(dependencyAnalysis).dependencies == [
            "org/gradle/api/DefaultTask": [] as Set<String>,
            "org/gradle/api/GradleException": [] as Set<String>,
            "org/gradle/api/Plugin": [] as Set<String>,
            "org/gradle/api/Task": [] as Set<String>,
            "test/gradle/test/Animal": [] as Set<String>,
            "test/gradle/test/Dog": [] as Set<String>,
            "test/gradle/test/Mammal": [] as Set<String>,
        ]
    }

    def "should output only org.gradle supertypes for class dependencies"() {
        given:
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
        run("tasks")

        then:
        mergeAnalysisOutput("impl-1.0.jar").exists()
        mergeAnalysisOutput("api-1.0.jar").exists()
        def implMergeAnalysis = mergeAnalysisOutput("impl-1.0.jar")
        serializer.readDependencyAnalysis(implMergeAnalysis).dependencies == [
            "B": ["org/gradle/D", "org/gradle/E"] as Set
        ]
        def apiMergeAnalysis = mergeAnalysisOutput("api-1.0.jar")
        serializer.readDependencyAnalysis(apiMergeAnalysis).dependencies == [
            "C": ["org/gradle/D", "org/gradle/E"] as Set,
            "org/gradle/D": ["org/gradle/D", "org/gradle/E"] as Set,
            "org/gradle/E": ["org/gradle/E"] as Set
        ]
    }

    @Requires(
        value = IntegTestPreconditions.NotConfigCached,
        reason = "Cc doesn't get invalidated when file dependency changes"
    )
    def "should re-instrument jar if classpath changes and class starts extending a Gradle core class transitively"() {
        given:
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
        run("tasks")

        then:
        gradleUserHomeOutputs("instrumented/instrumented-api-1.0.jar").size() == 1
        gradleUserHomeOutputs("instrumented/instrumented-impl-1.0.jar").size() == 1

        when:
        file("subproject/api/src/main/java/B.java").text = "import org.gradle.C; public class B extends C {}"
        file("subproject/api/src/main/java/org/gradle/C.java") << "package org.gradle; public class C {}"
        executer.inDirectory(file("subproject")).withTasks("jar").run()
        run("tasks")

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
        run("tasks")

        then:
        gradleUserHomeOutputs("instrumented/instrumented-api-1.0.jar").size() == 1
        gradleUserHomeOutputs("instrumented/instrumented-impl-1.0.jar").size() == 1

        when:
        file("subproject/api/src/main/java/B.java").text = "public class B extends C {}"
        file("subproject/api/src/main/java/C.java") << "public class C {}"
        executer.inDirectory(file("subproject")).withTasks("jar").run()
        run("tasks")

        then:
        gradleUserHomeOutputs("instrumented/instrumented-api-1.0.jar").size() == 2
        gradleUserHomeOutputs("instrumented/instrumented-impl-1.0.jar").size() == 1
    }

    @Issue("https://github.com/gradle/gradle/issues/28301")
    def "instrumentation and upgrades don't fail a build when repository is changed from remote to local"() {
        def mavenRemote = new MavenHttpRepository(server, "/repo", HttpRepository.MetadataType.DEFAULT, mavenRepo)
        def remoteModule = mavenRemote.module("test.gradle", "test-plugin", "0.2").publish()
        remoteModule.pom.expectDownload()
        remoteModule.artifact.expectDownload()
        def artifactCoordinates = "${remoteModule.groupId}:${remoteModule.artifactId}:${remoteModule.version}"

        when:
        buildFile.text = """
            buildscript {
                repositories { maven { url = "${mavenRemote.uri}" } }
                dependencies { classpath "$artifactCoordinates" }
            }
        """

        then:
        run("help")

        when:
        buildFile.text = """
            buildscript {
                repositories { maven { url = "${normaliseFileSeparators(mavenRepo.uri.toString())}" } }
                dependencies { classpath "$artifactCoordinates" }
            }
        """

        then:
        run("help")
    }

    @Issue("https://github.com/gradle/gradle/issues/28496")
    def "instrumentation and upgrades don't fail a build when we have two copies of the same artifact on the classpath"() {
        file("jars/first").mkdirs()
        file("jars/second").mkdirs()
        def jar = file("jars/first/test-plugin-0.2.jar").with {
            it.createFile()
        }
        GFileUtils.copyFile(jar, file("jars/second/test-plugin-0.2.jar"))

        when:
        buildFile.text = """
            buildscript {
                dependencies {
                    classpath(files("./jars/first/test-plugin-0.2.jar"))
                    classpath(files("./jars/second/test-plugin-0.2.jar"))
                }
            }
        """

        then:
        run("help")
    }

    def "external dependencies merge analysis is #mergeRun if type hierarchy #typeHierachyChange for local project"() {
        given:
        withIncludedBuild()
        file("included/src/main/java/Foo.java") << "class Foo {}"
        file("included/src/main/java/Bar.java") << "class Bar {}"
        buildFile << """
            import java.nio.file.Paths

            buildscript {
                repositories {
                    ${mavenCentralRepository()}
                }
                dependencies {
                    classpath "org.test:included"
                    classpath "org.apache.commons:commons-lang3:3.8.1"
                }
            }
        """

        when:
        run("tasks")

        then:
        typeHierarchyAnalysisOutputs("commons-lang3-3.8.1.jar").size() == 1
        dependencyAnalysisOutputs("commons-lang3-3.8.1.jar").size() == 1
        mergeAnalysisOutputs("commons-lang3-3.8.1.jar").size() == 1

        when:
        file("included/src/main/java/Bar.java").text = barContentOnChange
        run("tasks")

        then:
        typeHierarchyAnalysisOutputs("commons-lang3-3.8.1.jar").size() == 1
        dependencyAnalysisOutputs("commons-lang3-3.8.1.jar").size() == 1
        mergeAnalysisOutputs("commons-lang3-3.8.1.jar").size() == expectedFinalMergeAnalysisOutputs

        where:
        mergeRun     | typeHierachyChange | barContentOnChange                   | expectedFinalMergeAnalysisOutputs
        "re-run"     | "is changed"       | "class Bar extends Foo {}"           | 2
        "not re-run" | "is not changed"   | "class Bar { public void bar() {} }" | 1
    }

    def "project dependency analysis is #analysisRun if classes are #classChangeDescription and recompiled"() {
        given:
        withIncludedBuild()
        file("included/src/main/java/Foo.java") << "class Foo {}"
        file("included/src/main/java/Bar.java") << "class Bar {}"
        buildFile << """
            buildscript {
                repositories {
                    ${mavenCentralRepository()}
                }
                dependencies {
                    classpath "org.test:included"
                    classpath "org.apache.commons:commons-lang3:3.8.1"
                }
            }
        """

        when:
        run(":included:clean", "tasks")

        then:
        // Artifact name == "main" since in transform we get "classes/<language>/main" directory
        // as an input and we write a file name to the metadata as artifact name
        def artifactName = "main"
        typeHierarchyAnalysisOutputs(artifactName).size() == 1
        dependencyAnalysisOutputs(artifactName).size() == 1
        mergeAnalysisOutputs(artifactName).size() == 0

        when:
        file("included/src/main/java/Bar.java").text = barContentOnChange
        run(":included:clean", "tasks")

        then:
        typeHierarchyAnalysisOutputs(artifactName).size() == expectedFinalAnalysisOutputs
        dependencyAnalysisOutputs(artifactName).size() == expectedFinalAnalysisOutputs
        mergeAnalysisOutputs(artifactName).size() == 0

        where:
        analysisRun  | classChangeDescription | barContentOnChange                    | expectedFinalAnalysisOutputs
        "not re-run" | "not changed"          | "class Bar {}"                        | 1
        "re-run"     | "changed"              | "class Bar { private void bar() {} }" | 2
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
                        url = "${mavenRepo.uri}"
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
        def transformExecutions = buildOperations.all(ExecuteWorkBuildOperationType).findAll {
            it.details.workType == "TRANSFORM"
        }
        def pattern = Pattern.compile("Executing ([\$_a-zA-Z0-9]*):.*" + fileName)
        for (execution in transformExecutions) {
            buildOperations.search(execution) {
                def matcher = pattern.matcher(normaliseFileSeparators(it.displayName))
                if (matcher.matches()) {
                    transforms.add(matcher.group(1))
                    return true
                }
                return false
            }
        }
        return transforms
    }

    Set<TestFile> analyzeOutputs(String artifactName, String fileName, File cacheDir = getCacheDir()) {
        return findOutputs("$ANALYSIS_OUTPUT_DIR/$DEPENDENCY_ANALYSIS_FILE_NAME", cacheDir).findAll {
            serializer.readMetadataOnly(it).artifactName == artifactName
        }.collect {
            it.parentFile.listFiles().findAll { it.name == fileName }
        }.flatten() as Set<TestFile>
    }

    Set<TestFile> typeHierarchyAnalysisOutputs(String artifactName, File cacheDir = getCacheDir()) {
        return analyzeOutputs(artifactName, TYPE_HIERARCHY_ANALYSIS_FILE_NAME, cacheDir)
    }

    TestFile typeHierarchyAnalysisOutput(String artifactName, File cacheDir = getCacheDir()) {
        def analysis = typeHierarchyAnalysisOutputs(artifactName, cacheDir)
        if (analysis.size() == 1) {
            return analysis.first()
        }
        throw new AssertionError("Could not find exactly one type hierarchy analysis for $artifactName: $analysis")
    }

    Set<TestFile> dependencyAnalysisOutputs(String artifactName, File cacheDir = getCacheDir()) {
        return analyzeOutputs(artifactName, DEPENDENCY_ANALYSIS_FILE_NAME, cacheDir)
    }

    TestFile dependencyAnalysisOutput(String artifactName, File cacheDir = getCacheDir()) {
        def analysis = dependencyAnalysisOutputs(artifactName, cacheDir)
        if (analysis.size() == 1) {
            return analysis.first()
        }
        throw new AssertionError("Could not find exactly one dependency analysis for $artifactName: $analysis")
    }

    Set<TestFile> mergeAnalysisOutputs(String artifactName, File cacheDir = getCacheDir()) {
        return findOutputs("$MERGE_OUTPUT_DIR/$DEPENDENCY_ANALYSIS_FILE_NAME", cacheDir).findAll {
            serializer.readMetadataOnly(it).artifactName == artifactName
        }.collect { it } as Set<TestFile>
    }

    TestFile mergeAnalysisOutput(String artifactName, File cacheDir = getCacheDir()) {
        def analysis = mergeAnalysisOutputs(artifactName, cacheDir)
        if (analysis.size() == 1) {
            return analysis.first()
        }
        throw new AssertionError("Could not find exactly one merge dependency analysis for $artifactName: $analysis")
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
        return getGradleVersionedCacheDir().file(CacheLayout.TRANSFORMS.getName())
    }
}
