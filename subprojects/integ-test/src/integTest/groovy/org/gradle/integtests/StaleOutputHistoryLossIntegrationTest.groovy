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

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.StaleOutputJavaProject
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.fixtures.versions.ReleasedVersionDistributions
import org.gradle.test.fixtures.archive.JarTestFixture
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Issue
import spock.lang.Unroll

import static org.gradle.integtests.fixtures.StaleOutputJavaProject.*
import static org.gradle.util.GFileUtils.forceDelete

class StaleOutputHistoryLossIntegrationTest extends AbstractIntegrationSpec {

    private final ReleasedVersionDistributions releasedVersionDistributions = new ReleasedVersionDistributions()
    private final GradleExecuter mostRecentFinalReleaseExecuter = releasedVersionDistributions.mostRecentFinalRelease.executer(temporaryFolder, getBuildContext())

    def cleanup() {
        mostRecentFinalReleaseExecuter.cleanup()
    }

    @Issue("GRADLE-1501")
    @Unroll
    def "production sources files are removed in a single project build for #description"() {
        given:
        def javaProject = new StaleOutputJavaProject(testDirectory, null, buildDirName)
        buildFile << "apply plugin: 'java'"

        if (!defaultDir) {
            buildFile << """
                buildDir = '$javaProject.buildDirName'
            """
        }

        when:
        def result = runWithMostRecentFinalRelease(JAR_TASK_PATH)

        then:
        result.executedTasks.containsAll(COMPILE_JAVA_TASK_PATH, JAR_TASK_PATH)
        !result.output.contains(javaProject.buildDirCleanupMessage)
        javaProject.mainClassFile.assertIsFile()
        javaProject.redundantClassFile.assertIsFile()
        hasDescendants(javaProject.jarFile, javaProject.mainClassFile.name, javaProject.redundantClassFile.name)

        when:
        forceDelete(javaProject.redundantSourceFile)
        succeeds JAR_TASK_PATH

        then:
        executedAndNotSkipped(COMPILE_JAVA_TASK_PATH, JAR_TASK_PATH)
        outputContains(javaProject.buildDirCleanupMessage)
        javaProject.mainClassFile.assertIsFile()
        javaProject.redundantClassFile.assertDoesNotExist()
        hasDescendants(javaProject.jarFile, javaProject.mainClassFile.name)

        when:
        succeeds JAR_TASK_PATH

        then:
        skipped COMPILE_JAVA_TASK_PATH, JAR_TASK_PATH
        !output.contains(javaProject.buildDirCleanupMessage)
        javaProject.mainClassFile.assertIsFile()
        javaProject.redundantClassFile.assertDoesNotExist()
        hasDescendants(javaProject.jarFile, javaProject.mainClassFile.name)

        where:
        buildDirName | defaultDir | description
        'build'      | true       | 'default build directory'
        'out'        | false      | 'reconfigured build directory'
    }

    // classesDir really should be immutable once we've executed the task.
    @NotYetImplemented
    @Issue("GRADLE-1501")
    def "production sources files are removed even if output directory is reconfigured during execution phase"() {
        given:
        def javaProject = new StaleOutputJavaProject(testDirectory)
        buildFile << """
            apply plugin: 'java'
            
            task configureCompileJava {
                doLast {
                    sourceSets.main.output.classesDir = file('out')
                }
            }
            
            compileJava.dependsOn configureCompileJava
        """
        def customClassesOutputDir = file('out')
        def newMainClassFileName = new TestFile(customClassesOutputDir, javaProject.mainClassFile.name)
        def newRedundantClassFileName = new TestFile(customClassesOutputDir, javaProject.redundantClassFile.name)
        def newCleanupMessage = "Cleaned up directory '$customClassesOutputDir'"

        when:
        def result = runWithMostRecentFinalRelease(JAR_TASK_PATH)

        then:
        result.executedTasks.containsAll(COMPILE_JAVA_TASK_PATH, JAR_TASK_PATH)
        !result.output.contains(javaProject.buildDirCleanupMessage)
        javaProject.mainClassFile.assertDoesNotExist()
        javaProject.redundantClassFile.assertDoesNotExist()
        hasDescendants(javaProject.jarFile, javaProject.mainClassFile.name, javaProject.redundantClassFile.name)
        newMainClassFileName.assertIsFile()
        newRedundantClassFileName.assertIsFile()

        when:
        forceDelete(javaProject.redundantSourceFile)
        succeeds JAR_TASK_PATH

        then:
        executedAndNotSkipped(COMPILE_JAVA_TASK_PATH, JAR_TASK_PATH)
        outputContains(newCleanupMessage)
        javaProject.mainClassFile.assertDoesNotExist()
        javaProject.redundantClassFile.assertDoesNotExist()
        hasDescendants(javaProject.jarFile, javaProject.mainClassFile.name)
        newMainClassFileName.assertIsFile()
        newRedundantClassFileName.assertDoesNotExist()

        when:
        succeeds JAR_TASK_PATH

        then:
        skipped COMPILE_JAVA_TASK_PATH, JAR_TASK_PATH
        !output.contains(newCleanupMessage)
        javaProject.mainClassFile.assertDoesNotExist()
        javaProject.redundantClassFile.assertDoesNotExist()
        hasDescendants(javaProject.jarFile, javaProject.mainClassFile.name)
        newMainClassFileName.assertIsFile()
        newRedundantClassFileName.assertDoesNotExist()
    }

