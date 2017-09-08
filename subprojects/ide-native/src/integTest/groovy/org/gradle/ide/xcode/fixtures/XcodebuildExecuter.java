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

import com.google.common.collect.Lists;
import org.gradle.integtests.fixtures.executer.ExecutionResult;
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult;
import org.gradle.test.fixtures.file.ExecOutput;
import org.gradle.test.fixtures.file.TestFile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class XcodebuildExecuter {
    private final TestFile baseDir;
    private final List<String> args = new ArrayList<String>();
    public XcodebuildExecuter(TestFile baseDir) {
        this.baseDir = baseDir;
    }

    public XcodebuildExecuter withProject(String pathToBundle) {
        return withProject(baseDir.file(pathToBundle));
    }

    public XcodebuildExecuter withProject(File xcodeProjectBundle) {
        new TestFile(xcodeProjectBundle).assertIsDir();
        return withArguments("-project", xcodeProjectBundle.getAbsolutePath());
    }

    public XcodebuildExecuter withWorkspace(String pathToBundle) {
        return withWorkspace(baseDir.file(pathToBundle));
    }

    public XcodebuildExecuter withWorkspace(File bundle) {
        new TestFile(bundle).assertIsDir();
        return withArguments("-workspace", bundle.getAbsolutePath());
    }

    public XcodebuildExecuter withScheme(String schemeName) {
        withArgument("-scheme");
        withArgument(schemeName);
        return this;
    }

    public XcodebuildExecuter withConfiguration(String configurationName) {
        withArgument("-configuration");
        withArgument(configurationName);
        return this;
    }

    public XcodebuildExecuter withArguments(String... args) {
        return withArguments(Lists.newArrayList(args));
    }

    public XcodebuildExecuter withArguments(List<String> args) {
        this.args.clear();
        this.args.addAll(args);
        return this;
    }

    public XcodebuildExecuter withArgument(String arg) {
        this.args.add(arg);
        return this;
    }

    public ExecutionResult run(String action) {
        args.add(action);
        return run();
    }

    public ExecutionResult run() {
        ExecOutput result = new TestFile("/usr/bin/xcodebuild").exec(args.toArray());
        return new OutputScrapingExecutionResult(result.getOut(), result.getError());
    }

    public ExecutionResult succeeds() {
        ExecOutput result = new TestFile("/usr/bin/xcodebuild").exec(args.toArray());
        return new OutputScrapingExecutionResult(result.getOut(), result.getError());
    }
}
