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

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest

import static org.gradle.api.internal.initialization.DefaultScriptClassPathResolver.INSTRUMENTED_ATTRIBUTE
import static org.gradle.api.internal.initialization.DefaultScriptClassPathResolver.InstrumentationPhase.NOT_INSTRUMENTED

class ArtifactTransformIncrementalIntegrationTest extends AbstractDependencyResolutionTest implements ArtifactTransformTestFixture {

    def "incremental artifact transform in buildscript block for included build runs without exception"() {
        createDirs("a", "included")
        settingsFile << """
            includeBuild("included") {
                dependencySubstitution {
                    substitute(module("com.test:lib")).using(project(":b"))
                }
            }
            include ":a"
        """
        file("a/build.gradle") << """
            buildscript {
                // Build script classpath is resolved via ${INSTRUMENTED_ATTRIBUTE.name} attribute,
                // so we have to set that attribute too to make transform run
                def artifactType = Attribute.of('artifactType', String)
                def instrumented = Attribute.of('${INSTRUMENTED_ATTRIBUTE.name}', String.class)
                dependencies {
                    classpath "com.test:lib:1.0"
                    registerTransform(MakeColor) {
                        from.attribute(artifactType, 'jar').attribute(instrumented, '${NOT_INSTRUMENTED.value}')
                        to.attribute(artifactType, 'green').attribute(instrumented, '${NOT_INSTRUMENTED.value}')
                        parameters.targetColor.set('green')
                    }
                }
                configurations.classpath.attributes.attribute(artifactType, 'green')
            }
        """
        file("included/b/build.gradle") << """
            plugins {
                id("java-library")
            }
        """
        file("included/settings.gradle") << """
            rootProject.name = "included"
            include ":b"
        """
        file("buildSrc/src/main/groovy/MakeColor.groovy") << """
            import javax.inject.Inject
            import org.gradle.api.artifacts.transform.*
            import org.gradle.api.file.*
            import org.gradle.api.provider.*
            import org.gradle.api.tasks.*
            import org.gradle.work.InputChanges

            @CacheableTransform
            abstract class MakeColor implements TransformAction<Parameters> {
                interface Parameters extends TransformParameters {
                    @Input
                    Property<String> getTargetColor()
                }

                @PathSensitive(PathSensitivity.NAME_ONLY)
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                @Inject abstract InputChanges getInputChanges()

                @Override
                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    println "processing [\${input.name}]"
                    def output = outputs.file("\${input.name}.\${parameters.targetColor.get()}")
                    output.text = "\${input.exists() ? input.text : 'missing'}-\${parameters.targetColor.get()}"
                }
            }
        """

        expect:
        succeeds ":a:help"
        outputContains("processing [b.jar]")
    }

    def "can query incremental changes"() {
        createDirs("a", "b")
        settingsFile << """
            include 'a', 'b'
        """

        file("buildSrc/src/main/groovy/MakeGreen.groovy") << """
            import java.io.File
            import javax.inject.Inject;
            import groovy.transform.CompileStatic
            import org.gradle.api.provider.*
            import org.gradle.api.file.*
            import org.gradle.api.tasks.*
            import org.gradle.api.artifacts.transform.*
            import org.gradle.work.*

            abstract class MakeGreen implements TransformAction<Parameters> {

                interface Parameters extends TransformParameters {
                    @Internal
                    ListProperty<String> getAddedFiles()
                    @Internal
                    ListProperty<String> getModifiedFiles()
                    @Internal
                    ListProperty<String> getRemovedFiles()
                    @Internal
                    Property<Boolean> getIncrementalExecution()
                    @Internal
                    Property<Boolean> getRegisterNewOutput()
                }

                @Inject
                abstract InputChanges getInputChanges()

                @InputArtifact
                abstract Provider<FileSystemLocation> getInput()

                void transform(TransformOutputs outputs) {
                    println "Transforming " + input.get().asFile.name
                    println "incremental: " + inputChanges.incremental
                    assert parameters.incrementalExecution.get() == inputChanges.incremental
                    def changes = inputChanges.getFileChanges(input)
                    println "changes: \\n" + changes.join("\\n")
                    assert changes.findAll { it.changeType == ChangeType.ADDED }*.file as Set == resolveFiles(parameters.addedFiles.get())
                    assert changes.findAll { it.changeType == ChangeType.REMOVED }*.file as Set == resolveFiles(parameters.removedFiles.get())
                    assert changes.findAll { it.changeType == ChangeType.MODIFIED }*.file as Set == resolveFiles(parameters.modifiedFiles.get())
                    def outputDirectory = outputs.dir("output")
                    changes.each { change ->
                        if (change.file != input.get().asFile) {
                            File outputFile = new File(outputDirectory, change.file.name)
                            switch (change.changeType) {
                                case ChangeType.ADDED:
                                case ChangeType.MODIFIED:
                                    outputFile.text = change.file.text
                                    break
                                case ChangeType.REMOVED:
                                    outputFile.delete()
                                    break
                                default:
                                    throw new IllegalArgumentException()
                            }
                        }
                    }
                }

                private resolveFiles(List<String> files) {
                    files.collect { new File(input.get().asFile, it) } as Set
                }
            }
        """

        setupBuildFile()

        when:
        previousExecution()
        withProjectConfig("b") {
            outputFileContent = "changed"
        }
        then:
        executesIncrementally("ext.modified=['b']")

        when:
        withProjectConfig("b") {
            names = ["first", "second", "third"]
        }
        then:
        executesIncrementally("""
            ext.removed = ['b']
            ext.added = ['first', 'second', 'third']
        """)

        when:
        withProjectConfig("b") {
            names = ["first", "second"]
            outputFileContent = "different"
        }
        then:
        executesIncrementally("""
            ext.removed = ['third']
            ext.modified = ['first', 'second']
        """)
    }

    private void setupBuildFile() {
        buildFile .text = """
            ext {
                added = []
                modified = []
                removed = []
                incremental = true
                registerNewOutput = false
            }
        """
        setupBuildWithColorTransform {
            produceDirs()
            params("""
                addedFiles.set(provider { added })
                modifiedFiles.set(provider { modified })
                removedFiles.set(provider { removed })
                incrementalExecution.set(provider { incremental })
                incrementalExecution.set(provider { incremental })
                registerNewOutput.set(provider { project.registerNewOutput })
            """)
        }
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                }
            }
        """
    }

    void executesNonIncrementally(String fileChanges = "ext.added = ['', 'b']") {
        setupBuildFile()
        buildFile << """
            ext.incremental = false
            $fileChanges
        """
        succeeds ":a:resolve"
        outputContains("Transforming")
    }

    void executesIncrementally(String fileChanges) {
        setupBuildFile()
        buildFile << """
            ext.incremental = true
            $fileChanges
        """
        succeeds ":a:resolve"
        outputContains("Transforming")
    }

    void previousExecution() {
        executesNonIncrementally()
    }
}
