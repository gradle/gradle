/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.ide.visualstudio.fixtures;

import com.google.common.collect.Lists;
import org.apache.commons.io.FileUtils;
import org.gradle.integtests.fixtures.executer.ExecutionFailure;
import org.gradle.integtests.fixtures.executer.ExecutionResult;
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionFailure;
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult;
import org.gradle.internal.UncheckedException;
import org.gradle.nativeplatform.fixtures.AvailableToolChains;
import org.gradle.nativeplatform.fixtures.msvcpp.VisualStudioLocatorTestFixture;
import org.gradle.test.fixtures.file.ExecOutput;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.test.fixtures.file.TestFileHelper;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.gradle.ide.fixtures.IdeCommandLineUtil.buildEnvironment;

public class MSBuildExecutor {
    public enum MSBuildAction {
        BUILD,
        CLEAN;

        @Override
        public String toString() {
            return this.name().toLowerCase();
        }
    }

    private final List<String> args = new ArrayList<String>();
    private final AvailableToolChains.InstalledToolChain toolChain;
    private TestFile workingDir;
    private String projectName;

    public MSBuildExecutor(TestFile workingDir, AvailableToolChains.InstalledToolChain toolChain) {
        this.workingDir = workingDir;
        this.toolChain = toolChain;
    }

    public MSBuildExecutor withWorkingDir(TestFile workingDir) {
        this.workingDir = workingDir;
        return this;
    }

    public MSBuildExecutor withSolution(SolutionFile visualStudioSolution) {
        TestFile solutionFile = new TestFile(visualStudioSolution.getFile());
        solutionFile.assertIsFile();
        return addArguments(solutionFile.getAbsolutePath());
    }

    public MSBuildExecutor withConfiguration(String configurationName) {
        return addArguments("/p:Configuration=" + configurationName);
    }

    public MSBuildExecutor withProject(String projectName) {
        this.projectName = projectName;
        return this;
    }

    public MSBuildExecutor withArgument(String arg) {
        this.args.add(arg);
        return this;
    }

    private MSBuildExecutor addArguments(String... args) {
        this.args.addAll(Arrays.asList(args));
        return this;
    }

    private File getOutputsDir() {
        return workingDir.file("output");
    }

    private void cleanupOutputDir() {
        try {
            FileUtils.deleteDirectory(getOutputsDir());
            getOutputsDir().mkdir();
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private List<ExecutionOutput> getOutputFiles() {
        List<ExecutionOutput> outputFiles = Lists.newArrayList();
        for (File executionDir : getOutputsDir().listFiles()) {
            if (executionDir.isDirectory()) {
                outputFiles.add(new ExecutionOutput(new File(executionDir, "output.txt"), new File(executionDir, "error.txt")));
            }
        }
        return outputFiles;
    }

    public List<ExecutionResult> succeeds() {
        return succeeds(MSBuildAction.BUILD);
    }

    public List<ExecutionResult> succeeds(MSBuildAction action) {
        cleanupOutputDir();
        List<ExecutionResult> results = Lists.newArrayList();

        withArgument(toTargetArgument(action));
        ExecOutput result = findMSBuild().execute(args, buildEnvironment(workingDir));

        System.out.println(result.getOut());
        if (getOutputFiles().isEmpty()) {
            results.add(OutputScrapingExecutionResult.from(trimLines(result.getOut()), trimLines(result.getError())));
        } else {
            for (ExecutionOutput output : getOutputFiles()) {
                String gradleStdout = fileContents(output.stdout);
                String gradleStderr = fileContents(output.stderr);

                System.out.println(gradleStdout);
                System.out.println(gradleStderr);

                results.add(OutputScrapingExecutionResult.from(trimLines(gradleStdout), trimLines(gradleStderr)));
            }
        }
        System.out.println(result.getError());

        return results;
    }

    public ExecutionFailure fails() {
        return fails(MSBuildAction.BUILD);
    }

    public ExecutionFailure fails(MSBuildAction action) {
        cleanupOutputDir();

        withArgument(toTargetArgument(action));
        ExecOutput result = findMSBuild().execWithFailure(args, buildEnvironment(workingDir));

        List<ExecutionOutput> outputs = getOutputFiles();
        assert outputs.size() == 1;
        String gradleStdout = fileContents(outputs.get(0).stdout);
        String gradleStderr = fileContents(outputs.get(0).stderr);
        System.out.println(result.getOut());
        System.out.println(gradleStdout);
        System.out.println(gradleStderr);
        System.out.println(result.getError());

        return OutputScrapingExecutionFailure.from(trimLines(gradleStdout), trimLines(gradleStderr));
    }

    public ExecutionResult run() {
        return run(MSBuildAction.BUILD);
    }

    public ExecutionResult run(MSBuildAction action) {
        cleanupOutputDir();

        withArgument(toTargetArgument(action));
        ExecOutput result = new TestFileHelper(findMSBuild()).execute(args, buildEnvironment(workingDir));

        List<ExecutionOutput> outputs = getOutputFiles();
        String gradleStdout = result.getOut();
        String gradleStderr = result.getError();
        if (!outputs.isEmpty()) {
            gradleStdout = fileContents(outputs.get(0).stdout);
            gradleStderr = fileContents(outputs.get(0).stderr);
        }
        System.out.println(result.getOut());
        System.out.println(gradleStdout);
        System.out.println(gradleStderr);
        System.out.println(result.getError());

        if (result.getExitCode() != 0) {
            return OutputScrapingExecutionFailure.from(trimLines(gradleStdout), trimLines(gradleStderr));
        }
        return OutputScrapingExecutionResult.from(trimLines(gradleStdout), trimLines(gradleStderr));
    }

    private static String fileContents(File file) {
        try {
            // TODO this should not be using the default charset because it's not an input and might introduce flakiness
            return FileUtils.readFileToString(file, Charset.defaultCharset());
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private String trimLines(String s) {
        return s.replaceAll("\r?\n\\s+", "\n");
    }

    private String toTargetArgument(MSBuildAction action) {
        String result = "";
        if (projectName != null) {
            result = projectName;
        }
        if (!(projectName != null && action == MSBuildAction.BUILD)) {
            if (projectName != null) {
                result += ":";
            }
            result += action.toString();
        }
        return "/t:" + result;
    }

    private TestFile findMSBuild() {
        return new TestFile(new MSBuildVersionLocator(VisualStudioLocatorTestFixture.getVswhereLocator()).getMSBuildInstall(toolChain));
    }

    private static class ExecutionOutput {
        private final File stdout;
        private final File stderr;

        public ExecutionOutput(File stdout, File stderr) {
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }
}
