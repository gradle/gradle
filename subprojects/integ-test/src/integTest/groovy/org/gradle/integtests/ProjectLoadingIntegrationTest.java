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
import org.gradle.test.fixtures.file.TestFile;
import org.junit.Test;
import spock.lang.Issue;

import java.io.File;

import static org.hamcrest.CoreMatchers.startsWith;

public class ProjectLoadingIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void handlesSimilarlyNamedBuildFilesInSameDirectory() {
        TestFile buildFile1 = testFile("similarly-named build.gradle").write("task build");
        TestFile buildFile2 = testFile("similarly_named_build_gradle").write("task 'other-build'");

        executer.expectDeprecationWarnings(1);
        usingBuildFile(buildFile1).withTasks("build").run();

        executer.expectDeprecationWarnings(1);
        usingBuildFile(buildFile2).withTasks("other-build").run();

        executer.expectDeprecationWarnings(1);
        usingBuildFile(buildFile1).withTasks("build").run();
    }

    @Test
    public void handlesWhitespaceOnlySettingsAndBuildFiles() {
        testFile("settings.gradle").write("   \n  ");
        testFile("build.gradle").write("   ");
        inTestDirectory().withTasks("help").run();
    }

    @Test
    public void canDetermineRootProjectAndDefaultProjectBasedOnCurrentDirectory() {
        File rootDir = getTestDirectory();
        File childDir = new File(rootDir, "child");

        testFile("settings.gradle").write("include('child')");
        testFile("build.gradle").write("task('do-stuff')");
        testFile("child/build.gradle").write("task('do-stuff')");

        inDirectory(rootDir).withTasks("do-stuff").run().assertTasksExecuted(":do-stuff", ":child:do-stuff");
        inDirectory(rootDir).withTasks(":do-stuff").run().assertTasksExecuted(":do-stuff");

        inDirectory(childDir).withTasks("do-stuff").run().assertTasksExecuted(":child:do-stuff");
        inDirectory(childDir).withTasks(":do-stuff").run().assertTasksExecuted(":do-stuff");
    }

    @Test
    public void canDetermineRootProjectAndDefaultProjectBasedOnProjectDirectory() {
        File rootDir = getTestDirectory();
        File childDir = new File(rootDir, "child");

        testFile("settings.gradle").write("include('child')");
        testFile("build.gradle").write("task('do-stuff')");
        testFile("child/build.gradle").write("task('do-stuff')");

        usingProjectDir(rootDir).withTasks("do-stuff").run().assertTasksExecuted(":do-stuff", ":child:do-stuff");
        usingProjectDir(rootDir).withTasks(":do-stuff").run().assertTasksExecuted(":do-stuff");

        usingProjectDir(childDir).withTasks("do-stuff").run().assertTasksExecuted(":child:do-stuff");
        usingProjectDir(childDir).withTasks(":do-stuff").run().assertTasksExecuted(":do-stuff");
    }

    @Test
    public void canDetermineRootProjectAndDefaultProjectBasedOnBuildFile() {
        testFile("settings.gradle").write("include('child')");

        TestFile rootBuildFile = testFile("build.gradle");
        rootBuildFile.write("task('do-stuff')");

        TestFile childBuildFile = testFile("child/build.gradle");
        childBuildFile.write("task('do-stuff')");

        executer.withTasks("do-stuff").run().assertTasksExecuted(":do-stuff", ":child:do-stuff");
        executer.withTasks(":do-stuff").run().assertTasksExecuted(":do-stuff");

        executer.inDirectory(testFile("child")).withTasks("do-stuff").run().assertTasksExecuted(":child:do-stuff");
        executer.inDirectory(testFile("child")).withTasks(":do-stuff").run().assertTasksExecuted(":do-stuff");
    }

    @Test
    public void buildFailsWhenMultipleProjectsMeetDefaultProjectCriteria() {
        testFile("settings.gradle").writelns(
            "include 'child'",
            "project(':child').projectDir = rootProject.projectDir");
        testFile("build.gradle").write("// empty");

        ExecutionFailure result = inTestDirectory().withTasks("test").runWithFailure();
        result.assertThatDescription(startsWith("Multiple projects in this build have project directory"));

        result = usingProjectDir(getTestDirectory()).withTasks("test").runWithFailure();
        result.assertThatDescription(startsWith("Multiple projects in this build have project directory"));

        executer.expectDeprecationWarnings(1);
        result = usingBuildFile(testFile("build.gradle")).withTasks("test").runWithFailure();
        result.assertThatDescription(startsWith("Multiple projects in this build have build file"));
    }

    @Test
    public void buildFailsWhenSpecifiedBuildFileIsNotAFile() {
        TestFile file = testFile("unknown");

        ExecutionFailure result = usingBuildFile(file).runWithFailure();
        result.assertHasDescription("The specified build file '" + file + "' does not exist.");

        file.createDir();

        result = usingBuildFile(file).runWithFailure();
        result.assertHasDescription("The specified build file '" + file + "' is not a file.");
    }

    @Test
    public void buildFailsWhenSpecifiedProjectDirectoryIsNotADirectory() {
        TestFile file = testFile("unknown");

        ExecutionFailure result = usingProjectDir(file).runWithFailure();
        result.assertHasDescription("The specified project directory '" + file + "' does not exist.");

        file.createFile();

        result = usingProjectDir(file).runWithFailure();
        result.assertHasDescription("The specified project directory '" + file + "' is not a directory.");
    }

    @Test
    public void buildFailsWhenSpecifiedSettingsFileIsNotAFile() {
        TestFile file = testFile("unknown");

        ExecutionFailure result = inTestDirectory().usingSettingsFile(file).runWithFailure();
        result.assertHasDescription("The specified settings file '" + file + "' does not exist.");

        file.createDir();

        result = inTestDirectory().usingSettingsFile(file).runWithFailure();
        result.assertHasDescription("The specified settings file '" + file + "' is not a file.");
    }

    @Issue("gradle/gradle#4672")
    @Test
    public void buildFailsWhenSpecifiedInitScriptIsNotAFile() {
        TestFile file = testFile("unknown");

        ExecutionFailure result = inTestDirectory().usingInitScript(file).runWithFailure();
        result.assertHasDescription("The specified initialization script '" + file + "' does not exist.");

        file.createDir();

        result = inTestDirectory().usingInitScript(file).runWithFailure();
        result.assertHasDescription("The specified initialization script '" + file + "' is not a file.");
    }

    @Issue("gradle/gradle#4672")
    @Test
    public void buildFailsWhenOneInitScriptDoesNotExist() {
        TestFile initFile1 = testFile("init1").write("// empty");
        TestFile initFile2 = testFile("init2");

        ExecutionFailure result = inTestDirectory().usingInitScript(initFile1).usingInitScript(initFile2).runWithFailure();
        result.assertHasDescription("The specified initialization script '" + initFile2 + "' does not exist.");
    }

    @Test
    public void buildFailsWhenSpecifiedSettingsFileDoesNotContainMatchingProject() {
        TestFile settingsFile = testFile("settings.gradle");
        settingsFile.write("rootProject.name = 'foo'");

        TestFile projectDir = testFile("project dir");
        TestFile buildFile = projectDir.file("build.gradle").createFile();

        ExecutionFailure result = usingProjectDir(projectDir).withTasks("tasks").runWithFailure();
        result.assertHasDescription(String.format("Project directory '%s' is not part of the build defined by settings file '%s'.", projectDir, settingsFile));

        executer.expectDeprecationWarnings(2);
        result = usingBuildFile(buildFile).usingSettingsFile(settingsFile).withTasks("tasks").runWithFailure();
        result.assertHasDescription(String.format("Build file '%s' is not part of the build defined by settings file '%s'.", buildFile, settingsFile));
    }

    @Test
    public void settingsFileTakesPrecedenceOverBuildFileInSameDirectory() {
        testFile("settings.gradle").write("rootProject.buildFileName = 'root.gradle'");
        testFile("root.gradle").write("task('do-stuff')");

        TestFile buildFile = testFile("build.gradle");
        buildFile.write("throw new RuntimeException()");

        inTestDirectory().withTasks("do-stuff").run();
        usingProjectDir(getTestDirectory()).withTasks("do-stuff").run();
    }

    @Test
    public void settingsFileInParentDirectoryTakesPrecedenceOverBuildFile() {
        testFile("settings.gradle").writelns(
            "include 'child'",
            "project(':child').buildFileName = 'child.gradle'"
        );

        TestFile subDirectory = getTestDirectory().file("child");
        subDirectory.file("build.gradle").write("throw new RuntimeException()");
        subDirectory.file("child.gradle").write("task('do-stuff')");

        inDirectory(subDirectory).withTasks("do-stuff").run();
        usingProjectDir(subDirectory).withTasks("do-stuff").run();
    }

    @Test
    public void explicitBuildFileTakesPrecedenceOverBuildFileDefinedInSettingsInSameDirectory() {
        testFile("settings.gradle").write("rootProject.buildFileName = 'root.gradle'");
        testFile("root.gradle").write("throw new RuntimeException()");

        TestFile buildFile = testFile("build.gradle");
        buildFile.write("task('do-stuff')");

        executer.expectDeprecationWarnings(1);
        usingBuildFile(buildFile).withTasks("do-stuff").run();
    }

    @Test
    public void buildFailsWhenNestedBuildHasNoSettingsFile() {
        TestFile settingsFile = testFile("settings.gradle").write("include 'another'");

        TestFile subDirectory = getTestDirectory().file("sub");
        TestFile subBuildFile = subDirectory.file("build.gradle").write("");
        subDirectory.file("build.gradle").write("");

        ExecutionFailure result = inDirectory(subDirectory).withTasks("tasks").runWithFailure();
        result.assertHasDescription(String.format("Project directory '%s' is not part of the build defined by settings file '%s'.", subDirectory, settingsFile));

        executer.expectDeprecationWarnings(1);
        result = usingBuildFile(subBuildFile).inDirectory(subDirectory).withTasks("tasks").runWithFailure();
        result.assertHasDescription(String.format("Build file '%s' is not part of the build defined by settings file '%s'.", subBuildFile, settingsFile));

        result = usingProjectDir(subDirectory).withTasks("tasks").runWithFailure();
        result.assertHasDescription(String.format("Project directory '%s' is not part of the build defined by settings file '%s'.", subDirectory, settingsFile));
    }

    @Test
    public void canTargetRootProjectDirectoryFromSubDirectory() {
        testFile("settings.gradle").write("include 'another'");

        TestFile subDirectory = getTestDirectory().file("sub");
        subDirectory.file("build.gradle").write("throw new RuntimeException()");

        usingProjectDir(getTestDirectory()).inDirectory(subDirectory).withTasks("help").run();
    }

    @Test
    public void specifyingCustomSettingsFileIsDeprecated() {
        testFile("settings.gradle").write("include 'another'");

        TestFile subDirectory = file("sub");
        TestFile subSettingsFile = subDirectory.file("renamed_settings.gradle").write("");
        subDirectory.file("build.gradle").write("");

        executer.expectDocumentedDeprecationWarning("Specifying custom settings file location has been deprecated. This is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#configuring_custom_build_layout");
        inDirectory(subDirectory).usingSettingsFile(subSettingsFile).withTasks("help").run();
    }

    @Test
    public void specifyingCustomBuildFileIsDeprecated() {
        testFile("settings.gradle").write("include 'another'");
        TestFile renamedBuildGradle = file("renamed_build.gradle").createFile();

        executer.expectDocumentedDeprecationWarning("Specifying custom build file location has been deprecated. This is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#configuring_custom_build_layout");
        executer.usingBuildScript(renamedBuildGradle).withTasks("help").run();
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
        TestFile buildFilesDir = getTestDirectory().file("root");
        TestFile relocatedSettingsFile = buildFilesDir.file("settings.gradle");
        relocatedSettingsFile.writelns(
            "includeFlat 'child'",
            "rootProject.projectDir = new File(settingsDir, '..')",
            "rootProject.buildFileName = 'root/build.gradle'"
        );

        TestFile rootBuildFile = buildFilesDir.file("build.gradle");
        rootBuildFile.write("task('do-stuff', dependsOn: ':child:task')");

        TestFile childBuildFile = testFile("child/build.gradle");
        childBuildFile.writelns("task('do-stuff')", "task('task')");

        executer.expectDeprecationWarnings(1);
        usingProjectDir(getTestDirectory()).usingSettingsFile(relocatedSettingsFile).withTasks("do-stuff").run().assertTasksExecuted(":child:task", ":do-stuff", ":child:do-stuff").assertTaskOrder(":child:task", ":do-stuff");
        executer.expectDeprecationWarnings(1);
        usingBuildFile(rootBuildFile).withTasks("do-stuff").run().assertTasksExecuted(":child:task", ":do-stuff", ":child:do-stuff").assertTaskOrder(":child:task", ":do-stuff");
        executer.expectDeprecationWarnings(2);
        usingBuildFile(childBuildFile).usingSettingsFile(relocatedSettingsFile).withTasks("do-stuff").run().assertTasksExecutedInOrder(":child:do-stuff");
    }

    @Test
    public void multiProjectBuildCanHaveAllProjectsAsChildrenOfSettingsDir() {
        TestFile settingsFile = testFile("settings.gradle");
        settingsFile.writelns(
            "rootProject.projectDir = new File(settingsDir, 'root')",
            "include 'sub'",
            "project(':sub').projectDir = new File(settingsDir, 'root/sub')"
        );

        getTestDirectory().createDir("root").file("build.gradle").writelns("allprojects { task thing }");

        inTestDirectory().withTasks(":thing").run().assertTasksExecuted(":thing");
        inTestDirectory().withTasks(":sub:thing").run().assertTasksExecuted(":sub:thing");
    }

    @Test
    public void usesRootProjectAsDefaultProjectWhenInSettingsDir() {
        TestFile settingsDir = testFile("gradle");
        TestFile settingsFile = settingsDir.file("settings.gradle");
        settingsFile.writelns(
            "rootProject.projectDir = new File(settingsDir, '../root')",
            "include 'sub'",
            "project(':sub').projectDir = new File(settingsDir, '../root/sub')"
        );
        getTestDirectory().createDir("root").file("build.gradle").writelns("allprojects { task thing }");

        inDirectory(settingsDir).withTasks("thing").run().assertTasksExecuted(":thing", ":sub:thing");
    }

    @Test
    public void rootProjectDirectoryAndBuildFileDoNotHaveToExistWhenInSettingsDir() {
        TestFile settingsDir = testFile("gradle");
        TestFile settingsFile = settingsDir.file("settings.gradle");
        settingsFile.writelns(
            "rootProject.projectDir = new File(settingsDir, '../root')",
            "include 'sub'",
            "project(':sub').projectDir = new File(settingsDir, '../sub')"
        );
        getTestDirectory().createDir("sub").file("build.gradle").writelns("task thing");

        inDirectory(settingsDir).withTasks("thing").run().assertTasksExecuted(":sub:thing");
    }

    @Test
    public void settingsFileGetsIgnoredWhenUsingSettingsOnlyDirectoryAsProjectDirectory() {
        TestFile settingsDir = testFile("gradle");
        TestFile settingsFile = settingsDir.file("settings.gradle");
        settingsFile.writelns(
            "rootProject.projectDir = new File(settingsDir, '../root')"
        );
        getTestDirectory().createDir("root").file("build.gradle").writelns("task thing");

        inTestDirectory().withArguments("-p", settingsDir.getAbsolutePath()).withTasks("thing").runWithFailure()
            .assertHasDescription("Task 'thing' not found in root project 'gradle'.");
    }
}
