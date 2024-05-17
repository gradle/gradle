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

import groovy.test.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.StaleOutputJavaProject
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout
import org.gradle.integtests.fixtures.versions.ReleasedVersionDistributions
import org.gradle.internal.jvm.Jvm
import org.gradle.util.GradleVersion
import org.junit.Assume
import spock.lang.Issue

import static org.gradle.integtests.fixtures.StaleOutputJavaProject.JAR_TASK_NAME
import static org.gradle.util.internal.GFileUtils.forceDelete

@IntegrationTestTimeout(240)
class StaleOutputHistoryLossIntegrationTest extends AbstractIntegrationSpec {

    private final ReleasedVersionDistributions releasedVersionDistributions = new ReleasedVersionDistributions()
    private final GradleExecuter mostRecentReleaseExecuter = releasedVersionDistributions.mostRecentRelease.executer(temporaryFolder, buildContext)

    def cleanup() {
        mostRecentReleaseExecuter.cleanup()
    }

    def setup() {
        buildFile << "apply plugin: 'base'\n"
        // When adding support for a new JDK version, the previous release might not work with it yet.
        Assume.assumeTrue(releasedVersionDistributions.mostRecentRelease.worksWith(Jvm.current()))
    }

    GradleVersion getMostRecentReleaseVersion() {
        releasedVersionDistributions.mostRecentRelease.version
    }

    @Issue("https://github.com/gradle/gradle/issues/821")
    def "production class files are removed in a single project build for #description"() {
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
        javaProject.mainClassFile.assertIsFile()
        javaProject.redundantClassFile.assertIsFile()
        javaProject.assertJarHasDescendants(javaProject.mainClassFile.name, javaProject.redundantClassFile.name)

        when:
        forceDelete(javaProject.redundantSourceFile)
        succeeds JAR_TASK_NAME

        then:
        javaProject.mainClassFile.assertIsFile()
        javaProject.redundantClassFile.assertDoesNotExist()
        javaProject.assertJarHasDescendants(javaProject.mainClassFile.name)

        when:
        succeeds JAR_TASK_NAME

        then:
        javaProject.assertBuildTasksSkipped(result)

        where:
        buildDirName | defaultDir | description
        'build'      | true       | 'default build directory'
        'out'        | false      | 'reconfigured build directory'
    }

    def "production class files outside of 'build' are removed"() {
        given:
        def javaProject = new StaleOutputJavaProject(testDirectory, 'out')
        buildFile << """
            apply plugin: 'java'

            sourceSets {
                main {
                    java.destinationDirectory.set(file('out/classes/java/main'))
                }
            }
        """.stripIndent()

        when:
        result = runWithMostRecentFinalRelease(JAR_TASK_NAME)

        then:
        javaProject.mainClassFile.assertIsFile()
        javaProject.redundantClassFile.assertIsFile()

        when:
        forceDelete(javaProject.redundantSourceFile)
        succeeds JAR_TASK_NAME

        then:
        javaProject.mainClassFile.assertIsFile()
        javaProject.redundantClassFile.assertDoesNotExist()

        when:
        succeeds JAR_TASK_NAME

        then:
        javaProject.assertBuildTasksSkipped(result)
    }

    // We register the output directory before task execution and would have deleted output files at the end of configuration.
    @Issue("https://github.com/gradle/gradle/issues/821")
    @UnsupportedWithConfigurationCache
    def "production class files are removed even if output directory is reconfigured during execution phase"() {
        given:
        def javaProject = new StaleOutputJavaProject(testDirectory)
        buildFile << """
            apply plugin: 'java'

            task configureCompileJava {
                doLast {
                    compileJava.destinationDirectory.set(file('build/out'))
                    jar.from compileJava.destinationDirectory
                }
            }

            compileJava.dependsOn configureCompileJava
        """
        when:
        result = runWithMostRecentFinalRelease(JAR_TASK_NAME)

        then:
        javaProject.assertBuildTasksExecuted(result)
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
        javaProject.assertJarHasDescendants(javaProject.mainClassFile.name)
        javaProject.mainClassFileAlternate.assertIsFile()
        javaProject.redundantClassFileAlternate.assertDoesNotExist()

        when:
        succeeds JAR_TASK_NAME

        then:
        javaProject.assertBuildTasksSkipped(result)
    }

    @Issue("https://github.com/gradle/gradle/issues/821")
    def "production class files are removed in a multi-project build executed #description"(String[] arguments, String description) {
        given:
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
            javaProject.mainClassFile.assertIsFile()
            javaProject.redundantClassFile.assertDoesNotExist()
            javaProject.assertJarHasDescendants(javaProject.mainClassFile.name)
        }

