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
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.fixtures.versions.ReleasedVersionDistributions
import org.gradle.test.fixtures.archive.JarTestFixture
import org.gradle.util.GFileUtils

class StaleOutputGradleUpgradeIntegrationTest extends AbstractIntegrationSpec {

    private final ReleasedVersionDistributions releasedVersionDistributions = new ReleasedVersionDistributions()
    private final GradleExecuter mostRecentFinalReleaseExecuter = releasedVersionDistributions.mostRecentFinalRelease.executer(temporaryFolder, getBuildContext())

    def cleanup() {
        mostRecentFinalReleaseExecuter.cleanup()
    }

    @NotYetImplemented
    def "sources files are removed indented for compilation"() {
        given:
        def sourceFile1 = file('src/main/java/Main.java')
        sourceFile1 << 'public class Main {}'
        def sourceFile2 = file('src/main/java/Redundant.java')
        sourceFile2 << 'public class Redundant {}'
        def classFile1 = file('build/classes/main/Main.class')
        def classFile2 = file('build/classes/main/Redundant.class')
        def jarFile = file('build/libs/test.jar')
        def taskName = ':jar'

        settingsFile << "rootProject.name = 'test'"
        buildFile << "apply plugin: 'java'"

        when:
        def result = runWithMostRecentFinalRelease(taskName)

        then:
        result.executedTasks.contains(taskName)
        classFile1.assertIsFile()
        classFile2.assertIsFile()
        new JarTestFixture(jarFile).hasDescendants('Main.class', 'Redundant.class')

        when:
        GFileUtils.forceDelete(sourceFile2)
        succeeds taskName

        then:
        executedAndNotSkipped(taskName)
        classFile1.assertIsFile()
        classFile2.assertDoesNotExist()
        new JarTestFixture(jarFile).hasDescendants('Main.class')
    }

    @NotYetImplemented
    def "tasks have common output directories"() {
        def sourceFile1 = file('source1/source.txt')
        sourceFile1 << 'a'
        def sourceFile2 = file('source2/source.txt')
        sourceFile2 << 'b'
        def targetFile1 = file('target/source.txt')
        def targetFile2 = file('target/source.txt')
        def taskName = ':copyAll'

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
        def result = runWithMostRecentFinalRelease(taskName)

        then:
        result.executedTasks.containsAll(taskName, ':copy1', ':copy2')
        targetFile1.assertIsFile()
        targetFile2.assertIsFile()

        when:
        succeeds taskName

        then:
        executedAndNotSkipped(taskName, ':copy1', ':copy2')
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
        def taskName = ':copy'

        buildFile << """
            task copy(type: Copy) {
                from file('source')
                into file('target')
            }
        """

        when:
        def result = runWithMostRecentFinalRelease(taskName)

        then:
        result.executedTasks.contains(taskName)
        targetFile1.assertIsFile()
        targetFile2.assertIsFile()

        when:
        GFileUtils.forceDelete(sourceFile2)
        succeeds taskName

        then:
        executedAndNotSkipped(taskName)
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
        def taskName = ':customCopy'

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
        def result = runWithMostRecentFinalRelease(taskName)

        then:
        result.executedTasks.contains(taskName)
        targetFile1.assertIsFile()
        targetFile2.assertIsFile()

        when:
        buildFile << """
            customCopy {
                targetDir = file('newTarget')
            }
        """
        succeeds taskName

        then:
        executedAndNotSkipped(taskName)
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
        def taskName = ':copyAll'

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
        def result = runWithMostRecentFinalRelease(taskName)

        then:
        result.executedTasks.containsAll(taskName, ':copy1', ':copy2')
        targetFile1.assertIsFile()
        targetFile2.assertIsFile()

        when:
        buildFile << """
            def reducedTaskDependencies = copyAll.taskDependencies.getDependencies(copyAll) - copy2
            copyAll.dependsOn = reducedTaskDependencies
        """
        succeeds taskName

        then:
        executedAndNotSkipped(taskName, ':copy1')
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
        def taskName = ':copy'

        buildFile << """
            task copy(type: Copy) {
                from file('source')
                into file('target')
            }
        """

        when:
        def result = runWithMostRecentFinalRelease(taskName)

        then:
        result.executedTasks.contains(taskName)
        targetFile1.assertIsFile()
        targetFile2.assertIsFile()

        when:
        GFileUtils.forceDelete(sourceFile1)
        GFileUtils.forceDelete(sourceFile2)
        succeeds taskName

        then:
        executedAndNotSkipped(taskName)
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
        def taskName = ':copy'

        buildFile << """
            task copy(type: Copy) {
                from file('source')
                into file('target')
            }
        """

        when:
        def result = runWithMostRecentFinalRelease(taskName)

        then:
        result.executedTasks.contains(taskName)
        targetFile1.assertIsFile()
        targetFile2.assertIsFile()

        when:
        buildFile << """
            tasks.remove(copy)

            task newCopy(type: Copy) {
                from file('source')
                into file('target')
            }
        """
        succeeds 'newCopy'

        then:
        executedAndNotSkipped(':newCopy')
        targetFile1.assertIsFile()
        targetFile2.assertIsFile()
    }

    private ExecutionResult runWithMostRecentFinalRelease(String... tasks) {
        mostRecentFinalReleaseExecuter.withTasks(tasks).run()
    }
}
