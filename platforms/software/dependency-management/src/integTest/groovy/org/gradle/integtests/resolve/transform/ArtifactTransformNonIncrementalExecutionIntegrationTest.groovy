/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Issue

class ArtifactTransformNonIncrementalExecutionIntegrationTest extends AbstractIntegrationSpec {
    @Issue("https://github.com/gradle/gradle/issues/28475")
    def "non-reproducible artifact transform gets regenerated properly when transform directory is deleted between builds -- GitHub Action case"() {
        executer.beforeExecute {
            executer.requireOwnGradleUserHomeDir()
            executer.requireIsolatedDaemons()
            // Ensure we have a fresh daemon for each build invocation
//            executer.requireIsolatedDaemons()
//            executer.withArgument("--no-daemon")
        }

        given:
        buildFile << """
            import org.gradle.api.artifacts.transform.TransformParameters

            def type = Attribute.of("artifactType", String)

            abstract class NonReproducibleTransform implements TransformAction<TransformParameters.None> {

                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    // Produce a non-reproducible output
                    def outputFile = outputs.file("output.txt")
                    outputFile.text = "output-" + System.currentTimeMillis()
                    println "Produced non-reproducible output: \$outputFile"
                }
            }

            abstract class ConsumerTask extends DefaultTask {
                @InputFiles
                abstract ConfigurableFileCollection getInputFiles()

                @TaskAction
                void consume() {
                    inputFiles.forEach { file ->
                        println "Consumed: \$file"
                    }
                }
            }

            abstract class HashLookupTask extends DefaultTask {
                @InputFiles
                abstract ConfigurableFileCollection getInputFiles()

                @Inject
                abstract org.gradle.internal.hash.FileHasher getFileHasher()

                @TaskAction
                void consume() {
                    inputFiles.forEach { file ->
                        println "Hash: \${fileHasher.hash(file)}"
                    }
                }
            }


            dependencies {
                registerTransform(NonReproducibleTransform) {
                    from.attribute(type, "jar")
                    to.attribute(type, "test")
                }
            }

            configurations {
                compile
            }
            dependencies {
                def file = file("thing.jar")
                file.text = "not-really-a-jar"
                compile files(file)
            }

            task consumer(type: ConsumerTask) {
                inputFiles = configurations.compile.incoming.artifactView { attributes { it.attribute(type, "test") } }.files
            }
            task hashLookup(type: HashLookupTask) {
                inputFiles = configurations.compile.incoming.artifactView { attributes { it.attribute(type, "test") } }.files
            }
        """

        when: "First build produces version 1 of output"
        succeeds("consumer")
        updateCacheTimestamps()
        def firstOutput = immutableOutput.text
        def immutableWorkspace = immutableOutput.parentFile.parentFile

        then:
        immutableOutput.assertIsFile()

        when:
        succeeds("consumer")
        updateCacheTimestamps()

        then:
        // It's up-to-date, so no processing happens
        !producedOutputPath.present

        when:
        // Delete the transform output directory
        immutableWorkspace.deleteDir()

        then:
        immutableWorkspace.assertDoesNotExist()

        when:
        succeeds("consumer")
        updateCacheTimestamps()

        then:
        // The output is regenerated
        immutableOutput.text != firstOutput

        when:
        executer.withDaemonBaseDir(file("daemon-2"))
        succeeds("hashLookup")
        updateCacheTimestamps()

        then:
        // It's up-to-date, so no processing happens
        !producedOutputPath.present
    }

    private void updateCacheTimestamps() {
        long fixedTimeStamp = 1630000000000L
        println "Updating timestamps to $fixedTimeStamp on global caches"
        executer.gradleUserHomeDir.file("caches").eachFileRecurse {
            it.lastModified = fixedTimeStamp
        }
    }

    private Optional<String> getProducedOutputPath() {
        def pattern = /(?m)^Produced non-reproducible output: (.+)$/
        def matcher = result.output =~ pattern
        if (matcher.find()) {
            return Optional.of(matcher.group(1))
        } else {
            return Optional.empty()
        }
    }

    private TestFile getImmutableOutput() {
        String immutablePath = producedOutputPath.get().replaceAll(/-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/, '')
        return new TestFile(immutablePath)
    }
}
