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

import com.google.common.collect.Sets;
import org.gradle.api.Transformer;
import org.gradle.integtests.fixtures.executer.ExecutionFailure;
import org.gradle.integtests.fixtures.executer.ExecutionResult;
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionFailure;
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult;
import org.gradle.test.fixtures.file.ExecOutput;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.testng.Assert.assertTrue;

public class XcodebuildExecuter {
    private final List<String> args = new ArrayList<String>();
    private final File gradleUserHome;

    public XcodebuildExecuter(File gradleUserHome, File derivedData) {
        this.gradleUserHome = gradleUserHome;
        addArguments("-derivedDataPath", derivedData.getAbsolutePath());
    }

    public XcodebuildExecuter withProject(XcodeProjectPackage xcodeProject) {
        TestFile projectDir = new TestFile(xcodeProject.getDir());
        projectDir.assertIsDir();
        return addArguments("-project", projectDir.getAbsolutePath());
    }

    public XcodebuildExecuter withWorkspace(XcodeWorkspacePackage xcodeWorkspace) {
        TestFile workspaceDir = new TestFile(xcodeWorkspace.getDir());
        workspaceDir.assertIsDir();
        return addArguments("-workspace", workspaceDir.getAbsolutePath());
    }

    public XcodebuildExecuter withScheme(String schemeName) {
        return addArguments("-scheme", schemeName);
    }

    public XcodebuildExecuter withConfiguration(String configurationName) {
        return addArguments("-configuration", configurationName);
    }

    public XcodebuildExecuter withArgument(String arg) {
        this.args.add(arg);
        return this;
    }

    private XcodebuildExecuter addArguments(String... args) {
        this.args.addAll(Arrays.asList(args));
        return this;
    }

    public ExecutionResult succeeds() {
        ExecOutput result = findXcodeBuild().execute(args, buildEnvironment());
        return new OutputScrapingExecutionResult(result.getOut(), result.getError());
    }

    public ExecutionFailure fails() {
        ExecOutput result = findXcodeBuild().execWithFailure(args, buildEnvironment());
        // stderr of Gradle is redirected to stdout of xcodebuild tool. To work around, we consider xcodebuild stdout and stderr as
        // the error output only if xcodebuild failed most likely due to Gradle.
        return new OutputScrapingExecutionFailure(result.getOut(), result.getOut() + "\n" + result.getError());
    }

    private List<String> buildEnvironment() {
        Set<String> envvars = Sets.newLinkedHashSet();
        envvars.add("GRADLE_USER_HOME=" + gradleUserHome);
        CollectionUtils.collect(System.getenv().entrySet(), envvars, new Transformer<String, Map.Entry<String, String>>() {
            @Override
            public String transform(Map.Entry<String, String> envvar) {
                return envvar.getKey() + "=" + envvar.getValue();
            }
        });
        return CollectionUtils.toList(envvars);
    }

    private TestFile findXcodeBuild() {
        TestFile xcodebuild = new TestFile("/usr/bin/xcodebuild");
        assertTrue(xcodebuild.exists(), "This test requires xcode to be installed in " + xcodebuild.getAbsolutePath());
        return xcodebuild;
    }
}
