/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.cc.impl

import spock.lang.Issue

class ConfigurationCacheIncludedBuildTransformIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    @Issue("https://github.com/gradle/gradle/issues/36177")
    def "can use #projectType of included build in buildscript classpath when the project is an input for transform"() {
        buildFile("included/settings.gradle", """
            include("$projectPath")
        """)
        buildFile("included/${relativeDir(projectPath)}/build.gradle", """
            plugins {
                id("java-library")
            }
            group = "org.test"
            version = "1.0"
        """)

        settingsFile """
            includeBuild("included")
        """

        buildFile """
            import org.gradle.api.artifacts.transform.TransformParameters
            buildscript {
                dependencies {
                    classpath("org.test:$artifactName:1.0")
                }
            }

            plugins {
                id("java-library")
            }

            ${withFooTransformedDependency("org.test:$artifactName:1.0")}
        """

        when:
        configurationCacheRun "syncFoo"

        and:
        // Run again as projects load logic is not triggered by load-after-store
        configurationCacheRun "syncFoo"

        then:
        file("build/${artifactName}-1.0.jar.foo").exists()

        where:
        projectType         | projectPath   | artifactName
        "project"           | ":lib"        | "lib"
        "nested subproject" | ":lib:sublib" | "sublib"
    }

    def "can use #projectType from included build logic build as an input for transform"() {
        buildFile("build-logic/settings.gradle", """
            include("$projectPath")
        """)
        buildFile("build-logic/build.gradle", """
            plugins {
                id("groovy-gradle-plugin")
            }
        """)
        file("build-logic/src/main/groovy/my-plugin.gradle") << """
            println 'In script plugin'
        """
        buildFile("build-logic/${relativeDir(projectPath)}/build.gradle", """
            plugins {
                id("java-library")
            }
            group = "org.test"
            version = "1.0"
        """)

        settingsFile """
            pluginManagement {
                includeBuild("build-logic")
            }
            includeBuild("build-logic")
        """

        buildFile """
            import org.gradle.api.artifacts.transform.TransformParameters
            plugins {
                id("my-plugin")
                id("java-library")
            }

            ${withFooTransformedDependency("org.test:$artifactName:1.0")}
        """

        when:
        configurationCacheRun "syncFoo"

        and:
        // Run again as projects load logic is not triggered by load-after-store
        configurationCacheRun "syncFoo"

        then:
        file("build/${artifactName}-1.0.jar.foo").exists()

        where:
        projectType         | projectPath   | artifactName
        "project"           | ":lib"        | "lib"
        "nested subproject" | ":lib:sublib" | "sublib"
    }

    def "can use #projectType from included build in buildscript classpath and as transform input in another included build"() {
        buildFile("build-logic/settings.gradle", """
            include("$projectPath")
        """)
        buildFile("build-logic/${relativeDir(projectPath)}/build.gradle", """
            plugins {
                id("java-library")
            }
            group = "org.test"
            version = "1.0"
        """)

        buildFile("consumer/settings.gradle", """
            includeBuild("../build-logic")
        """)
        buildFile("consumer/build.gradle", """
            import org.gradle.api.artifacts.transform.TransformParameters
            buildscript {
                dependencies {
                    classpath("org.test:$artifactName:1.0")
                }
            }

            plugins {
                id("java-library")
            }

            ${withFooTransformedDependency("org.test:$artifactName:1.0")}
        """)

        settingsFile """
            includeBuild("consumer")
            includeBuild("build-logic")
        """

        buildFile """
            tasks.register("run") {
                dependsOn(gradle.includedBuild("consumer").task(":syncFoo"))
            }
        """

        when:
        configurationCacheRun "run"

        and:
        // Run again as projects load logic is not triggered by load-after-store
        configurationCacheRun "run"

        then:
        file("consumer/build/${artifactName}-1.0.jar.foo").exists()

        where:
        projectType        | projectPath    | artifactName
        "project"          | ":lib"         | "lib"
        "nested subproject"| ":lib:sublib"  | "sublib"
    }

    def "can use #projectType from included build logic build as transform input in another included build"() {
        buildFile("build-logic/settings.gradle", """
            include("$projectPath")
        """)
        buildFile("build-logic/build.gradle", """
            plugins {
                id("groovy-gradle-plugin")
            }
        """)
        file("build-logic/src/main/groovy/my-plugin.gradle") << """
            println 'In script plugin'
        """
        buildFile("build-logic/${relativeDir(projectPath)}/build.gradle", """
            plugins {
                id("java-library")
            }
            group = "org.test"
            version = "1.0"
        """)

        buildFile("consumer/settings.gradle", """
            pluginManagement {
                includeBuild("../build-logic")
            }
            includeBuild("../build-logic")
        """)
        buildFile("consumer/build.gradle", """
            import org.gradle.api.artifacts.transform.TransformParameters
            plugins {
                id("my-plugin")
                id("java-library")
            }

            ${withFooTransformedDependency("org.test:$artifactName:1.0")}
        """)

        settingsFile """
            includeBuild("consumer")
            includeBuild("build-logic")
        """

        buildFile """
            tasks.register("run") {
                dependsOn(gradle.includedBuild("consumer").task(":syncFoo"))
            }
        """

        when:
        configurationCacheRun "run"

        and:
        // Run again as projects load logic is not triggered by load-after-store
        configurationCacheRun "run"

        then:
        file("consumer/build/${artifactName}-1.0.jar.foo").exists()

        where:
        projectType         | projectPath   | artifactName
        "project"           | ":lib"        | "lib"
        "nested subproject" | ":lib:sublib" | "sublib"
    }

    private static String relativeDir(String projectPath) {
        projectPath.replace(':', '/')
    }

    private static withFooTransformedDependency(String dependency) {
        """
            abstract class FooTransform implements TransformAction<TransformParameters.None> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInput()
                void transform(TransformOutputs outputs) {
                    def inputFile = input.get().asFile
                    outputs.file(inputFile.name + ".foo").createNewFile()
                }
            }

            dependencies {
                registerTransform(FooTransform) {
                    from.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
                    to.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "foo")
                }
                implementation("$dependency")
            }

             tasks.register("syncFoo", Sync) {
                from(configurations.runtimeClasspath.incoming.artifactView {
                    attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "foo")
                }.files)
                into(layout.buildDirectory)
            }
        """
    }
}

