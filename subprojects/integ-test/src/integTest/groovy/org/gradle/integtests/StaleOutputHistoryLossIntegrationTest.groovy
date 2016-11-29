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

import com.google.common.io.Files
import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.fixtures.versions.ReleasedVersionDistributions
import org.gradle.test.fixtures.archive.JarTestFixture
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Issue

import static org.gradle.util.GFileUtils.forceDelete

class StaleOutputHistoryLossIntegrationTest extends AbstractIntegrationSpec {

    private final ReleasedVersionDistributions releasedVersionDistributions = new ReleasedVersionDistributions()
    private final GradleExecuter mostRecentFinalReleaseExecuter = releasedVersionDistributions.mostRecentFinalRelease.executer(temporaryFolder, getBuildContext())

    def cleanup() {
        mostRecentFinalReleaseExecuter.cleanup()
    }

    @NotYetImplemented
    @Issue("GRADLE-1501")
    def "production sources files are removed"() {
        given:
        def javaProjectFixture = new JavaProjectFixture()
        def jarFile = file('build/libs/test.jar')
        def taskPath = ':jar'

        settingsFile << "rootProject.name = 'test'"
        buildFile << "apply plugin: 'java'"

        when:
        def result = runWithMostRecentFinalRelease(taskPath)

        then:
        result.executedTasks.contains(taskPath)
        javaProjectFixture.mainClassFile.assertIsFile()
        javaProjectFixture.redundantClassFile.assertIsFile()
        hasDescendants(jarFile, javaProjectFixture.mainClassFile.name, javaProjectFixture.redundantClassFile.name)

        when:
        forceDelete(javaProjectFixture.redundantSourceFile)
        succeeds taskPath

        then:
        executedAndNotSkipped(taskPath)
        javaProjectFixture.mainClassFile.assertIsFile()
        javaProjectFixture.redundantClassFile.assertDoesNotExist()
        hasDescendants(jarFile, javaProjectFixture.mainClassFile.name)
    }

    @NotYetImplemented
    @Issue("GRADLE-1501")
    def "task history is deleted"() {
        def javaProjectFixture = new JavaProjectFixture()
        def jarFile = file('build/libs/test.jar')
        def taskPath = ':jar'

        settingsFile << "rootProject.name = 'test'"
        buildFile << "apply plugin: 'java'"

        when:
        succeeds taskPath

        then:
        result.executedTasks.contains(taskPath)
        javaProjectFixture.mainClassFile.assertIsFile()
        javaProjectFixture.redundantClassFile.assertIsFile()
        hasDescendants(jarFile, javaProjectFixture.mainClassFile.name, javaProjectFixture.redundantClassFile.name)

        when:
        file('.gradle').assertIsDir().deleteDir()
        forceDelete(javaProjectFixture.redundantSourceFile)
        succeeds taskPath

        then:
        executedAndNotSkipped(taskPath)
        javaProjectFixture.mainClassFile.assertIsFile()
        javaProjectFixture.redundantClassFile.assertDoesNotExist()
        hasDescendants(jarFile, javaProjectFixture.mainClassFile.name)
    }

    def "source files under buildSrc are removed"() {
        given:
        def javaProjectFixture = new JavaProjectFixture('buildSrc')
        def jarFile = file('buildSrc/build/libs/buildSrc.jar')
        def taskPath = ':help'

        when:
        def result = runWithMostRecentFinalRelease(taskPath)

        then:
        result.executedTasks.contains(taskPath)
        javaProjectFixture.mainClassFile.assertIsFile()
        javaProjectFixture.redundantClassFile.assertIsFile()
        hasDescendants(jarFile, javaProjectFixture.mainClassFile.name, javaProjectFixture.redundantClassFile.name)

        when:
        forceDelete(javaProjectFixture.redundantSourceFile)
        succeeds taskPath

        then:
        executedAndNotSkipped(taskPath)
        javaProjectFixture.mainClassFile.assertIsFile()
        javaProjectFixture.redundantClassFile.assertDoesNotExist()
        hasDescendants(jarFile, javaProjectFixture.mainClassFile.name)
    }

