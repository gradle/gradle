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
import org.junit.Assume
import spock.lang.Issue
import spock.lang.Timeout
import spock.lang.Unroll

import static org.gradle.integtests.fixtures.StaleOutputJavaProject.JAR_TASK_NAME
import static org.gradle.util.GFileUtils.forceDelete

@Timeout(120)
class StaleOutputHistoryLossIntegrationTest extends AbstractIntegrationSpec {

    private final ReleasedVersionDistributions releasedVersionDistributions = new ReleasedVersionDistributions()
    // TODO: Convert this to .mostRecentFinalRelease once 4.0 is released.
    private final GradleExecuter mostRecentFinalReleaseExecuter = releasedVersionDistributions.getMostRecentSnapshot().executer(temporaryFolder, getBuildContext())

    def cleanup() {
        mostRecentFinalReleaseExecuter.cleanup()
    }

    def setup() {
        executer.beforeExecute {
            executer.withArgument('--info')
        }
    }

    @Issue("GRADLE-1501")
    @Unroll
    def "production sources files are removed in a single project build for #description"() {
        given:
        def javaProject = new StaleOutputJavaProject(testDirectory, buildDirName)
        buildFile << "apply plugin: 'java'"

        if (!defaultDir) {
            buildFile << """
                buildDir = '$javaProject.buildDirName'
            """
        }

        when:
        result = runWithMostRecentFinalRelease(JAR_TASK_NAME)

        then:
        javaProject.assertDoesNotHaveCleanupMessage(result)
        javaProject.mainClassFile.assertIsFile()
        javaProject.redundantClassFile.assertIsFile()
        javaProject.assertJarHasDescendants(javaProject.mainClassFile.name, javaProject.redundantClassFile.name)

        when:
        forceDelete(javaProject.redundantSourceFile)
        succeeds JAR_TASK_NAME

        then:
        javaProject.assertHasCleanupMessage(result)
        javaProject.mainClassFile.assertIsFile()
        javaProject.redundantClassFile.assertDoesNotExist()
        javaProject.assertJarHasDescendants(javaProject.mainClassFile.name)

        when:
        succeeds JAR_TASK_NAME

        then:
        javaProject.assertBuildTasksSkipped(result)
        javaProject.assertDoesNotHaveCleanupMessage(result)

        where:
        buildDirName | defaultDir | description
        'build'      | true       | 'default build directory'
        'out'        | false      | 'reconfigured build directory'
    }

    @Issue("https://github.com/gradle/gradle/issues/1274")
    def "buildSrc included in multi-project build as subproject"() {
        file("buildSrc/src/main/groovy/MyPlugin.groovy") << """
            import org.gradle.api.*

            class MyPlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.tasks.create("myTask") {
                        doLast {
                            def closure = {
                                println "From plugin"
                            }
                            closure()
                        }
                    }
                }
            }
        """
        file("buildSrc/build.gradle") << "apply plugin: 'groovy'"
        buildFile << """
            apply plugin: 'java'
            apply plugin: MyPlugin
            myTask.dependsOn 'jar'
        """
        settingsFile << """
            include 'buildSrc'
        """

        when:
        result = runWithMostRecentFinalRelease("myTask")
        then:
        result.assertOutputContains("From plugin")

        when:
        succeeds "myTask"
        then:
        result.assertOutputContains("From plugin")
    }

    // We register the output directory before task execution and would have deleted output files at the end of configuration.
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
        when:
        result = runWithMostRecentFinalRelease(JAR_TASK_NAME)

        then:
        javaProject.assertBuildTasksExecuted(result)
        javaProject.assertDoesNotHaveCleanupMessage(result)
        javaProject.mainClassFile.assertDoesNotExist()
        javaProject.redundantClassFile.assertDoesNotExist()
        javaProject.assertJarHasDescendants(javaProject.mainClassFile.name, javaProject.redundantClassFile.name)
        javaProject.mainClassFileAlternate.assertIsFile()
        javaProject.redundantClassFileAlternate.assertIsFile()

        when:
        forceDelete(javaProject.redundantSourceFile)
        succeeds JAR_TASK_NAME

        then:
        javaProject.assertBuildTasksExecuted(result)
        javaProject.assertHasCleanupMessage(result)
        javaProject.assertJarHasDescendants(javaProject.mainClassFile.name)
        javaProject.mainClassFileAlternate.assertIsFile()
        javaProject.redundantClassFileAlternate.assertDoesNotExist()

        when:
        succeeds JAR_TASK_NAME

