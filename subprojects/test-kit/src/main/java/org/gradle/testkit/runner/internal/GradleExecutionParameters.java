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

import java.io.File;
import java.io.Writer;
import java.util.List;

public class GradleExecutionParameters {
    private final File gradleUserHome;
    private final File projectDir;
    private final List<String> buildArgs;
    private final List<String> jvmArgs;
    private final ClassPath injectedClassPath;
    private final boolean embedded;
    private final Writer standardOutput;
    private final Writer standardError;

    public GradleExecutionParameters(File gradleUserHome, File projectDir, List<String> buildArgs, List<String> jvmArgs, ClassPath injectedClassPath,
                                     boolean embedded, Writer standardOutput, Writer standardError) {
        this.gradleUserHome = gradleUserHome;
        this.projectDir = projectDir;
        this.buildArgs = buildArgs;
        this.jvmArgs = jvmArgs;
        this.injectedClassPath = injectedClassPath;
        this.embedded = embedded;
        this.standardOutput = standardOutput;
        this.standardError = standardError;
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

    public Writer getStandardOutput() {
        return standardOutput;
    }

    public Writer getStandardError() {
        return standardError;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        GradleExecutionParameters that = (GradleExecutionParameters) o;

        if (embedded != that.embedded) {
            return false;
        }
        if (gradleUserHome != null ? !gradleUserHome.equals(that.gradleUserHome) : that.gradleUserHome != null) {
            return false;
        }
        if (projectDir != null ? !projectDir.equals(that.projectDir) : that.projectDir != null) {
            return false;
        }
        if (buildArgs != null ? !buildArgs.equals(that.buildArgs) : that.buildArgs != null) {
            return false;
        }
        if (jvmArgs != null ? !jvmArgs.equals(that.jvmArgs) : that.jvmArgs != null) {
            return false;
        }
        if (injectedClassPath != null ? !injectedClassPath.equals(that.injectedClassPath) : that.injectedClassPath != null) {
            return false;
        }
        if (standardOutput != null ? !standardOutput.equals(that.standardOutput) : that.standardOutput != null) {
            return false;
        }
        return !(standardError != null ? !standardError.equals(that.standardError) : that.standardError != null);

    }

    @Override
    public int hashCode() {
        int result = gradleUserHome != null ? gradleUserHome.hashCode() : 0;
        result = 31 * result + (projectDir != null ? projectDir.hashCode() : 0);
        result = 31 * result + (buildArgs != null ? buildArgs.hashCode() : 0);
        result = 31 * result + (jvmArgs != null ? jvmArgs.hashCode() : 0);
        result = 31 * result + (injectedClassPath != null ? injectedClassPath.hashCode() : 0);
        result = 31 * result + (embedded ? 1 : 0);
        result = 31 * result + (standardOutput != null ? standardOutput.hashCode() : 0);
        result = 31 * result + (standardError != null ? standardError.hashCode() : 0);
        return result;
    }
}
