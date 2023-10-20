/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.integtests.resolve.transform

import org.gradle.api.tasks.PathSensitivity
import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import spock.lang.Issue

import java.util.regex.Pattern

class ArtifactTransformInputArtifactIntegrationTest extends AbstractDependencyResolutionTest implements ArtifactTransformTestFixture, DirectoryBuildCacheFixture {
    def "transform does not execute when project artifact cannot be built"() {
        settingsFile << "include 'a', 'b', 'c'"
        setupBuildWithColorTransform()
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }
            project(':b') {
                tasks.producer.doLast { throw new RuntimeException('broken') }
            }

            abstract class MakeGreen implements TransformAction<TransformParameters.None> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    println "processing \${input.name}"
                    assert input.file
                }
            }
        """

        when:
        executer.withArgument("--continue")
        fails(":a:resolve")

        then:
        transformed("c.jar")
        failure.assertHasFailures(1)
        failure.assertHasDescription("Execution failed for task ':b:producer'.")
        failure.assertHasCause("broken")
    }

    // Documents existing behaviour. The absolute path of the input artifact is baked into the workspace identity
    // for incremental transforms, and so when the path changes the outputs are invalidated
    def "can attach #description to input artifact property with incrementally transformed artifact but it has no effect when not caching"() {
        settingsFile << "include 'a', 'b', 'c'"
        setupBuildWithColorTransform()
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }

            abstract class MakeGreen implements TransformAction<TransformParameters.None> {
                @InputArtifact ${annotation}
                abstract Provider<FileSystemLocation> getInputArtifact()

                @Inject
                abstract InputChanges getInputChanges()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    println "processing \${input.name}"
                    def output = outputs.file(input.name + ".green")
                    output.text = input.text + ".green"
                }
            }
        """

        when:
        succeeds(":a:resolve")

        then:
        result.assertTasksNotSkipped(":b:producer", ":c:producer", ":a:resolve")
        transformed("b.jar", "c.jar")
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        succeeds(":a:resolve")

        then: // no change, should be up to date
        result.assertTasksNotSkipped(":a:resolve")
        transformed()
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        withProjectConfig("b") {
            outputFileContent = "new"
        }
        succeeds(":a:resolve")

        then: // new content, should run
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b.jar")
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        withProjectConfig("b") {
            outputFileContent = "new"
        }
        succeeds(":a:resolve")

        then: // no change, should be up to date
        result.assertTasksNotSkipped(":a:resolve")
        transformed()
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        withProjectConfig("b") {
            projectBuildDir = "out"
            outputFileContent = "new"
        }
        succeeds(":a:resolve")

        then: // path has changed, should run
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b.jar")
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        withProjectConfig("b") {
            projectBuildDir = "out"
            outputFileContent = "new"
        }
        succeeds(":a:resolve")

        then: // no change, should be up to date
        result.assertTasksNotSkipped(":a:resolve")
        transformed()
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        withProjectConfig("b") {
            projectBuildDir = "out"
            outputFileContent = "new"
            outputFileName = "b-blue.jar"
        }
        succeeds(":a:resolve")

        then: // new file name, should run
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b-blue.jar")
        outputContains("result = [b-blue.jar.green, c.jar.green]")

        when:
        withProjectConfig("b") {
            projectBuildDir = "out"
            outputFileContent = "new"
            outputFileName = "b-blue.jar"
        }
        succeeds(":a:resolve")

        then: // no change, should be up to date
        result.assertTasksNotSkipped(":a:resolve")
        transformed()
        outputContains("result = [b-blue.jar.green, c.jar.green]")

        when:
        succeeds(":a:resolve")

        then: // have already seen these artifacts before, but the transform outputs have been overwritten
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b.jar")
        outputContains("result = [b.jar.green, c.jar.green]")

        where:
        description                                 | annotation
        "no sensitivity"                            | ""
        "@PathSensitive(PathSensitivity.ABSOLUTE)"  | "@PathSensitive(PathSensitivity.ABSOLUTE)"
        "@PathSensitive(PathSensitivity.RELATIVE)"  | "@PathSensitive(PathSensitivity.RELATIVE)"
        "@PathSensitive(PathSensitivity.NAME_ONLY)" | "@PathSensitive(PathSensitivity.NAME_ONLY)"
        "@PathSensitive(PathSensitivity.NONE)"      | "@PathSensitive(PathSensitivity.NONE)"
    }

    def "can attach #description to input artifact property with incrementally transformed artifact directory but it has no effect when not caching"() {
        settingsFile << "include 'a', 'b', 'c'"
        setupBuildWithColorTransform {
            produceDirs()
        }
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }

            abstract class MakeGreen implements TransformAction<TransformParameters.None> {
                @InputArtifact ${annotation}
                abstract Provider<FileSystemLocation> getInputArtifact()

                @Inject
                abstract InputChanges getInputChanges()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    if (input.exists()) {
                        println "processing \${input.name}"
                    } else {
                        println "processing missing \${input.name}"
                    }
                    def output = outputs.file(input.name + ".green")
                    output.text = input.list().length + ".green"
                }
            }
        """

        when:
        succeeds(":a:resolve")

        then:
        result.assertTasksNotSkipped(":b:producer", ":c:producer", ":a:resolve")
        transformed("b-dir", "c-dir")
        outputContains("result = [b-dir.green, c-dir.green]")

        when:
        succeeds(":a:resolve")

        then: // no change, should be up-to-date
        result.assertTasksNotSkipped(":a:resolve")
        transformed()
        outputContains("result = [b-dir.green, c-dir.green]")

        when:
        withProjectConfig("b") {
            outputFileName = "new"
        }
        succeeds(":a:resolve")

        then: // directory content has changed (file renamed)
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b-dir")
        outputContains("result = [b-dir.green, c-dir.green]")

        when:
        withProjectConfig("b") {
            outputFileName = "new"
        }
        succeeds(":a:resolve")

        then: // no change, should be up-to-date
        result.assertTasksNotSkipped(":a:resolve")
        transformed()
        outputContains("result = [b-dir.green, c-dir.green]")

        when:
        withProjectConfig("b") {
            outputFileName = "new"
            outputFileContent = "new"
        }
        succeeds(":a:resolve")

        then: // directory content has changed (file contents changed)
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b-dir")
        outputContains("result = [b-dir.green, c-dir.green]")

        when:
        withProjectConfig("b") {
            outputFileName = "new"
            outputFileContent = "new"
        }
        succeeds(":a:resolve")

        then: // no change, should be up-to-date
        result.assertTasksNotSkipped(":a:resolve")
        transformed()
        outputContains("result = [b-dir.green, c-dir.green]")

        when:
        withProjectConfig("b") {
            outputFileName = "new"
            outputFileContent = "new"
            outputDirName = "b-blue"
        }
        succeeds(":a:resolve")

        then: // directory name has changed
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b-blue")
        outputContains("result = [b-blue.green, c-dir.green]")

        when:
        withProjectConfig("b") {
            outputFileName = "new"
            outputFileContent = "new"
            outputDirName = "b-blue"
        }
        succeeds(":a:resolve")

        then: // no change, should be up-to-date
        result.assertTasksNotSkipped(":a:resolve")
        transformed()
        outputContains("result = [b-blue.green, c-dir.green]")

        when:
        withProjectConfig("b") {
            projectBuildDir = "out"
            outputFileName = "new"
            outputFileContent = "new"
            outputDirName = "b-blue"
        }
        succeeds(":a:resolve")

        then: // directory path has changed
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b-blue")

        when:
        withProjectConfig("b") {
            projectBuildDir = "out"
            outputFileName = "new"
            outputFileContent = "new"
            outputDirName = "b-blue"
        }
        succeeds(":a:resolve")

        then: // no change, should be up-to-date
        result.assertTasksNotSkipped(":a:resolve")
        transformed()
        outputContains("result = [b-blue.green, c-dir.green]")

        when:
        succeeds(":a:resolve")

        then: // have already seen these artifacts before, but the transform outputs have been overwritten
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b-dir")
        outputContains("result = [b-dir.green, c-dir.green]")

        where:
        description                                 | annotation
        "no sensitivity"                            | ""
        "@PathSensitive(PathSensitivity.ABSOLUTE)"  | "@PathSensitive(PathSensitivity.ABSOLUTE)"
        "@PathSensitive(PathSensitivity.RELATIVE)"  | "@PathSensitive(PathSensitivity.RELATIVE)"
        "@PathSensitive(PathSensitivity.NAME_ONLY)" | "@PathSensitive(PathSensitivity.NAME_ONLY)"
    }

    def "re-runs incremental transform when input artifact file changes from file to missing"() {
        settingsFile << "include 'a', 'b', 'c'"
        setupBuildWithColorTransform()
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }

            abstract class MakeGreen implements TransformAction<TransformParameters.None> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                @Inject
                abstract InputChanges getInputChanges()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    if (input.exists()) {
                        println "processing \${input.name}"
                    } else {
                        println "processing missing \${input.name}"
                    }
                    def output = outputs.file(input.name + ".green")
                    output.text = "green"
                }
            }
        """

        when:
        succeeds(":a:resolve")

        then:
        result.assertTasksNotSkipped(":b:producer", ":c:producer", ":a:resolve")
        transformed("b.jar", "c.jar")
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        succeeds(":a:resolve")

        then: // no changes
        result.assertTasksNotSkipped(":a:resolve")
        transformed()
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        succeeds(":a:resolve", "-DbContent=")

        then: // file is missing, should run
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("missing b.jar")
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        succeeds(":a:resolve")

        then: // seen these before, but the transform outputs have been overwritten
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b.jar")
        outputContains("result = [b.jar.green, c.jar.green]")
    }

    def "inputs to the build cache key are reported when build cache logging is enabled"() {
        given:
        settingsFile << "include 'a', 'b'"
        setupBuildWithColorTransform()
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                }
            }

            @CacheableTransform
            abstract class MakeGreen implements TransformAction<TransformParameters.None> {
                @PathSensitive(PathSensitivity.NONE)
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    println "processing \${input.name}"
                    def output = outputs.file(input.text + ".green")
                    output.text = input.text + ".green"
                }
            }
        """

        when:
        file("gradle.properties") << "${StartParameterBuildOptions.BuildCacheDebugLoggingOption.GRADLE_PROPERTY}=true"
        withBuildCache().run ":a:resolve"

        then:
        output.contains("Appending implementation to build cache key: MakeGreen")
        output.contains("Appending input value fingerprint for 'inputPropertiesHash' to build cache key:")
        output.contains("Appending input file fingerprints for 'inputArtifact' to build cache key:")
        output.contains("Appending input file fingerprints for 'inputArtifactDependencies' to build cache key:")
        output.contains("Appending output property name to build cache key: outputDirectory")
        output.contains("Appending output property name to build cache key: resultsFile")
        output.contains("Build cache key for MakeGreen")
    }

    def "honors @PathSensitive(NONE) on input artifact property for project artifact file when caching"() {
        settingsFile << "include 'a', 'b', 'c'"
        setupBuildWithColorTransform()
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }

            @CacheableTransform
            abstract class MakeGreen implements TransformAction<TransformParameters.None> {
                @PathSensitive(PathSensitivity.NONE)
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    println "processing \${input.name}"
                    def output = outputs.file(input.name + ".green")
                    output.text = input.text + ".green"
                }
            }
        """

        when:
        withBuildCache().succeeds(":a:resolve")

        then:
        result.assertTasksNotSkipped(":b:producer", ":c:producer", ":a:resolve")
        transformed("b.jar", "c.jar")
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        withProjectConfig("b") {
            projectBuildDir = "out"
        }
        withBuildCache().succeeds(":a:resolve")

        then: // path has changed, but should be up to date
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed()
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        withProjectConfig("b") {
            projectBuildDir = "out"
            outputFileName = "b-blue.jar"
        }
        withBuildCache().succeeds(":a:resolve")

        then: // name has changed, should run
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b-blue.jar")
        outputContains("result = [b-blue.jar.green, c.jar.green]")

        when:
        withBuildCache()
        withProjectConfig("b") {
            projectBuildDir = "out"
            outputFileContent = "b-new"
        }
        succeeds(":a:resolve")

        then: // new content, should run
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b.jar")
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        withBuildCache()
        withProjectConfig("b") {
            projectBuildDir = "out"
            outputFileContent = "b-new"
        }
        succeeds(":a:resolve")

        then: // no change, should be up to date
        result.assertTasksNotSkipped(":a:resolve")
        transformed()
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        withBuildCache()
        succeeds(":a:resolve")

        then: // have already seen these artifacts before
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed()
        outputContains("result = [b.jar.green, c.jar.green]")
    }

    def "honors @PathSensitive(#sensitivity) to input artifact property for incremental artifact directory transforms when caching"() {
        settingsFile << "include 'a', 'b', 'c'"
        setupBuildWithColorTransform {
            produceDirs()
        }
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }

            @CacheableTransform
            abstract class MakeGreen implements TransformAction<TransformParameters.None> {
                @PathSensitive(PathSensitivity.${sensitivity})
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                @Inject
                abstract InputChanges getInputChanges()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    println "processing \${input.name}"
                    def output = outputs.file(input.name + ".green")
                    output.text = input.list().length + ".green"
                }
            }
        """

        when:
        withBuildCache().succeeds(":a:resolve")

        then:
        result.assertTasksNotSkipped(":b:producer", ":c:producer", ":a:resolve")
        transformed("b-dir", "c-dir")
        outputContains("result = [b-dir.green, c-dir.green]")

        when:
        withProjectConfig("b") {
            projectBuildDir = "out"
        }
        withBuildCache().succeeds(":a:resolve")

        then: // path has changed, but should be up to date
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed()
        outputContains("result = [b-dir.green, c-dir.green]")

        when:
        withProjectConfig("b") {
            projectBuildDir = "out"
            outputDirName = "b-blue"
        }
        withBuildCache().succeeds(":a:resolve")

        then: // name has changed, should run
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b-blue")
        outputContains("result = [b-blue.green, c-dir.green]")

        when:
        withProjectConfig("b") {
            projectBuildDir = "out"
            outputDirName = "b-blue"
            outputFileContent = "new"
        }
        withBuildCache().succeeds(":a:resolve")

        then: // new content, should run
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b-blue")
        outputContains("result = [b-blue.green, c-dir.green]")

        when:
        withProjectConfig("b") {
            projectBuildDir = "out"
            outputDirName = "b-blue"
            outputFileContent = "new"
        }
        withBuildCache().succeeds(":a:resolve")

        then: // no change, should be up to date
        result.assertTasksNotSkipped(":a:resolve")
        transformed()
        outputContains("result = [b-blue.green, c-dir.green]")

        when:
        withProjectConfig("b") {
            projectBuildDir = "out"
            outputDirName = "b-blue"
            outputFileContent = "new"
            outputFileName = "new"
        }
        withBuildCache().succeeds(":a:resolve")

        then: // new content (renamed file), should run
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b-blue")
        outputContains("result = [b-blue.green, c-dir.green]")

        when:
        withBuildCache().succeeds(":a:resolve")

        then: // have already seen these artifacts before
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed()
        outputContains("result = [b-dir.green, c-dir.green]")

        where:
        sensitivity << [PathSensitivity.NAME_ONLY, PathSensitivity.RELATIVE]
    }

    def "honors @PathSensitive(#sensitivity) on input artifact property for project artifact file when caching"() {
        settingsFile << "include 'a', 'b', 'c'"
        setupBuildWithColorTransform()
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }

            @CacheableTransform
            abstract class MakeGreen implements TransformAction<TransformParameters.None> {
                @PathSensitive(PathSensitivity.${sensitivity})
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    println "processing \${input.name}"
                    def output = outputs.file(input.name + ".green")
                    output.text = input.text + ".green"
                }
            }
        """

        when:
        withBuildCache().succeeds(":a:resolve")

        then:
        result.assertTasksNotSkipped(":b:producer", ":c:producer", ":a:resolve")
        transformed("b.jar", "c.jar")
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        withProjectConfig("b") {
            projectBuildDir = "out"
        }
        withBuildCache().succeeds(":a:resolve")

        then: // path has changed, but should be up to date
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed()
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        withProjectConfig("b") {
            projectBuildDir = "out"
            outputFileName = "b-blue.jar"
        }
        withBuildCache().succeeds(":a:resolve")

        then: // name has changed, should run
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b-blue.jar")
        outputContains("result = [b-blue.jar.green, c.jar.green]")

        when:
        withProjectConfig("b") {
            projectBuildDir = "out"
            outputFileName = "b-blue.jar"
            outputFileContent = "new"
        }
        withBuildCache().succeeds(":a:resolve")

        then: // new content, should run
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b-blue.jar")
        outputContains("result = [b-blue.jar.green, c.jar.green]")

        when:
        withProjectConfig("b") {
            projectBuildDir = "out"
            outputFileName = "b-blue.jar"
            outputFileContent = "new"
        }
        withBuildCache().succeeds(":a:resolve")

        then: // no change, should be up to date
        result.assertTasksNotSkipped(":a:resolve")
        transformed()
        outputContains("result = [b-blue.jar.green, c.jar.green]")

        when:
        withBuildCache().succeeds(":a:resolve")

        then: // have already seen these artifacts before
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed()
        outputContains("result = [b.jar.green, c.jar.green]")

        where:
        sensitivity << [PathSensitivity.RELATIVE, PathSensitivity.NAME_ONLY]
    }

    def "honors content changes for @PathSensitive(NONE) on input artifact property for incremental artifact directory transforms when not caching"() {
        settingsFile << "include 'a', 'b', 'c'"
        setupBuildWithColorTransform {
            produceDirs()
        }
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }

            abstract class MakeGreen implements TransformAction<TransformParameters.None> {
                @PathSensitive(PathSensitivity.NONE)
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                @Inject
                abstract InputChanges getInputChanges()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    println "processing \${input.name}"
                    def output = outputs.file(input.name + ".green")
                    output.text = input.list().length + ".green"
                }
            }
        """

        when:
        succeeds(":a:resolve")

        then:
        result.assertTasksNotSkipped(":b:producer", ":c:producer", ":a:resolve")
        transformed("b-dir", "c-dir")
        outputContains("result = [b-dir.green, c-dir.green]")

        when:
        withProjectConfig("b") {
            projectBuildDir = "out"
        }
        succeeds(":a:resolve")

        then: // path has changed, but path is baked into workspace identity
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b-dir")
        outputContains("result = [b-dir.green, c-dir.green]")

        when:
        withProjectConfig("b") {
            projectBuildDir = "out"
            outputDirName = "b-blue"
        }
        succeeds(":a:resolve")

        then: // name has changed, but path is baked into workspace identity
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b-blue")
        outputContains("result = [b-blue.green, c-dir.green]")

        when:
        withProjectConfig("b") {
            projectBuildDir = "out"
            outputDirName = "b-blue"
            outputFileContent = "new"
        }
        succeeds(":a:resolve")

        then: // new content, should run
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b-blue")
        outputContains("result = [b-blue.green, c-dir.green]")

        when:
        withProjectConfig("b") {
            projectBuildDir = "out"
            outputDirName = "b-blue"
            outputFileContent = "new"
        }
        succeeds(":a:resolve")

        then: // no change, should be up to date
        result.assertTasksNotSkipped(":a:resolve")
        transformed()
        outputContains("result = [b-blue.green, c-dir.green]")

        when:
        withProjectConfig("b") {
            projectBuildDir = "out"
            outputDirName = "b-blue"
            outputFileContent = "new"
            outputFileName = "new"
        }
        succeeds(":a:resolve")

        then: // new content (renamed file), should not run
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed()
        outputContains("result = [b-blue.green, c-dir.green]")

        when:
        succeeds(":a:resolve")

        then: // have already seen these artifacts before
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed()
        outputContains("result = [b-dir.green, c-dir.green]")
    }

    def "honors @PathSensitive(NONE) on input artifact property for incremental artifact directory transforms when caching"() {
        settingsFile << "include 'a', 'b', 'c'"
        setupBuildWithColorTransform {
            produceDirs()
        }
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }

            @CacheableTransform
            abstract class MakeGreen implements TransformAction<TransformParameters.None> {
                @PathSensitive(PathSensitivity.NONE)
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                @Inject
                abstract InputChanges getInputChanges()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    println "processing \${input.name}"
                    def output = outputs.file(input.name + ".green")
                    output.text = input.list().length + ".green"
                }
            }
        """

        when:
        withBuildCache().succeeds(":a:resolve")

        then:
        result.assertTasksNotSkipped(":b:producer", ":c:producer", ":a:resolve")
        transformed("b-dir", "c-dir")
        outputContains("result = [b-dir.green, c-dir.green]")

        when:
        withProjectConfig("b") {
            projectBuildDir = "out"
        }
        withBuildCache().succeeds(":a:resolve")

        then: // path has changed, should be up to date
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed()
        outputContains("result = [b-dir.green, c-dir.green]")

        when:
        withProjectConfig("b") {
            projectBuildDir = "out"
            outputDirName = "b-blue"
        }
        withBuildCache().succeeds(":a:resolve")

        then: // name has changed, should run
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b-blue")
        outputContains("result = [b-blue.green, c-dir.green]")

        when:
        withProjectConfig("b") {
            projectBuildDir = "out"
            outputDirName = "b-blue"
            outputFileContent = "new"
        }
        withBuildCache().succeeds(":a:resolve")

        then: // new content, should run
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b-blue")
        outputContains("result = [b-blue.green, c-dir.green]")

        when:
        withProjectConfig("b") {
            projectBuildDir = "out"
            outputDirName = "b-blue"
            outputFileContent = "new"
        }
        withBuildCache().succeeds(":a:resolve")

        then: // no change, should be up to date
        result.assertTasksNotSkipped(":a:resolve")
        transformed()
        outputContains("result = [b-blue.green, c-dir.green]")

        when:
        withProjectConfig("b") {
            projectBuildDir = "out"
            outputDirName = "b-blue"
            outputFileContent = "new"
            outputFileName = "new"
        }
        withBuildCache().succeeds(":a:resolve")

        then: // new content (renamed file), should not run
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed()
        outputContains("result = [b-blue.green, c-dir.green]")

        when:
        withBuildCache().succeeds(":a:resolve")

        then: // have already seen these artifacts before
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed()
        outputContains("result = [b-dir.green, c-dir.green]")
    }

    def "can attach @PathSensitive(NONE) to input artifact property for external artifact"() {
        setupBuildWithColorTransform()
        def group1libv10 = mavenRepo.module("group1", "lib", "1.0").adhocVariants().variant('runtime', [color: 'blue']).withModuleMetadata().publish()
        group1libv10.artifactFile.text = "lib"
        def group2libv10 = mavenRepo.module("group2", "lib", "1.0").adhocVariants().variant('runtime', [color: 'blue']).withModuleMetadata().publish()
        group2libv10.artifactFile.text = "lib"
        def group2libv11 = mavenRepo.module("group2", "lib", "1.1").adhocVariants().variant('runtime', [color: 'blue']).withModuleMetadata().publish()
        group2libv11.artifactFile.text = "lib"
        def group2lib2v10 = mavenRepo.module("group2", "lib2", "1.0").adhocVariants().variant('runtime', [color: 'blue']).withModuleMetadata().publish()
        group2lib2v10.artifactFile.text = "lib2"
        def group1lib2v10 = mavenRepo.module("group1", "lib2", "1.0").adhocVariants().variant('runtime', [color: 'blue']).withModuleMetadata().publish()
        group1lib2v10.artifactFile.text = "lib2"

        buildFile << """
            repositories {
                maven {
                    url = '${mavenRepo.uri}'
                    metadataSources { gradleMetadata() }
                }
            }
            dependencies {
                implementation providers.gradleProperty('externalCoords').orElse('group1:lib:1.0')
                implementation 'group2:lib2:1.0'
            }

            abstract class MakeGreen implements TransformAction<TransformParameters.None> {
                @PathSensitive(PathSensitivity.NONE)
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    println "processing \${input.name}"
                    def output = outputs.file(input.name + ".green")
                    output.text = input.text + ".green"
                }
            }
        """

        when:
        succeeds(":resolve")

        then:
        transformed("lib-1.0.jar", "lib2-1.0.jar")
        outputContains("result = [lib-1.0.jar.green, lib2-1.0.jar.green]")

        when:
        succeeds(":resolve")

        then: // no change, should be up-to-date
        transformed()
        outputContains("result = [lib-1.0.jar.green, lib2-1.0.jar.green]")

        when:
        succeeds(":resolve", "-PexternalCoords=group2:lib:1.0")

        then: // path change, should be up-to-date
        transformed()
        outputContains("result = [lib-1.0.jar.green, lib2-1.0.jar.green]")

        when:
        succeeds(":resolve", "-PexternalCoords=group2:lib:1.1")

        then: // name change, should run
        transformed("lib-1.1.jar")
        outputContains("result = [lib-1.1.jar.green, lib2-1.0.jar.green]")

        when:
        group1libv10.artifactFile.text = "new-lib"
        succeeds(":resolve")

        then: // content change, should run
        transformed("lib-1.0.jar")
        outputContains("result = [lib-1.0.jar.green, lib2-1.0.jar.green]")

        when:
        succeeds(":resolve", "-PexternalCoords=group1:lib2:1.0")

        then: // duplicate content / name
        transformed()
        outputContains("result = [lib2-1.0.jar.green]")
    }

    def "can attach @PathSensitive(#sensitivity) to input artifact property for external artifact"() {
        setupBuildWithColorTransform()
        def lib1 = withColorVariants(mavenRepo.module("group1", "lib", "1.0")).publish()
        lib1.artifactFile.text = "lib"
        def lib2 = withColorVariants(mavenRepo.module("group2", "lib", "1.0")).publish()
        lib2.artifactFile.text = "lib"
        def lib3 = withColorVariants(mavenRepo.module("group2", "lib", "1.1")).publish()
        lib3.artifactFile.text = "lib"
        def lib4 = withColorVariants(mavenRepo.module("group2", "lib2", "1.0")).publish()
        lib4.artifactFile.text = "lib2"

        buildFile << """
            repositories {
                maven {
                    url = '${mavenRepo.uri}'
                    metadataSources { gradleMetadata() }
                }
            }
            dependencies {
                implementation providers.gradleProperty('externalCoords').orElse('group1:lib:1.0')
                implementation 'group2:lib2:1.0'
            }

            abstract class MakeGreen implements TransformAction<TransformParameters.None> {
                @PathSensitive(PathSensitivity.${sensitivity})
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    println "processing \${input.name}"
                    def output = outputs.file(input.name + ".green")
                    output.text = input.text + ".green"
                }
            }
        """

        when:
        succeeds(":resolve")

        then:
        transformed("lib-1.0.jar", "lib2-1.0.jar")
        outputContains("result = [lib-1.0.jar.green, lib2-1.0.jar.green]")

        when:
        succeeds(":resolve")

        then: // no change, should be up-to-date
        transformed()
        outputContains("result = [lib-1.0.jar.green, lib2-1.0.jar.green]")

        when:
        succeeds(":resolve", "-PexternalCoords=group2:lib:1.0")

        then: // path change, should be up-to-date
        transformed()
        outputContains("result = [lib-1.0.jar.green, lib2-1.0.jar.green]")

        when:
        succeeds(":resolve", "-PexternalCoords=group2:lib:1.1")

        then: // name change, should run
        transformed("lib-1.1.jar")
        outputContains("result = [lib-1.1.jar.green, lib2-1.0.jar.green]")

        when:
        lib1.artifactFile.text = "new-lib"
        succeeds(":resolve")

        then: // content change, should run
        transformed("lib-1.0.jar")
        outputContains("result = [lib-1.0.jar.green, lib2-1.0.jar.green]")

        where:
        sensitivity << [PathSensitivity.RELATIVE, PathSensitivity.NAME_ONLY]
    }

    def "honors content changes with @#annotation on input artifact property with incremental artifact transforms file when not caching"() {
        settingsFile << "include 'a', 'b', 'c'"
        setupBuildWithColorTransform {
            produceJars()
        }
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }

            abstract class MakeGreen implements TransformAction<TransformParameters.None> {
                @InputArtifact @${annotation}
                abstract Provider<FileSystemLocation> getInputArtifact()

                @Inject
                abstract InputChanges getInputChanges()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    println "processing \${input.name}"
                    def output = outputs.file(input.name + ".green")
                    output.text = input.text + ".green"
                }
            }
        """

        when:
        succeeds(":a:resolve")

        then:
        result.assertTasksNotSkipped(":b:producer", ":c:producer", ":a:resolve")
        transformed("b.jar", "c.jar")
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        succeeds(":a:resolve")

        then: // no change, should be up to date
        result.assertTasksNotSkipped(":a:resolve")
        transformed()
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        withProjectConfig("b") {
            outputFileContent = "new"
        }
        succeeds(":a:resolve")

        then: // new content, should run
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b.jar")
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        withProjectConfig("b") {
            outputFileContent = "new"
        }
        succeeds(":a:resolve")

        then: // no change, should be up to date
        result.assertTasksNotSkipped(":a:resolve")
        transformed()
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        withProjectConfig("b") {
            outputFileContent = "new"
            outputJarEntryTimestamp = 567
        }
        succeeds(":a:resolve")

        then: // timestamp change only, should not run
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed()
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        withProjectConfig("b") {
            outputFileContent = "new"
            outputJarEntryTimestamp = 567
            projectBuildDir = "out"
        }
        succeeds(":a:resolve")

        then: // path has changed, but path is baked into workspace identity
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b.jar")
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        withProjectConfig("b") {
            outputFileContent = "new"
            outputJarEntryTimestamp = 567
            projectBuildDir = "out"
            outputFileName = "b-blue.jar"
        }
        succeeds(":a:resolve")

        then: // new file name, but path is baked into workspace identity
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b-blue.jar")
        outputContains("result = [b-blue.jar.green, c.jar.green]")

        when:
        succeeds(":a:resolve")

        then: // have already seen these artifacts before, but outputs have been overwritten
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b.jar")
        outputContains("result = [b.jar.green, c.jar.green]")

        where:
        annotation << ["Classpath", "CompileClasspath"]
    }

    def "honors @#annotation on input artifact property with project artifact file when caching"() {
        settingsFile << "include 'a', 'b', 'c'"
        setupBuildWithColorTransform {
            produceJars()
        }
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }

            @CacheableTransform
            abstract class MakeGreen implements TransformAction<TransformParameters.None> {
                @InputArtifact @${annotation}
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    println "processing \${input.name}"
                    def output = outputs.file(input.name + ".green")
                    output.text = input.text + ".green"
                }
            }
        """

        when:
        withBuildCache().succeeds(":a:resolve")

        then:
        result.assertTasksNotSkipped(":b:producer", ":c:producer", ":a:resolve")
        transformed("b.jar", "c.jar")
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        withBuildCache().succeeds(":a:resolve")

        then: // no change, should be up to date
        result.assertTasksNotSkipped(":a:resolve")
        transformed()
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        withProjectConfig("b") {
            outputFileContent = "new"
        }
        withBuildCache().succeeds(":a:resolve")

        then: // new content, should run
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b.jar")
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        withProjectConfig("b") {
            outputFileContent = "new"
        }
        withBuildCache().succeeds(":a:resolve")

        then: // no change, should be up to date
        result.assertTasksNotSkipped(":a:resolve")
        transformed()
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        withProjectConfig("b") {
            outputFileContent = "new"
            outputJarEntryTimestamp = 567
        }
        withBuildCache().succeeds(":a:resolve")

        then: // timestamp change only, should not run
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed()
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        withProjectConfig("b") {
            outputFileContent = "new"
            outputJarEntryTimestamp = 567
            projectBuildDir = "out"
        }
        withBuildCache().succeeds(":a:resolve")

        then: // path has changed, should not run
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed()
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        withProjectConfig("b") {
            outputFileContent = "new"
            outputJarEntryTimestamp = 567
            projectBuildDir = "out"
            outputFileName = "b-blue.jar"
        }
        withBuildCache().succeeds(":a:resolve")

        then: // new file name, should run
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b-blue.jar")
        outputContains("result = [b-blue.jar.green, c.jar.green]")

        when:
        withBuildCache().succeeds(":a:resolve")

        then: // have already seen these artifacts before, should not run
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed()
        outputContains("result = [b.jar.green, c.jar.green]")

        where:
        annotation << ["Classpath", "CompileClasspath"]
    }

    def "honors runtime classpath normalization for input artifact for incremental transform"() {
        settingsFile << "include 'a', 'b', 'c'"
        setupBuildWithColorTransform {
            produceJars()
        }
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }

                normalization {
                    runtimeClasspath {
                        ignore("ignored.txt")
                    }
                }
            }

            abstract class MakeGreen implements TransformAction<TransformParameters.None> {
                @InputArtifact @Classpath
                abstract Provider<FileSystemLocation> getInputArtifact()

                @Inject
                abstract InputChanges getInputChanges()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    println "processing \${input.name}"
                    def output = outputs.file(input.name + ".green")
                    output.text = input.text + ".green"
                }
            }
        """

        when:
        withProjectConfig("b") {
            outputJarEntryName = "ignored.txt"
        }
        run(":a:resolve")

        then:
        result.assertTasksNotSkipped(":b:producer", ":c:producer", ":a:resolve")
        transformed("b.jar", "c.jar")
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        withProjectConfig("b") {
            outputJarEntryName = "ignored.txt"
            outputFileContent = "different"
        }
        run(":a:resolve")

        then: // change is ignored due to normalization
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed()
        outputContains("result = [b.jar.green, c.jar.green]")
    }

    @Issue("https://github.com/gradle/gradle/issues/19003")
    def "can transform an empty input artifact"() {
        settingsFile << "include 'a', 'b'"
        setupBuildWithColorTransform {
            produceDirs()
        }
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                }
            }

            abstract class MakeGreen implements TransformAction<TransformParameters.None> {
                // Make sure empty directories in the input are ignored, making the fingerprint empty.
                @PathSensitive(PathSensitivity.RELATIVE)
                @IgnoreEmptyDirectories
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    println "processing \${input.name}"
                    def output = outputs.file(input.name + ".green")
                    output.text = input.name + ".green"
                }
            }
        """

        when:
        withProjectConfig("b") {
            emptyOutputDir()
        }
        run(":a:resolve")

        then:
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b-dir")
        outputContains("result = [b-dir.green]")
    }

    void transformed(String... expected) {
        def actual = output.readLines().inject([]) { items, line ->
            def matcher = Pattern.compile("processing\\s+(.+)").matcher(line)
            if (matcher.find()) {
                items.add(matcher.group(1))
            }
            return items
        }
        assert actual.sort() == (expected as List).sort()
    }
}
