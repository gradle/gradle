/*
 * Copyright 2008 the original author or authors.
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

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.Ignore;

import java.io.File;

public class ProjectLoadingIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void handlesSimilarlyNamedBuildFilesInSameDirectory() {
        File buildFile1 = getTestBuildFile("similarly-named build.gradle");
        File buildFile2 = getTestBuildFile("similarly_named_build_gradle");
        assertEquals(buildFile1.getParentFile(), buildFile2.getParentFile());

        usingBuildFile(buildFile1).runTasks("build");

        usingBuildFile(buildFile2).runTasks("other-build");

        usingBuildFile(buildFile1).runTasks("build");
    }

    @Test
    public void handlesWhitespaceOnlySettingsAndBuildFiles() {
        testFile("settings.gradle").write("   \n  ");
        testFile("build.gradle").write("   ");
        inTestDirectory().showTaskList();
    }

    @Test
    public void embeddedBuildFileIgnoresBuildAndScriptFiles() {
        File rootDir = getTestDir();
        testFile("settings.gradle").write("throw new RuntimeException()");
        testFile("build.gradle").write("throw new RuntimeException()");
        inDirectory(rootDir).usingBuildScript("Task task = createTask('do-stuff')").runTasks("do-stuff");
    }

    @Test
    public void canDetermineRootProjectAndCurrentProjectBasedOnCurrentDirectory() {
        File rootDir = getTestDir();
        File childDir = new File(rootDir, "child");

        testFile("settings.gradle").write("include('child')");
        testFile("build.gradle").write("createTask('do-stuff')");
        testFile("child/build.gradle").write("createTask('do-stuff')");

        inDirectory(rootDir).withSearchUpwards().runTasks(":do-stuff", "child:do-stuff").assertTasksExecuted(":do-stuff", ":child:do-stuff");
        inDirectory(childDir).withSearchUpwards().runTasks(":do-stuff", "do-stuff").assertTasksExecuted(":do-stuff", ":child:do-stuff");
    }

    @Test
    public void canDetermineRootProjectAndCurrentProjectBasedOnBuildFileName() {
        testFile("settings.gradle").write("include('child')");

        TestFile rootBuildFile = testFile("build.gradle");
        rootBuildFile.write("createTask('do-stuff')");

        TestFile childBuildFile = testFile("child/build.gradle");
        childBuildFile.write("createTask('do-stuff')");

        usingBuildFile(rootBuildFile).withSearchUpwards().runTasks(":do-stuff", "child:do-stuff").assertTasksExecuted(":do-stuff", ":child:do-stuff");
        usingBuildFile(childBuildFile).withSearchUpwards().runTasks(":do-stuff", "do-stuff").assertTasksExecuted(":do-stuff", ":child:do-stuff");
    }

    @Test
    public void settingsFileTakesPrecedenceOverBuildFileInSameDirectory() {
        testFile("settings.gradle").write("rootProject.buildFileName = 'root.gradle'");
        testFile("root.gradle").write("createTask('do-stuff')");
        
        TestFile buildFile = testFile("build.gradle");
        buildFile.write("throw new RuntimeException()");

        inTestDirectory().runTasks("do-stuff");
    }

    @Test
    public void explicitBuildFileTakesPrecedenceOverSettingsFileInSameDirectory() {
        testFile("settings.gradle").write("rootProject.buildFileName = 'root.gradle'");
        testFile("root.gradle").write("throw new RuntimeException()");

        TestFile buildFile = testFile("build.gradle");
        buildFile.write("createTask('do-stuff')");

        usingBuildFile(buildFile).runTasks("do-stuff");
    }

    @Test
    public void ignoresMultiProjectBuildInParentDirectory() {
        testFile("settings.gradle").write("include('another')");
        testFile("gradle.properties").writelns("prop=value2", "otherProp=value");

        File subDirectory = new File(getTestDir(), "subdirectory");
        TestFile buildFile = testFile(subDirectory, "build.gradle");
        buildFile.writelns("createTask('do-stuff') {",
                "assertThat(prop, equalTo('value'))",
                "assertTrue(!project.hasProperty('otherProp'))",
                "}");
        testFile("subdirectory/gradle.properties").write("prop=value");

        inDirectory(subDirectory).withSearchUpwards().runTasks("do-stuff");
        usingBuildFile(buildFile).withSearchUpwards().runTasks("do-stuff");
    }

    @Test
    public void multiProjectBuildCanHaveMultipleProjectsWithSameProjectDir() {
        testFile("settings.gradle").writelns(
            "include 'child1', 'child2'",
            "project(':child1').projectDir = new File(settingsDir, 'shared')",
            "project(':child2').projectDir = new File(settingsDir, 'shared')"
        );
        testFile("shared/build.gradle").write("createTask('do-stuff')");

        inTestDirectory().runTasks("do-stuff").assertTasksExecuted(":child1:do-stuff", ":child2:do-stuff");
    }

    @Test
    public void multiProjectBuildCanHaveSeveralProjectsWithSameBuildFile() {
        testFile("settings.gradle").writelns(
            "include 'child1', 'child2'",
            "project(':child1').buildFileName = '../child.gradle'",
            "project(':child2').buildFileName = '../child.gradle'"
        );
        testFile("child.gradle").write("createTask('do-stuff')");

        inTestDirectory().runTasks("do-stuff").assertTasksExecuted(":child1:do-stuff", ":child2:do-stuff");
    }

    @Test
    public void multiProjectBuildCanHaveSettingsFileAndRootBuildFileInSubDir() {
        File buildFilesDir = new File(getTestDir(), "root");
        TestFile settingsFile = testFile(buildFilesDir, "settings.gradle");
        settingsFile.writelns(
            "includeFlat 'child'",
            "rootProject.projectDir = new File(settingsDir, '..')",
            "rootProject.buildFileName = 'root/build.gradle'"
        );

        TestFile rootBuildFile = testFile(buildFilesDir, "build.gradle");
        rootBuildFile.write("createTask('do-stuff')");

        TestFile childBuildFile = testFile("child/build.gradle");
        childBuildFile.write("createTask('do-stuff')");

        inTestDirectory().usingSettingsFile(settingsFile).runTasks("do-stuff").assertTasksExecuted(":do-stuff", ":child:do-stuff");
        usingBuildFile(rootBuildFile).runTasks("do-stuff").assertTasksExecuted(":do-stuff", ":child:do-stuff");
        usingBuildFile(childBuildFile).usingSettingsFile(settingsFile).runTasks("do-stuff").assertTasksExecuted(":child:do-stuff");
    }

    @Test @Ignore
    public void doesSomethingUsefulWhenNoMatchingProjectFound() {
        fail();
    }
}