    def "tasks have common output directories"() {
        def sourceFile1 = file('source1/source1.txt')
        sourceFile1 << 'a'
        def sourceFile2 = file('source2/source2.txt')
        sourceFile2 << 'b'
        def targetFile1 = file('target/source1.txt')
        def targetFile2 = file('target/source2.txt')
        def taskPath = ':copyAll'

        buildFile << """
            task copy1(type: Copy) {
                from file('source1')
                into file('target')
            }

            task copy2(type: Copy) {
                from file('source2')
                into file('target')
            }

            task copyAll {
                dependsOn copy1, copy2
            }
        """

        when:
        def result = runWithMostRecentFinalRelease(taskPath)

        then:
        result.executedTasks.containsAll(taskPath, ':copy1', ':copy2')
        targetFile1.assertIsFile()
        targetFile2.assertIsFile()

        when:
        succeeds taskPath

        then:
        executedAndNotSkipped(taskPath, ':copy1', ':copy2')
        targetFile1.assertIsFile()
        targetFile2.assertIsFile()
    }

    @NotYetImplemented
    def "tasks have output directories outside of build directory"() {
        given:
        def sourceFile1 = file('source/source1.txt')
        sourceFile1 << 'a'
        def sourceFile2 = file('source/source2.txt')
        sourceFile2 << 'b'
        def targetFile1 = file('target/source1.txt')
        def targetFile2 = file('target/source2.txt')
        def taskPath = ':copy'

        buildFile << """
            task copy(type: Copy) {
                from file('source')
                into file('target')
            }
        """

        when:
        def result = runWithMostRecentFinalRelease(taskPath)

        then:
        result.executedTasks.contains(taskPath)
        targetFile1.assertIsFile()
        targetFile2.assertIsFile()

        when:
        forceDelete(sourceFile2)
        succeeds taskPath

        then:
        executedAndNotSkipped(taskPath)
        targetFile1.assertIsFile()
        targetFile2.assertDoesNotExist()
    }

    @NotYetImplemented
    def "output directories are changed"() {
        given:
        def sourceFile1 = file('source/source1.txt')
        sourceFile1 << 'a'
        def sourceFile2 = file('source/source2.txt')
        sourceFile2 << 'b'
        def targetFile1 = file('target/source1.txt')
        def targetFile2 = file('target/source2.txt')
        def taskPath = ':customCopy'

        buildFile << """
            task customCopy(type: CustomCopy) {
                sourceDir = file('source')
                targetDir = file('target')
            }

            class CustomCopy extends DefaultTask {
                @InputDirectory
                File sourceDir

                @OutputDirectory
                File targetDir

                @TaskAction
                void copyFiles() {
                    project.copy {
                        from sourceDir
                        into targetDir
                    }
                }
            }
        """

        when:
        def result = runWithMostRecentFinalRelease(taskPath)

        then:
        result.executedTasks.contains(taskPath)
        targetFile1.assertIsFile()
        targetFile2.assertIsFile()

        when:
        buildFile << """
            customCopy {
                targetDir = file('newTarget')
            }
        """
        succeeds taskPath

        then:
        executedAndNotSkipped(taskPath)
        targetFile1.assertDoesNotExist()
        targetFile2.assertDoesNotExist()
        file('newTarget/source1.txt').assertIsFile()
        file('newTarget/source2.txt').assertIsFile()
    }

    @NotYetImplemented
    def "task is removed"() {
        def sourceFile1 = file('source1/source.txt')
        sourceFile1 << 'a'
        def sourceFile2 = file('source2/source.txt')
        sourceFile2 << 'b'
        def targetFile1 = file('target1/source.txt')
        def targetFile2 = file('target2/source.txt')
        def taskPath = ':copyAll'

        buildFile << """
            task copy1(type: Copy) {
                from file('source1')
                into file('target1')
            }

            task copy2(type: Copy) {
                from file('source2')
                into file('target2')
            }

            task copyAll {
                dependsOn copy1, copy2
            }
        """

        when:
        def result = runWithMostRecentFinalRelease(taskPath)

        then:
        result.executedTasks.containsAll(taskPath, ':copy1', ':copy2')
        targetFile1.assertIsFile()
        targetFile2.assertIsFile()

        when:
        buildFile << """
            def reducedTaskDependencies = copyAll.taskDependencies.getDependencies(copyAll) - copy2
            copyAll.dependsOn = reducedTaskDependencies
        """
        succeeds taskPath

        then:
        executedAndNotSkipped(taskPath, ':copy1')
        notExecuted(':copy2')
        targetFile1.assertIsFile()
        targetFile2.assertDoesNotExist()
    }