        then:
        javaProject.assertBuildTasksSkipped(result)
        javaProject.assertDoesNotHaveCleanupMessage(result)
    }

    @Unroll
    @Issue("GRADLE-1501")
    def "production sources files are removed in a multi-project build executed #description"(String[] arguments, String description) {
        given:
        Assume.assumeFalse("This doesn't work with configure on demand since not all projects are configured, so not all outputs are registered.", arguments.contains("--configure-on-demand"))

        def projectCount = 3
        def javaProjects = (1..projectCount).collect {
            def projectName = createProjectName(it)
            file("${projectName}/build.gradle") << "apply plugin: 'java'"
            new StaleOutputJavaProject(testDirectory, "build", projectName)
        }

        file('settings.gradle') << "include ${(1..projectCount).collect { "'${createProjectName(it)}'" }.join(',')}"

        when:
        result = runWithMostRecentFinalRelease(arguments)

        then:
        javaProjects.each { javaProject ->
            javaProject.assertDoesNotHaveCleanupMessage(result)
            javaProject.mainClassFile.assertIsFile()
            javaProject.redundantClassFile.assertIsFile()
            javaProject.assertJarHasDescendants(javaProject.mainClassFile.name, javaProject.redundantClassFile.name)
        }

        when:
        javaProjects.each {
            forceDelete(it.redundantSourceFile)
        }
        succeeds arguments

        then:
        javaProjects.each { javaProject ->
            javaProject.assertHasCleanupMessage(result)
            javaProject.mainClassFile.assertIsFile()
            javaProject.redundantClassFile.assertDoesNotExist()
            javaProject.assertJarHasDescendants(javaProject.mainClassFile.name)
        }

        when:
        succeeds arguments

        then:
        javaProjects.each { javaProject ->
            javaProject.assertDoesNotHaveCleanupMessage(result)
        }

        where:
        arguments                                                | description
        [JAR_TASK_NAME]                                          | 'without additional argument'
        [JAR_TASK_NAME, '--parallel']                            | 'in parallel'
        [JAR_TASK_NAME, '--parallel', '--configure-on-demand']   | 'in parallel and configure on demand enabled'
    }

    @Unroll
    @Issue("GRADLE-1501")
    def "production sources files are removed in a multi-project build executed when a single project is built #description"(String singleTask, List arguments, String description) {
        given:
        Assume.assumeFalse("This doesn't work with configure on demand since not all projects are configured, so not all outputs are registered.", arguments.contains("--configure-on-demand"))

        def projectCount = 3
        def javaProjects = (1..projectCount).collect {
            def projectName = createProjectName(it)
            file("${projectName}/build.gradle") << "apply plugin: 'java'"
            new StaleOutputJavaProject(testDirectory, "build", projectName)
        }

        file('settings.gradle') << "include ${(1..projectCount).collect { "'${createProjectName(it)}'" }.join(',')}"

        when:
        // Build everything first
        mostRecentFinalReleaseExecuter.withArguments(arguments)
        result = runWithMostRecentFinalRelease(JAR_TASK_NAME)

        then:
        javaProjects.each { javaProject ->
            javaProject.assertDoesNotHaveCleanupMessage(result)
            javaProject.mainClassFile.assertIsFile()
            javaProject.redundantClassFile.assertIsFile()
            javaProject.assertJarHasDescendants(javaProject.mainClassFile.name, javaProject.redundantClassFile.name)
        }

        when:
        // Remove some files and rebuild a single task with the latest version of Gradle
        javaProjects.each {
            forceDelete(it.redundantSourceFile)
        }
        executer.withArguments(arguments)
        succeeds(singleTask)

        then:
        def builtProject = javaProjects[0]
        builtProject.assertBuildTasksExecuted(result)
        builtProject.mainClassFile.assertIsFile()
        builtProject.redundantClassFile.assertDoesNotExist()
        builtProject.assertJarHasDescendants(builtProject.mainClassFile.name)
        builtProject.assertHasCleanupMessage(result)

        when:
        // Build everything
        executer.withArguments(arguments)
        succeeds JAR_TASK_NAME

        then:
        javaProjects.each { javaProject ->
            javaProject.assertJarHasDescendants(javaProject.mainClassFile.name)
        }

        where:
        singleTask      | arguments                               | description
        ":project1:jar" | []                                      | 'without additional argument'
        ":project1:jar" | ['--parallel']                          | 'in parallel'
        ":project1:jar" | ['--parallel', '--configure-on-demand'] | 'in parallel and configure on demand enabled'
    }

    @Issue("GRADLE-1501")
    def "task history is deleted"() {
        def javaProject = new StaleOutputJavaProject(testDirectory)
        buildFile << "apply plugin: 'java'"

        when:
        succeeds JAR_TASK_NAME

        then:
        javaProject.assertBuildTasksExecuted(result)
        javaProject.assertDoesNotHaveCleanupMessage(result)
        javaProject.mainClassFile.assertIsFile()
        javaProject.redundantClassFile.assertIsFile()
        javaProject.assertJarHasDescendants(javaProject.mainClassFile.name, javaProject.redundantClassFile.name)

        when:
        file('.gradle').assertIsDir().deleteDir()
        forceDelete(javaProject.redundantSourceFile)
        succeeds JAR_TASK_NAME

        then:
        javaProject.assertBuildTasksExecuted(result)
        javaProject.assertHasCleanupMessage(result)
        javaProject.mainClassFile.assertIsFile()
        javaProject.redundantClassFile.assertDoesNotExist()
        javaProject.assertJarHasDescendants(javaProject.mainClassFile.name)

        when:
        succeeds JAR_TASK_NAME

        then:
        javaProject.assertBuildTasksSkipped(result)
        javaProject.assertDoesNotHaveCleanupMessage(result)
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
        result = runWithMostRecentFinalRelease(taskPath)

        then:
        targetFile1.assertIsFile()
        targetFile2.assertIsFile()

        when:
        succeeds taskPath

        then:
        executedAndNotSkipped(taskPath, ':copy1', ':copy2')
        targetFile1.assertIsFile()
        targetFile2.assertIsFile()
    }

    // We don't clean anything other than source set output directories
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
        result = runWithMostRecentFinalRelease(taskPath)

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
        result = runWithMostRecentFinalRelease(taskPath)

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
        result = runWithMostRecentFinalRelease(taskPath)

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
        result = runWithMostRecentFinalRelease(taskPath)

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
        result = runWithMostRecentFinalRelease(taskPath)

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

    static String createProjectName(int projectNo) {
        "project$projectNo"
    }
}
