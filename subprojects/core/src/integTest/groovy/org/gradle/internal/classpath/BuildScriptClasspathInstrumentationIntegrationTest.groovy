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

import java.nio.file.Files
import java.util.regex.Pattern
import java.util.stream.Collectors

import static org.gradle.api.internal.initialization.transform.CacheInstrumentationTypeRegistryBuildService.GENERATE_CLASS_HIERARCHY_WITHOUT_UPGRADES_PROPERTY
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
        run("tasks", "--info")

        then:
        gradleUserHomeOutput("original/buildSrc.jar").exists()
        gradleUserHomeOutput("instrumented/buildSrc.jar").exists()
        gradleUserHomeOutput("original/included-1.0.jar").exists()
        gradleUserHomeOutput("instrumented/included-1.0.jar").exists()
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
        allTransformsFor("commons-lang3-3.8.1.jar") ==~ ["CollectDirectClassSuperTypesTransform", "MergeSuperTypesTransform", "ExternalDependencyInstrumentingArtifactTransform"]
        gradleUserHomeOutputs("original/commons-lang3-3.8.1.jar").isEmpty()
        gradleUserHomeOutput("instrumented/commons-lang3-3.8.1.jar").exists()
    }

    def "should merge class hierarchies"() {
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
        run("tasks")

        then:
        gradleUserHomeOutput("instrumented/impl-1.0.jar").exists()
        gradleUserHomeOutput("instrumented/api-1.0.jar").exists()
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
            "CollectDirectClassSuperTypesTransform",
            "MergeSuperTypesTransform",
            "ExternalDependencyInstrumentingArtifactTransform",
            "CollectDirectClassSuperTypesTransform",
            "MergeSuperTypesTransform",
            "ExternalDependencyInstrumentingArtifactTransform"
        ]
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

    def "should collect super types for artifacts"() {
        given:
        // We test content in the global cache
        requireOwnGradleUserHomeDir()
        File jarFile = mavenRepo.module("test", "animals", "1.0").publish().getArtifactFile()
        artifactBuilder().with {
            it.sourceFile("org/gradle/test/Dogs.java").createFile().text = '''
                package org.gradle.test;
                class GermanShepherd extends Dog implements Animal {
                }
                abstract class Dog implements Mammal {
                }
                interface Mammal extends Animal {
                }
                interface Animal {
                }
            '''
            it.buildJar(jarFile)
        }
        buildFile << """
            buildscript {
                 repositories {
                    maven { url "$mavenRepo.uri" }
                }
                dependencies {
                    classpath "test:animals:1.0"
                }
            }
        """

        when:
        run("tasks", "--info")

        then:
        allTransformsFor("animals-1.0.jar") ==~ ["MergeSuperTypesTransform", "CollectDirectClassSuperTypesTransform", "ExternalDependencyInstrumentingArtifactTransform"]
        def output = gradleUserHomeOutput("direct/animals-1.0.jar.super-types")
        output.exists()
        output.readLines().drop(1) == [
            "org/gradle/test/Dog=org/gradle/test/Mammal",
            "org/gradle/test/GermanShepherd=org/gradle/test/Animal,org/gradle/test/Dog",
            "org/gradle/test/Mammal=org/gradle/test/Animal"
        ]
    }

    def "should collect and merge class hierarchies"() {
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
        gradleUserHomeOutput("instrumented/impl-1.0.jar").exists()
        gradleUserHomeOutput("instrumented/api-1.0.jar").exists()
        def implTypes = gradleUserHomeOutput("merged/impl-1.0.jar.super-types")
        implTypes.readLines().drop(1) == [
            "A=A,B",
        ]
        def apiTypes = gradleUserHomeOutput("merged/api-1.0.jar.super-types")
        apiTypes.readLines().drop(1) == []
    }

    def "should re-instrument jar if hierarchy changes"() {
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
        gradleUserHomeOutputs("instrumented/api-1.0.jar").size() == 1
        gradleUserHomeOutputs("instrumented/impl-1.0.jar").size() == 1

        when:
        file("subproject/api/src/main/java/B.java").text = "public class B extends C {}"
        file("subproject/api/src/main/java/C.java") << "public class C {}"
        executer.inDirectory(file("subproject")).withTasks("jar").run()
        run("tasks", "-D$GENERATE_CLASS_HIERARCHY_WITHOUT_UPGRADES_PROPERTY=true")

        then:
        gradleUserHomeOutputs("instrumented/api-1.0.jar").size() == 2
        gradleUserHomeOutputs("instrumented/impl-1.0.jar").size() == 2
    }

    def "should not re-instrument jar if classpath changes but hierarchy doesn't"() {
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
        gradleUserHomeOutputs("instrumented/api-1.0.jar").size() == 1
        gradleUserHomeOutputs("instrumented/impl-1.0.jar").size() == 1

        when:
        file("subproject/api/src/main/java/C.java") << "public class C {}"
        executer.inDirectory(file("subproject")).withTasks("jar").run()
        run("tasks", "-D$GENERATE_CLASS_HIERARCHY_WITHOUT_UPGRADES_PROPERTY=true")

        then:
        gradleUserHomeOutputs("instrumented/api-1.0.jar").size() == 2
        gradleUserHomeOutputs("instrumented/impl-1.0.jar").size() == 1
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

    def multiProjectJavaBuild(String projectName = "included", Action<String> init) {
        file("$projectName/api/build.gradle") << """
            plugins {
                id("java-library")
            }
            group = "org.test"
            version = "1.0"
        """
        file("$projectName/impl/build.gradle") << """
            plugins {
                id("java-library")
            }
            group = "org.test"
            version = "1.0"

            dependencies {
                implementation project(":api")
            }
        """
        file("$projectName/settings.gradle") << """
            rootProject.name = '$projectName'
            include("api", "impl")
        """
        init(projectName)
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
