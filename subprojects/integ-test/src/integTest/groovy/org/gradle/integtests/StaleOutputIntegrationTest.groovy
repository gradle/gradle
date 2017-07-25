/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

class StaleOutputIntegrationTest extends AbstractIntegrationSpec {
    @Issue(['GRADLE-2440', 'GRADLE-2579'])
    def 'stale classes are removed after Java sources are removed'() {
        setup:
        buildScript("apply plugin: 'java'")
        def fooJavaFile = file('src/main/java/Foo.java') << 'public class Foo {}'
        def fooClassFile = javaClassFile('Foo.class')
        def barJavaFile = file('src/main/java/com/example/Bar.java') << '''
            package com.example;

            public class Bar {}
        '''
        def barClassFile = javaClassFile('com/example/Bar.class')

        when:
        succeeds('compileJava')

        then:
        fooClassFile.exists()
        executedAndNotSkipped(':compileJava')

        when:
        fooJavaFile.delete()
        barJavaFile.delete()

        and:
        succeeds('compileJava')

        then:
        !fooClassFile.exists()
        !barClassFile.exists()
        executedAndNotSkipped(':compileJava')

        and:
        succeeds('compileJava')

        then:
        !fooClassFile.exists()
        !barClassFile.exists()
        skipped(':compileJava')
    }

    @Issue(['GRADLE-2440', 'GRADLE-2579'])
    def 'stale output file is removed after input source directory is emptied.'() {
        def taskWithSources = new TaskWithSources()
        taskWithSources.createInputs()
        buildFile << taskWithSources.buildScript

        when:
        succeeds(taskWithSources.taskPath)

        then:
        taskWithSources.outputFile.exists()
        executedAndNotSkipped(taskWithSources.taskPath)

        when:
        taskWithSources.removeInputs()

        and:
        succeeds(taskWithSources.taskPath)

        then:
        !taskWithSources.outputFile.exists()
        executedAndNotSkipped(taskWithSources.taskPath)

        and:
        succeeds(taskWithSources.taskPath)

        then:
        !taskWithSources.outputFile.exists()
        skipped(taskWithSources.taskPath)
    }

    @Issue("https://github.com/gradle/gradle/issues/973")
    def "only files owned by the build are deleted"() {
        def taskWithSources = new TaskWithSources(outputDir: 'unsafe-dir/output')
        taskWithSources.createInputs()
        buildFile << taskWithSources.buildScript

        when:
        succeeds(taskWithSources.taskPath)

        then:
        taskWithSources.outputFile.exists()
        executedAndNotSkipped(taskWithSources.taskPath)

        when:
        taskWithSources.removeInputs()
        succeeds(taskWithSources.taskPath)

        then:
        taskWithSources.outputFile.exists()
        skipped(taskWithSources.taskPath)
    }

    def "the output directory is not deleted if there are overlapping outputs"() {
        def taskWithSources = new TaskWithSources()
        taskWithSources.createInputs()
        def overlappingOutputFile = file("${taskWithSources.outputDir}/overlapping.txt")
        buildFile << taskWithSources.buildScript
        buildFile << """
            task taskWithOverlap {
                outputs.file('${taskWithSources.outputDir}/overlapping.txt')
                doLast {
                    file('${taskWithSources.outputDir}/overlapping.txt').text = "overlapping file"
                }
            }
        """.stripIndent()

        when:
        succeeds(taskWithSources.taskPath, "taskWithOverlap")

        then:
        taskWithSources.outputFile.exists()
        overlappingOutputFile.exists()
        executedAndNotSkipped(taskWithSources.taskPath, ":taskWithOverlap")

        when:
        taskWithSources.removeInputs()
        succeeds(taskWithSources.taskPath)

        then:
        overlappingOutputFile.exists()
        !taskWithSources.outputFile.exists()
        executedAndNotSkipped(taskWithSources.taskPath)
    }

    def "custom clean targets are removed"() {
        given:
        buildFile << """
            apply plugin: 'base'
            
            task myTask {
                outputs.dir "external/output"
                outputs.file "customFile"
                outputs.dir "build/dir"
                doLast {}
            }
            
            clean {
                delete "customFile"
            }
        """
        def dirInBuildDir = file("build/dir").createDir()
        def customFile = file("customFile").touch()
        def myTaskDir = file("external/output").createDir()

        when:
        succeeds("myTask")
        then:
        dirInBuildDir.assertDoesNotExist()
        customFile.assertDoesNotExist()
        buildFile.assertExists()
        // We should improve this eventually.  We currently don't delete _all_ outputs from every task
        // because we don't configure every clean task and we don't know if it's safe to remove all outputs.
        myTaskDir.assertExists()
    }

    def "stale outputs are removed after Gradle version change"() {
        given:
        buildFile << """
            apply plugin: 'base'

            task myTask {
                outputs.file "build/file"
                outputs.dir "build/dir"
                doLast {
                    assert !file("build/file").exists()
                    file("build/file").text = "Created"
                    assert !file("build/dir").exists() 
                    assert file("build/dir").mkdirs()
                }
            }
        """
        def dirInBuildDir = file("build/dir").createDir()
        def fileInBuildDir = file("build/file").touch()

        expect:
        succeeds("myTask")

        when:
        // Now that we produce this, we can detect the situation where
        // someone builds with Gradle 4.3, then 4.2 and then 4.3 again.
        file(".gradle/buildOutputCleanup/cache.properties").text = """
            gradle.version=1.0
        """
        // recreate the output
        dirInBuildDir.createDir()
        fileInBuildDir.touch()
        then:
        succeeds("myTask")
    }

    class TaskWithSources {
        String outputDir = "build/output"
        File inputFile = file('src/data/input.txt')
        String taskName = 'test'

        String getBuildScript() {
            """       
                apply plugin: 'base'

                task ${taskName} {
                    def sources = files("src")
                    inputs.dir sources skipWhenEmpty()
                    outputs.dir "${outputDir}"
                    doLast {
                        file("${outputDir}").mkdirs()
                        sources.asFileTree.visit { details ->
                            if (!details.directory) {
                                def output = file("${outputDir}/\$details.relativePath")
                                output.parentFile.mkdirs()
                                output.text = details.file.text
                            }
                        }
                    }
                }
            """.stripIndent()
        }

        File getOutputFile() {
            file("${outputDir}/data/input.txt")
        }

        String getTaskPath() {
            ":${taskName}"
        }

        void removeInputs() {
            inputFile.parentFile.deleteDir()
        }

        void createInputs() {
            inputFile.text = "input"
        }
    }

}
