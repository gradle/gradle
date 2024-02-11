/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.ide.xcode.fixtures;

import org.gradle.integtests.fixtures.executer.ExecutionFailure;
import org.gradle.integtests.fixtures.executer.ExecutionResult;
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionFailure;
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult;
import org.gradle.test.fixtures.file.ExecOutput;
import org.gradle.test.fixtures.file.TestFile;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.gradle.ide.fixtures.IdeCommandLineUtil.buildEnvironment;
import static org.junit.Assert.assertTrue;

public class XcodebuildExecutor {
    public enum XcodeAction {
        BUILD,
        CLEAN,
        TEST;

        @Override
        public String toString() {
            return this.name().toLowerCase();
        }
    }

    private final List<String> args = new ArrayList<String>();
    private final TestFile testDirectory;

    public XcodebuildExecutor(TestFile testDirectory) {
        this(testDirectory, testDirectory.file(".xcode-derived"));
    }

    private XcodebuildExecutor(TestFile testDirectory, File derivedData) {
        addArguments("-derivedDataPath", derivedData.getAbsolutePath());
        this.testDirectory = testDirectory;
    }

    public XcodebuildExecutor withProject(XcodeProjectPackage xcodeProject) {
        TestFile projectDir = new TestFile(xcodeProject.getDir());
        projectDir.assertIsDir();
        return addArguments("-project", projectDir.getAbsolutePath());
    }

    public XcodebuildExecutor withWorkspace(XcodeWorkspacePackage xcodeWorkspace) {
        TestFile workspaceDir = new TestFile(xcodeWorkspace.getDir());
        workspaceDir.assertIsDir();
        return addArguments("-workspace", workspaceDir.getAbsolutePath());
    }

    public XcodebuildExecutor withScheme(String schemeName) {
        return addArguments("-scheme", schemeName);
    }

    public XcodebuildExecutor withConfiguration(String configurationName) {
        return addArguments("-configuration", configurationName);
    }

    public XcodebuildExecutor withArgument(String arg) {
        this.args.add(arg);
        return this;
    }

    private XcodebuildExecutor addArguments(String... args) {
        this.args.addAll(Arrays.asList(args));
        return this;
    }

    public ExecutionResult succeeds() {
        return succeeds(XcodeAction.BUILD);
    }

    public ExecutionResult succeeds(XcodeAction action) {
        withArgument(action.toString());
        ExecOutput result = findXcodeBuild().execute(args, buildEnvironment(testDirectory));
        System.out.println(result.getOut());
        return OutputScrapingExecutionResult.from(result.getOut(), result.getError());
    }

    public ExecutionFailure fails() {
        return fails(XcodeAction.BUILD);
    }

    // Xcode 14.2 seems to return the error in the format
    // that can't be recognized by OutputScrapingExecutionFailure.
    // Returns raw output of `xcodebuild`
    public ExecOutput execWithFailure(XcodeAction action) {
        withArgument(action.toString());
        return findXcodeBuild().execWithFailure(args, buildEnvironment(testDirectory));
    }

    public ExecutionFailure fails(XcodeAction action) {
        withArgument(action.toString());
        ExecOutput result = findXcodeBuild().execWithFailure(args, buildEnvironment(testDirectory));
        // stderr of Gradle is redirected to stdout of xcodebuild tool. To work around, we consider xcodebuild stdout and stderr as
        // the error output only if xcodebuild failed most likely due to Gradle.
        System.out.println(result.getOut());
        System.out.println(result.getError());
        return OutputScrapingExecutionFailure.from(result.getOut(), result.getError());
    }

    private TestFile findXcodeBuild() {
        TestFile xcodebuild = new TestFile("/usr/bin/xcodebuild");
        assertTrue("This test requires xcode to be installed in " + xcodebuild.getAbsolutePath(), xcodebuild.exists());
        return xcodebuild;
    }
}
