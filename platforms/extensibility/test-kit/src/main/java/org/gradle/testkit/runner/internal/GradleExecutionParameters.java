/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.testkit.runner.internal;

import org.gradle.internal.classpath.ClassPath;

import javax.annotation.Nullable;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

public class GradleExecutionParameters {

    private final GradleProvider gradleProvider;
    private final File gradleUserHome;
    private final File projectDir;
    private final List<String> buildArgs;
    private final List<String> jvmArgs;
    private final ClassPath injectedClassPath;
    private final boolean embedded;
    private final OutputStream standardOutput;
    private final OutputStream standardError;
    private final InputStream standardInput;
    private final Map<String, String> environment;

    public GradleExecutionParameters(
        GradleProvider gradleProvider,
        File gradleUserHome,
        File projectDir,
        List<String> buildArgs,
        List<String> jvmArgs,
        ClassPath injectedClassPath,
        boolean embedded,
        OutputStream standardOutput,
        OutputStream standardError,
        InputStream standardInput,
        Map<String, String> environment) {
        this.gradleProvider = gradleProvider;
        this.gradleUserHome = gradleUserHome;
        this.projectDir = projectDir;
        this.buildArgs = buildArgs;
        this.jvmArgs = jvmArgs;
        this.injectedClassPath = injectedClassPath;
        this.embedded = embedded;
        this.standardOutput = standardOutput;
        this.standardError = standardError;
        this.standardInput = standardInput;
        this.environment = environment;
    }

    public GradleProvider getGradleProvider() {
        return gradleProvider;
    }

    public File getGradleUserHome() {
        return gradleUserHome;
    }

    public File getProjectDir() {
        return projectDir;
    }

    public List<String> getBuildArgs() {
        return buildArgs;
    }

    public List<String> getJvmArgs() {
        return jvmArgs;
    }

    public ClassPath getInjectedClassPath() {
        return injectedClassPath;
    }

    public boolean isEmbedded() {
        return embedded;
    }

    public OutputStream getStandardOutput() {
        return standardOutput;
    }

    public OutputStream getStandardError() {
        return standardError;
    }

    public InputStream getStandardInput() {
        return standardInput;
    }

    @Nullable
    public Map<String, String> getEnvironment() {
        return environment;
    }
}
