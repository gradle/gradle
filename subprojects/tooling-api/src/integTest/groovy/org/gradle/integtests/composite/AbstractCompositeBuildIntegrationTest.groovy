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

import com.beust.jcommander.internal.Lists
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile

/**
 * Tests for composite build.
 */
abstract class AbstractCompositeBuildIntegrationTest extends AbstractIntegrationSpec {
    List builds = []

    protected void execute(File build, String task, Iterable<String> arguments = []) {
        prepare(build, arguments)
        succeeds(task)
    }

    protected void fails(File build, String task, Iterable<String> arguments = []) {
        prepare(build, arguments)
        fails(task)
    }

    private void prepare(File build, Iterable<String> arguments) {
        executer.inDirectory(build)

        List<File> participants = Lists.newArrayList(builds)
        participants.remove(build)
        for (File participant : participants) {
            executer.withArgument("--participant")
            executer.withArgument(participant.path)
        }
        for (String arg : arguments) {
            executer.withArgument(arg)
        }
    }

    protected void executed(String... tasks) {
        def executedTasks = result.executedTasks
        for (String task : tasks) {
            assert executedTasks.contains(task)
            assert executedTasks.findAll({ it == task }).size() == 1
        }
    }

    TestFile getRootDir() {
        temporaryFolder.testDirectory
    }

    def populate(String projectName, @DelegatesTo(ProjectTestFile) Closure cl) {
        def project = new ProjectTestFile(rootDir, projectName)
        project.with(cl)
        project
    }

    def singleProjectBuild(String projectName, @DelegatesTo(ProjectTestFile) Closure cl = {}) {
        def project = populate(projectName) {
            settingsFile << """
                rootProject.name = '${rootProjectName}'
            """

            buildFile << """
                group = 'org.test'
                version = '1.0'
            """
            file('src/main/java/Dummy.java') << "public class Dummy {}"
        }
        project.with(cl)
        return project
    }

    def multiProjectBuild(String projectName, List<String> subprojects, @DelegatesTo(ProjectTestFile) Closure cl = {}) {
        String subprojectList = subprojects.collect({"'$it'"}).join(',')
        def rootMulti = populate(projectName) {
            settingsFile << """
                rootProject.name = '${rootProjectName}'
                include ${subprojectList}
            """

            buildFile << """
                allprojects {
                    group = 'org.test'
                    version = '1.0'
                }
            """
        }
        rootMulti.with(cl)
        rootMulti.file('src/main/java/Dummy.java') << "public class Dummy {}"
        subprojects.each {
            rootMulti.file(it, 'src/main/java/Dummy.java') << "public class Dummy {}"
        }
        return rootMulti
    }

    TestFile projectDir(String project) {
        file(project)
    }

    static class ProjectTestFile extends TestFile {
        private final String projectName

        ProjectTestFile(TestFile rootDir, String projectName) {
            super(rootDir, [ projectName ])
            this.projectName = projectName
        }
        String getRootProjectName() {
            projectName
        }
        TestFile getBuildFile() {
            file("build.gradle")
        }
        TestFile getSettingsFile() {
            file("settings.gradle")
        }
        void addChildDir(String name) {
            file(name).file("build.gradle") << "// Dummy child build"
        }
    }

}
