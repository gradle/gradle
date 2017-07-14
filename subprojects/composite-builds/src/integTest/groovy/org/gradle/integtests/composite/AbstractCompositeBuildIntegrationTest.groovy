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

package org.gradle.integtests.composite

import com.google.common.collect.Lists
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.build.BuildTestFile
import org.gradle.internal.execution.ExecuteTaskBuildOperationType
import org.gradle.internal.operations.BuildOperationType
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.test.fixtures.file.TestFile
/**
 * Tests for composite build.
 */
abstract class AbstractCompositeBuildIntegrationTest extends AbstractIntegrationSpec {
    BuildTestFile buildA
    List<File> includedBuilds = []
    def operations = new BuildOperationsFixture(executer, temporaryFolder)


    def setup() {
        buildTestFixture.withBuildInSubDir()
        buildA = singleProjectBuild("buildA") {
            buildFile << """
                apply plugin: 'java'
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
"""
        }
    }

    def dependency(BuildTestFile sourceBuild = buildA, String notation) {
        sourceBuild.buildFile << """
            dependencies {
                compile '${notation}'
            }
"""
    }

    def includeBuild(File build, def mappings = "") {
        if (mappings == "") {
            buildA.settingsFile << """
                includeBuild('${build.toURI()}')
"""
        } else {
            buildA.settingsFile << """
                includeBuild('${build.toURI()}') {
                    dependencySubstitution {
                        $mappings
                    }
                }
"""

        }
    }

    protected void execute(BuildTestFile build, String[] tasks, Iterable<String> arguments = []) {
        prepare(build, arguments)
        succeeds(tasks)
        assertSingleBuildOperationsTree()
    }

    protected void execute(BuildTestFile build, String task, Iterable<String> arguments = []) {
        prepare(build, arguments)
        succeeds(task)
        assertSingleBuildOperationsTree()
    }

    protected void fails(BuildTestFile build, String task, Iterable<String> arguments = []) {
        prepare(build, arguments)
        fails(task)
        assertSingleBuildOperationsTree()
    }

    private void prepare(BuildTestFile build, Iterable<String> arguments) {
        executer.inDirectory(build)

        List<File> includedBuilds = Lists.newArrayList(includedBuilds)
        includedBuilds.each {
            includeBuild(it)
        }
        for (String arg : arguments) {
            executer.withArgument(arg)
        }
    }

    protected void executed(String... tasks) {
        def executedTasks = result.executedTasks
        for (String task : tasks) {
            containsOnce(executedTasks, task)
        }
    }

    protected static void containsOnce(List<String> tasks, String task) {
        assert tasks.contains(task)
        assert tasks.findAll({ it == task }).size() == 1
    }

    void assertTaskExecuted(String build, String taskPath) {
        assert operations.first(ExecuteTaskBuildOperationType) {
            it.details.buildPath == build && it.details.taskPath == taskPath
        }
    }

    void assertTaskExecutedOnce(String build, String taskPath) {
        operations.only(ExecuteTaskBuildOperationType) {
            it.details.buildPath == build && it.details.taskPath == taskPath
        }
    }

    void assertTaskNotExecuted(String build, String taskPath) {
        operations.none(ExecuteTaskBuildOperationType) {
            it.details.buildPath == build && it.details.taskPath == taskPath
        }
    }

    void assertSingleBuildOperationsTree() {
        assert operations.roots().size() == 1
    }

    class TimePoint implements Comparable<TimePoint> {
        private final boolean end
        private final long time
        private final BuildOperationRecord operation

        TimePoint(BuildOperationRecord operation, long time) {
            this(operation, time, false)
        }

        TimePoint(BuildOperationRecord operation, long time, boolean end) {
            this.operation = operation
            this.time = time
            this.end = end
        }

        @Override
        int compareTo(TimePoint o) {
            if (o.time > time) {
                return -1
            }
            else if (o.time < time) {
                return 1
            }
            else {
                if (end) {
                    return -1
                } else {
                    return 0
                }
            }
        }

        @Override
        String toString() {
            if (end) {
                time + "E"
            } else {
                time + "S"
            }
        }
    }

    void assertConcurrentOperationsDoNotExceed(Class<BuildOperationType> type, int maximumConcurrentOperations, boolean concurrencyExpected=false) {
        def allOperations = operations.all(type)

        List<TimePoint> points = []

        allOperations.each {
            points.add(new TimePoint(it, it.startTime))
            points.add(new TimePoint(it, it.endTime, true))
        }

        def concurrencySeen = false
        def concurrentOperations = []
        points.sort().each {
            if (it.end) {
                concurrentOperations.remove(it.operation)
            } else {
                concurrentOperations.add(it.operation)
            }
            assert concurrentOperations.size() <= maximumConcurrentOperations
            if (concurrentOperations.size() > 1) {
                concurrencySeen = true
            }
        }
        if (concurrencyExpected) {
            assert concurrencySeen : "No operations were executed concurrently"
        }
    }

    TestFile getRootDir() {
        temporaryFolder.testDirectory
    }


    def applyPlugin(BuildTestFile build, String name = "pluginBuild") {
        build.buildFile << """
            buildscript {
                dependencies {
                    classpath 'org.test:$name:1.0'
                }
            }
            apply plugin: 'org.test.plugin.$name'
"""
    }

    def pluginProjectBuild(String name) {
        def className = name.capitalize()
        singleProjectBuild(name) {
            buildFile << """
apply plugin: 'java-gradle-plugin'

gradlePlugin {
    plugins {
        ${name} {
            id = "org.test.plugin.$name"
            implementationClass = "org.test.$className"
        }
    }
}
"""
            file("src/main/java/org/test/${className}.java") << """
package org.test;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;

public class ${className} implements Plugin<Project> {
    public void apply(Project project) {
        Task task = project.task("taskFrom${className}");
        task.setGroup("Plugin");
    }
}
"""
        }

    }
}