    @Unroll
    @Issue("GRADLE-1501")
    def "production sources files are removed in a multi-project build executed #description"() {
        given:
        def projectCount = 3
        def javaProjects = (1..projectCount).collect { new StaleOutputJavaProject(testDirectory, createProjectName(it)) }

        buildFile << """
            subprojects {
                apply plugin: 'java'
            }
        """

        file('settings.gradle') << "include ${(1..projectCount).collect { "'${createProjectName(it)}'" }.join(',')}"

        when:
        def result = runWithMostRecentFinalRelease(arguments as String[])

        then:
        javaProjects.each {
            def expectedTaskPaths = [":${it.rootDirName}${COMPILE_JAVA_TASK_PATH}".toString(), ":${it.rootDirName}${JAR_TASK_PATH}".toString()]
            assert result.executedTasks.containsAll(expectedTaskPaths)
            !result.output.contains(it.buildDirCleanupMessage)
            assert it.mainClassFile.assertIsFile()
            assert it.redundantClassFile.assertIsFile()
            assert hasDescendants(it.jarFile, it.mainClassFile.name, it.redundantClassFile.name)
        }

        when:
        javaProjects.each {
            forceDelete(it.redundantSourceFile)
        }
        succeeds arguments as String[]

        then:
        javaProjects.each {
            def expectedTaskPaths = [":${it.rootDirName}${COMPILE_JAVA_TASK_PATH}".toString(), ":${it.rootDirName}${JAR_TASK_PATH}".toString()]
            assert result.executedTasks.containsAll(expectedTaskPaths)
            outputContains(it.buildDirCleanupMessage)
            assert it.mainClassFile.assertIsFile()
            assert it.redundantClassFile.assertDoesNotExist()
            assert hasDescendants(it.jarFile, it.mainClassFile.name)
        }

        when:
        succeeds arguments as String[]

        then:
        javaProjects.each {
            def expectedTaskPaths = [":${it.rootDirName}${COMPILE_JAVA_TASK_PATH}".toString(), ":${it.rootDirName}${JAR_TASK_PATH}".toString()]
            skipped expectedTaskPaths as String[]
            assert !output.contains(it.buildDirCleanupMessage)
            assert it.mainClassFile.assertIsFile()
            assert it.redundantClassFile.assertDoesNotExist()
            assert hasDescendants(it.jarFile, it.mainClassFile.name)
        }

        where:
        arguments                                              | description
        [JAR_TASK_NAME]                                        | 'without additional argument'
        [JAR_TASK_NAME, '--parallel']                          | 'in parallel'
        [JAR_TASK_NAME, '--parallel', '--configure-on-demand'] | 'in parallel and configure on demand enabled'
    }

    @Issue("GRADLE-1501")
    def "task history is deleted"() {
        def javaProject = new StaleOutputJavaProject(testDirectory)
        buildFile << "apply plugin: 'java'"

        when:
        succeeds JAR_TASK_PATH

        then:
        result.executedTasks.containsAll(COMPILE_JAVA_TASK_PATH, JAR_TASK_PATH)
        !result.output.contains(javaProject.buildDirCleanupMessage)
        javaProject.mainClassFile.assertIsFile()
        javaProject.redundantClassFile.assertIsFile()
        hasDescendants(javaProject.jarFile, javaProject.mainClassFile.name, javaProject.redundantClassFile.name)

        when:
        file('.gradle').assertIsDir().deleteDir()
        forceDelete(javaProject.redundantSourceFile)
        succeeds JAR_TASK_PATH

        then:
        executedAndNotSkipped(COMPILE_JAVA_TASK_PATH, JAR_TASK_PATH)
        outputContains(javaProject.buildDirCleanupMessage)
        javaProject.mainClassFile.assertIsFile()
        javaProject.redundantClassFile.assertDoesNotExist()
        hasDescendants(javaProject.jarFile, javaProject.mainClassFile.name)

        when:
        succeeds JAR_TASK_PATH

        then:
        skipped COMPILE_JAVA_TASK_PATH, JAR_TASK_PATH
        !output.contains(javaProject.buildDirCleanupMessage)
        javaProject.mainClassFile.assertIsFile()
        javaProject.redundantClassFile.assertDoesNotExist()
        hasDescendants(javaProject.jarFile, javaProject.mainClassFile.name)
    }