    @NotYetImplemented
    def "inputs become empty for task"() {
        given:
        def sourceFile1 = file('source/source1.txt')
        sourceFile1 << 'a'
        def sourceFile2 = file('source/source2.txt')
        sourceFile2 << 'b'
        def targetFile1 = file('target/source1.txt')
        def targetFile2 = file('target/source2.txt')
        def taskPath = ':customCopy'

        buildFile << """
            task customCopy(type: CustomCopy) {
                sourceDir = fileTree('source')
                targetDir = file('target')
            }

            class CustomCopy extends DefaultTask {
                @InputFiles
                @SkipWhenEmpty
                FileTree sourceDir

                @OutputDirectory
                File targetDir

                @TaskAction
                void copyFiles() {
                    project.copy {
                        from sourceDir
                        into targetDir
                    }
                }
            }
        """

        when:
        def result = runWithMostRecentFinalRelease(taskPath)

        then:
        result.executedTasks.contains(taskPath)
        targetFile1.assertIsFile()
        targetFile2.assertIsFile()

        when:
        forceDelete(sourceFile1)
        forceDelete(sourceFile2)
        succeeds taskPath

        then:
        executedAndNotSkipped(taskPath)
        targetFile1.assertDoesNotExist()
        targetFile2.assertDoesNotExist()
    }

    @NotYetImplemented
    def "task is renamed"() {
        given:
        def sourceFile1 = file('source/source1.txt')
        sourceFile1 << 'a'
        def sourceFile2 = file('source/source2.txt')
        sourceFile2 << 'b'
        def targetFile1 = file('target/source1.txt')
        def targetFile2 = file('target/source2.txt')
        def taskPath = ':copy'

        buildFile << """
            task copy(type: Copy) {
                from file('source')
                into file('target')
            }
        """

        when:
        def result = runWithMostRecentFinalRelease(taskPath)

        then:
        result.executedTasks.contains(taskPath)
        targetFile1.assertIsFile()
        targetFile2.assertIsFile()
        long targetFile1LastModified = targetFile1.lastModified()
        long targetFile2LastModified = targetFile2.lastModified()

        when:
        def newTaskPath = ':newCopy'

        buildFile << """
            tasks.remove(copy)

            task newCopy(type: Copy) {
                from file('source')
                into file('target')
            }
        """
        forceDelete(sourceFile2)
        succeeds newTaskPath

        then:
        executedAndNotSkipped(newTaskPath)
        targetFile1.assertIsFile()
        targetFile2.assertIsFile()
        targetFile1.lastModified() != targetFile1LastModified
        targetFile2.lastModified() != targetFile2LastModified
    }

    private ExecutionResult runWithMostRecentFinalRelease(String... tasks) {
        mostRecentFinalReleaseExecuter.withTasks(tasks).run()
    }

    static boolean hasDescendants(File jarFile, String... relativePaths) {
        new JarTestFixture(jarFile).hasDescendants(relativePaths)
    }

    private class JavaProjectFixture {
        private final String rootDirName
        private final TestFile mainSourceFile
        private final TestFile redundantSourceFile
        private final TestFile mainClassFile
        private final TestFile redundantClassFile

        JavaProjectFixture() {
            this(null)
        }

        JavaProjectFixture(String rootDirName) {
            this.rootDirName = rootDirName
            mainSourceFile = writeJavaSourceFile('Main')
            redundantSourceFile = writeJavaSourceFile('Redundant')
            mainClassFile = determineClassFile(mainSourceFile)
            redundantClassFile = determineClassFile(redundantSourceFile)
        }

        private TestFile writeJavaSourceFile(String className) {
            String sourceFilePath = "src/main/java/${className}.java"
            sourceFilePath = prependRootDirName(sourceFilePath)
            def sourceFile = StaleOutputHistoryLossIntegrationTest.this.file(sourceFilePath)
            sourceFile << "public class $className {}"
            sourceFile
        }

        private TestFile determineClassFile(File sourceFile) {
            String classFilePath = "build/classes/main/${Files.getNameWithoutExtension(sourceFile.name)}.class"
            classFilePath = prependRootDirName(classFilePath)
            StaleOutputHistoryLossIntegrationTest.this.file(classFilePath)
        }

        private String prependRootDirName(String filePath) {
            rootDirName ? "$rootDirName/$filePath" : filePath
        }

        TestFile getRedundantSourceFile() {
            redundantSourceFile
        }

        TestFile getMainClassFile() {
            mainClassFile
        }

        TestFile getRedundantClassFile() {
            redundantClassFile
        }
    }
}