        and:
        succeeds arguments

        where:
        arguments                                              | description
        [JAR_TASK_NAME]                                        | 'without additional argument'
        [JAR_TASK_NAME, '--parallel']                          | 'in parallel'
        [JAR_TASK_NAME, '--parallel', '--configure-on-demand'] | 'in parallel and configure on demand enabled'
    }

    @Issue("https://github.com/gradle/gradle/issues/821")
    def "production class files are removed in a multi-project build executed when a single project is built #description"(String singleTask, List arguments, String description) {
        given:
        def projectCount = 3
        def javaProjects = (1..projectCount).collect {
            def projectName = createProjectName(it)
            file("${projectName}/build.gradle") << "apply plugin: 'java'"
            new StaleOutputJavaProject(testDirectory, "build", projectName)
        }

        file('settings.gradle') << "include ${(1..projectCount).collect { "'${createProjectName(it)}'" }.join(',')}"

        when:
        // Build everything first
        mostRecentReleaseExecuter.withArguments(arguments)
        result = runWithMostRecentFinalRelease(JAR_TASK_NAME)

        then:
        javaProjects.each { javaProject ->
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

    @Issue("https://github.com/gradle/gradle/issues/821")
    def "task history is deleted"() {
        def javaProject = new StaleOutputJavaProject(testDirectory)
        buildFile << "apply plugin: 'java'"

        when:
        succeeds JAR_TASK_NAME

        then:
        javaProject.assertBuildTasksExecuted(result)
        javaProject.mainClassFile.assertIsFile()
        javaProject.redundantClassFile.assertIsFile()
        javaProject.assertJarHasDescendants(javaProject.mainClassFile.name, javaProject.redundantClassFile.name)

        when:
        file('.gradle').assertIsDir().deleteDir()
        forceDelete(javaProject.redundantSourceFile)
        succeeds JAR_TASK_NAME

        then:
        javaProject.assertBuildTasksExecuted(result)
        javaProject.mainClassFile.assertIsFile()
        javaProject.redundantClassFile.assertDoesNotExist()
        javaProject.assertJarHasDescendants(javaProject.mainClassFile.name)

        when:
        succeeds JAR_TASK_NAME

        then:
        javaProject.assertBuildTasksSkipped(result)
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

    @NotYetImplemented
    // We don't clean anything which is not safe to delete
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
        result.assertTaskExecuted(taskPath)
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
        result.assertTaskExecuted(taskPath)
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
        result.assertTasksExecuted(taskPath, ':copy1', ':copy2')
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

    def "inputs become empty for task"() {
        given:
        def sourceFile1 = file('source/source1.txt')
        sourceFile1 << 'a'
        def sourceFile2 = file('source/source2.txt')
        sourceFile2 << 'b'
        def targetFile1 = file('build/target/source1.txt')
        def targetFile2 = file('build/target/source2.txt')
        def taskPath = ':customCopy'

        buildFile << """
            task customCopy(type: CustomCopy) {
                sourceDir = fileTree('source')
                targetDir = file('build/target')
            }

            class CustomCopy extends DefaultTask {
                @InputFiles
                @SkipWhenEmpty
                @IgnoreEmptyDirectories
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
        result.assertTaskExecuted(taskPath)
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

    def "task is replaced"() {
        given:
        def sourceFile1 = file('source/source1.txt')
        sourceFile1 << 'a'
        def sourceFile2 = file('source/source2.txt')
        sourceFile2 << 'b'
        def targetFile1 = file('build/target/source1.txt')
        def targetFile2 = file('build/target/source2.txt')
        def taskPath = ':copy'

        buildFile << """
            tasks.register("copy", Copy) {
                from file('source')
                into 'build/target'
            }
        """

        when:
        result = runWithMostRecentFinalRelease(taskPath)

        then:
        result.assertTaskExecuted(taskPath)
        targetFile1.assertIsFile()
        targetFile2.assertIsFile()

        when:
        buildFile << """
            tasks.replace("copy", Copy).configure {
                from file('source')
                into 'build/target'
            }
        """
        forceDelete(sourceFile2)
        succeeds taskPath

        then:
        executedAndNotSkipped(taskPath)
        targetFile1.assertIsFile()
        targetFile2.assertDoesNotExist()
    }

    private ExecutionResult runWithMostRecentFinalRelease(String... tasks) {
        result = mostRecentReleaseExecuter.withTasks(tasks).run()
        result
    }

    static String createProjectName(int projectNo) {
        "project$projectNo"
    }
}
