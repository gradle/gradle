/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.integtests;

import org.gradle.integtests.fixtures.AbstractIntegrationTest;
import org.gradle.integtests.fixtures.executer.ExecutionFailure;
import org.gradle.util.TestFile;
import org.junit.Test;

import java.io.File;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;

public class ProjectLoadingIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void handlesSimilarlyNamedBuildFilesInSameDirectory() {
        TestFile buildFile1 = testFile("similarly-named build.gradle").write("task build");
        TestFile buildFile2 = testFile("similarly_named_build_gradle").write("task 'other-build'");

        usingBuildFile(buildFile1).withTasks("build").run();

        usingBuildFile(buildFile2).withTasks("other-build").run();

        usingBuildFile(buildFile1).withTasks("build").run();
    }

    @Test
    public void handlesWhitespaceOnlySettingsAndBuildFiles() {
        testFile("settings.gradle").write("   \n  ");
        testFile("build.gradle").write("   ");
        inTestDirectory().withTaskList().run();
    }

    @Test
    public void canDetermineRootProjectAndDefaultProjectBasedOnCurrentDirectory() {
        File rootDir = getTestWorkDir();
        File childDir = new File(rootDir, "child");

        testFile("settings.gradle").write("include('child')");
        testFile("build.gradle").write("task('do-stuff')");
        testFile("child/build.gradle").write("task('do-stuff')");

        inDirectory(rootDir).withSearchUpwards().withTasks("do-stuff").run().assertTasksExecuted(":do-stuff", ":child:do-stuff");
        inDirectory(rootDir).withSearchUpwards().withTasks(":do-stuff").run().assertTasksExecuted(":do-stuff");

        inDirectory(childDir).withSearchUpwards().withTasks("do-stuff").run().assertTasksExecuted(":child:do-stuff");
        inDirectory(childDir).withSearchUpwards().withTasks(":do-stuff").run().assertTasksExecuted(":do-stuff");
    }

    @Test
    public void canDetermineRootProjectAndDefaultProjectBasedOnProjectDirectory() {
        File rootDir = getTestWorkDir();
        File childDir = new File(rootDir, "child");

        testFile("settings.gradle").write("include('child')");
        testFile("build.gradle").write("task('do-stuff')");
        testFile("child/build.gradle").write("task('do-stuff')");

        usingProjectDir(rootDir).withSearchUpwards().withTasks("do-stuff").run().assertTasksExecuted(":do-stuff", ":child:do-stuff");
        usingProjectDir(rootDir).withSearchUpwards().withTasks(":do-stuff").run().assertTasksExecuted(":do-stuff");

        usingProjectDir(childDir).withSearchUpwards().withTasks("do-stuff").run().assertTasksExecuted(":child:do-stuff");
        usingProjectDir(childDir).withSearchUpwards().withTasks(":do-stuff").run().assertTasksExecuted(":do-stuff");
    }

    @Test
    public void canDetermineRootProjectAndDefaultProjectBasedOnBuildFile() {
        testFile("settings.gradle").write("include('child')");

        TestFile rootBuildFile = testFile("build.gradle");
        rootBuildFile.write("task('do-stuff')");

        TestFile childBuildFile = testFile("child/build.gradle");
        childBuildFile.write("task('do-stuff')");

        usingBuildFile(rootBuildFile).withSearchUpwards().withTasks("do-stuff").run().assertTasksExecuted(":do-stuff", ":child:do-stuff");
        usingBuildFile(rootBuildFile).withSearchUpwards().withTasks(":do-stuff").run().assertTasksExecuted(":do-stuff");

        usingBuildFile(childBuildFile).withSearchUpwards().withTasks("do-stuff").run().assertTasksExecuted(":child:do-stuff");
        usingBuildFile(childBuildFile).withSearchUpwards().withTasks(":do-stuff").run().assertTasksExecuted(":do-stuff");
    }

    @Test
    public void buildFailsWhenMultipleProjectsMeetDefaultProjectCriteria() {
        testFile("settings.gradle").writelns(
            "include 'child'",
            "project(':child').projectDir = rootProject.projectDir");
        testFile("build.gradle").write("// empty");

        ExecutionFailure result = inTestDirectory().withTasks("test").runWithFailure();
        result.assertThatDescription(startsWith("Could not select the default project for this build. Multiple projects in this build have project directory"));

        result = usingProjectDir(getTestWorkDir()).withTasks("test").runWithFailure();
        result.assertThatDescription(startsWith("Could not select the default project for this build. Multiple projects in this build have project directory"));

        result = usingBuildFile(testFile("build.gradle")).withTasks("test").runWithFailure();
        result.assertThatDescription(startsWith("Could not select the default project for this build. Multiple projects in this build have build file"));
    }

    @Test
    public void buildFailsWhenSpecifiedBuildFileIsNotAFile() {
        ExecutionFailure result = usingBuildFile(testFile("unknown build file")).runWithFailure();
        result.assertThatDescription(startsWith("Build file"));
        result.assertThatDescription(endsWith("does not exist."));
    }

    @Test
    public void buildFailsWhenSpecifiedProjectDirectoryIsNotADirectory() {
        ExecutionFailure result = usingProjectDir(testFile("unknown dir")).runWithFailure();
        result.assertThatDescription(startsWith("Project directory"));
        result.assertThatDescription(endsWith("does not exist."));
    }

    @Test
    public void buildFailsWhenSpecifiedSettingsFileIsNotAFile() {
        ExecutionFailure result = inTestDirectory().usingSettingsFile(testFile("unknown")).runWithFailure();
        result.assertThatDescription(startsWith("Could not read settings file"));
        result.assertThatDescription(endsWith("as it does not exist."));
    }

    @Test
    public void buildFailsWhenSpecifiedSettingsFileDoesNotContainMatchingProject() {
        TestFile settingsFile = testFile("settings.gradle");
        settingsFile.write("// empty");

        TestFile projectdir = testFile("project dir");
        projectdir.mkdirs();

        ExecutionFailure result = usingProjectDir(projectdir).usingSettingsFile(settingsFile).runWithFailure();
        result.assertThatDescription(startsWith("Could not select the default project for this build. No projects in this build have project directory"));
    }

    @Test
    public void settingsFileTakesPrecedenceOverBuildFileInSameDirectory() {
        testFile("settings.gradle").write("rootProject.buildFileName = 'root.gradle'");
        testFile("root.gradle").write("task('do-stuff')");
        
        TestFile buildFile = testFile("build.gradle");
        buildFile.write("throw new RuntimeException()");

        inTestDirectory().withTasks("do-stuff").run();
        usingProjectDir(getTestWorkDir()).withTasks("do-stuff").run();
    }

    @Test
    public void settingsFileInParentDirectoryTakesPrecedenceOverBuildFile() {
        testFile("settings.gradle").writelns(
            "include 'child'",
            "project(':child').buildFileName = 'child.gradle'"
        );

        TestFile subDirectory = getTestWorkDir().file("child");
        subDirectory.file("build.gradle").write("throw new RuntimeException()");
        subDirectory.file("child.gradle").write("task('do-stuff')");

        inDirectory(subDirectory).withSearchUpwards().withTasks("do-stuff").run();
        usingProjectDir(subDirectory).withSearchUpwards().withTasks("do-stuff").run();
    }

    @Test
    public void explicitBuildFileTakesPrecedenceOverSettingsFileInSameDirectory() {
        testFile("settings.gradle").write("rootProject.buildFileName = 'root.gradle'");
        testFile("root.gradle").write("throw new RuntimeException()");

        TestFile buildFile = testFile("build.gradle");
        buildFile.write("task('do-stuff')");

        usingBuildFile(buildFile).withTasks("do-stuff").run();
    }

    @Test
    public void ignoresMultiProjectBuildInParentDirectoryWhichDoesNotMeetDefaultProjectCriteria() {
        testFile("settings.gradle").write("include 'another'");
        testFile("gradle.properties").writelns("prop=value2", "otherProp=value");

        TestFile subDirectory = getTestWorkDir().file("subdirectory");
        TestFile buildFile = subDirectory.file("build.gradle");
        buildFile.writelns("task('do-stuff') << {",
                "assert prop == 'value'",
                "assert !project.hasProperty('otherProp')",
                "}");
        testFile("subdirectory/gradle.properties").write("prop=value");

        inDirectory(subDirectory).withSearchUpwards().withTasks("do-stuff").run();
        usingProjectDir(subDirectory).withSearchUpwards().withTasks("do-stuff").run();
        usingBuildFile(buildFile).withSearchUpwards().withTasks("do-stuff").run();
    }

    @Test
    public void multiProjectBuildCanHaveMultipleProjectsWithSameProjectDir() {
        testFile("settings.gradle").writelns(
            "include 'child1', 'child2'",
            "project(':child1').projectDir = new File(settingsDir, 'shared')",
            "project(':child2').projectDir = new File(settingsDir, 'shared')"
        );
        testFile("shared/build.gradle").write("task('do-stuff')");

        inTestDirectory().withTasks("do-stuff").run().assertTasksExecuted(":child1:do-stuff", ":child2:do-stuff");
    }

    @Test
    public void multiProjectBuildCanHaveSeveralProjectsWithSameBuildFile() {
        testFile("settings.gradle").writelns(
            "include 'child1', 'child2'",
            "project(':child1').buildFileName = '../child.gradle'",
            "project(':child2').buildFileName = '../child.gradle'"
        );
        testFile("child.gradle").write("task('do-stuff')");

        inTestDirectory().withTasks("do-stuff").run().assertTasksExecuted(":child1:do-stuff", ":child2:do-stuff");
    }

    @Test
    public void multiProjectBuildCanHaveSettingsFileAndRootBuildFileInSubDir() {
        TestFile buildFilesDir = getTestWorkDir().file("root");
        TestFile settingsFile = buildFilesDir.file("settings.gradle");
        settingsFile.writelns(
            "includeFlat 'child'",
            "rootProject.projectDir = new File(settingsDir, '..')",
            "rootProject.buildFileName = 'root/build.gradle'"
        );

        TestFile rootBuildFile = buildFilesDir.file("build.gradle");
        rootBuildFile.write("task('do-stuff', dependsOn: ':child:task')");

        TestFile childBuildFile = testFile("child/build.gradle");
        childBuildFile.writelns("task('do-stuff')", "task('task')");

        usingProjectDir(getTestWorkDir()).usingSettingsFile(settingsFile).withTasks("do-stuff").run().assertTasksExecuted(":child:task", ":do-stuff", ":child:do-stuff");
        usingBuildFile(rootBuildFile).withTasks("do-stuff").run().assertTasksExecuted(":child:task", ":do-stuff", ":child:do-stuff");
        usingBuildFile(childBuildFile).usingSettingsFile(settingsFile).withTasks("do-stuff").run().assertTasksExecuted(":child:do-stuff");
    }
}