    @Unroll
    def "source files under buildSrc are removed for #description"() {
        given:
        def buildSrcProject = new StaleOutputJavaProject(testDirectory, 'buildSrc', buildDirName)
        def helpTaskPath = ':help'
        def buildSrcCleanTaskPath = ":$buildSrcProject.rootDirName:clean"

        if (!defaultDir) {
            file("$buildSrcProject.rootDirName/build.gradle") << """
                buildDir = '$buildSrcProject.buildDirName'
            """
        }

        when:
        def result = runWithMostRecentFinalRelease(helpTaskPath)

        then:
        result.executedTasks.contains(helpTaskPath)
        !result.output.contains(buildSrcProject.buildDirCleanupMessage)
        buildSrcProject.mainClassFile.assertIsFile()
        buildSrcProject.redundantClassFile.assertIsFile()
        hasDescendants(buildSrcProject.jarFile, buildSrcProject.mainClassFile.name, buildSrcProject.redundantClassFile.name)

        when:
        forceDelete(buildSrcProject.redundantSourceFile)
        succeeds helpTaskPath

        then:
        executedAndNotSkipped(helpTaskPath)
        !output.contains(buildSrcCleanTaskPath)
        !result.output.contains(buildSrcProject.buildDirCleanupMessage)
        outputContains(buildSrcProject.buildDirCleanupMessage)
        buildSrcProject.mainClassFile.assertIsFile()
        buildSrcProject.redundantClassFile.assertDoesNotExist()
        hasDescendants(buildSrcProject.jarFile, buildSrcProject.mainClassFile.name)

        when:
        succeeds helpTaskPath

        then:
        executedAndNotSkipped(helpTaskPath)
        !output.contains(buildSrcCleanTaskPath)
        !result.output.contains(buildSrcProject.buildDirCleanupMessage)
        !output.contains(buildSrcProject.buildDirCleanupMessage)
        buildSrcProject.mainClassFile.assertIsFile()
        buildSrcProject.redundantClassFile.assertDoesNotExist()
        hasDescendants(buildSrcProject.jarFile, buildSrcProject.mainClassFile.name)

        where:
        buildDirName | defaultDir | description
        'build'      | true       | 'default build directory'
        'out'        | false      | 'reconfigured build directory'
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
            apply plugin: 'base'
            
            task copy(type: Copy) {
                from file('source')
                into file('target')
            }
            
            clean {
                delete 'target'
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

    // We don't yet record the previous outputs and automatically clean them up
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

    // We don't track outputs that used to be outputs and are no longer "owned" by a task
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
            apply plugin: 'base'
            
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

    // We don't track all outputs from tasks, so we won't delete target/
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
        skipped(taskPath)
        targetFile1.assertDoesNotExist()
        targetFile2.assertDoesNotExist()
    }

    // We don't track outputs from tasks that were removed, so we won't remove its outputs.
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
                into temporaryDir
            }
        """

        when:
        def result = runWithMostRecentFinalRelease(taskPath)

        then:
        result.executedTasks.contains(taskPath)
        targetFile1.assertIsFile()
        targetFile2.assertIsFile()

        when:
        def newTaskPath = ':newCopy'

        buildFile << """
            tasks.remove(copy)

            task newCopy(type: Copy) {
                from file('source')
                into temporaryDir
            }
        """
        forceDelete(sourceFile2)
        succeeds newTaskPath

        then:
        executedAndNotSkipped(newTaskPath)
        targetFile1.assertIsFile()
        targetFile2.assertDoesNotExist()
    }

    private ExecutionResult runWithMostRecentFinalRelease(String... tasks) {
        mostRecentFinalReleaseExecuter.withTasks(tasks).run()
    }

    static boolean hasDescendants(File jarFile, String... relativePaths) {
        new JarTestFixture(jarFile).hasDescendants(relativePaths)
    }

    static String createProjectName(int projectNo) {
        "project$projectNo"
    }
}
