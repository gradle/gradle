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

import static org.apache.commons.io.FileUtils.*;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.Ignore;

import java.io.File;
import java.io.IOException;

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
    public void embeddedBuildFileIgnoresBuildAndScriptFiles() throws IOException {
        File rootDir = getTestDir();
        testFile("settings.gradle").write("throw new RuntimeException()");
        testFile("build.gradle").write("throw new RuntimeException()");
        inDirectory(rootDir).usingBuildScript("Task task = createTask('do-stuff')").runTasks("do-stuff");
    }

    @Test
    public void canDeterminesRootProjectAndCurrentProjectBasedOnCurrentDirectory() throws IOException {
        File rootDir = getTestDir();
        File childDir = new File(rootDir, "child");

        testFile("settings.gradle").write("include('child')");
        testFile("build.gradle").write("createTask('do-stuff')");
        testFile("child/build.gradle").write("createTask('do-stuff')");

        inDirectory(rootDir).withSearchUpwards().runTasks(":do-stuff", "child:do-stuff").assertTasksExecuted(":do-stuff", ":child:do-stuff");
        inDirectory(childDir).withSearchUpwards().runTasks(":do-stuff", "do-stuff").assertTasksExecuted(":do-stuff", ":child:do-stuff");
    }

    @Test
    public void handlesProjectInSubdirectoryOfAnotherMultiProjectBuild() throws IOException {
        testFile("settings.gradle").write("include('another')");
        testFile("gradle.properties").writelns("prop=value2", "otherProp=value");

        TestFile buildFile = testFile("subdirectory/build.gradle");
        buildFile.writelns("createTask('do-stuff') {",
                "assertThat(prop, equalTo('value'))",
                "assertTrue(!project.hasProperty('otherProp'))",
                "}");
        testFile("subdirectory/gradle.properties").write("prop=value");

        usingBuildFile(buildFile).withSearchUpwards().runTasks("do-stuff");
    }

}
