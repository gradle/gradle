/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.plugins.buildcomparison.gradle.internal;

import org.gradle.api.plugins.buildcomparison.gradle.GradleBuildInvocationSpec;
import org.gradle.util.GFileUtils;
import org.gradle.util.GUtil;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.util.List;

/**
 * Comparing focussed decorator.
 *
 * This object cannot add any state as equals/hashCode delegate.
 */
public class ComparableGradleBuildInvocationSpec implements GradleBuildInvocationSpec {

    public static final GradleVersion PROJECT_OUTCOMES_MINIMUM_VERSION = GradleVersion.version("1.2");
    public static final GradleVersion EXEC_MINIMUM_VERSION = GradleVersion.version("1.0");

    private final GradleBuildInvocationSpec spec;

    public ComparableGradleBuildInvocationSpec(GradleBuildInvocationSpec spec) {
        this.spec = spec;
    }

    public File getProjectDir() {
        return spec.getProjectDir();
    }

    public void setProjectDir(Object projectDir) {
        spec.setProjectDir(projectDir);
    }

    public GradleVersion getGradleVersion() {
        return spec.getGradleVersion();
    }

    public void setGradleVersion(String gradleVersion) {
        spec.setGradleVersion(gradleVersion);
    }

    public List<String> getTasks() {
        return spec.getTasks();
    }

    public void setTasks(Iterable<String> tasks) {
        spec.setTasks(tasks);
    }

    public List<String> getArguments() {
        return spec.getArguments();
    }

    public void setArguments(Iterable<String> arguments) {
        spec.setArguments(arguments);
    }

    public boolean isExecutable() {
        return getGradleVersion().compareTo(EXEC_MINIMUM_VERSION) >= 0;
    }

    public boolean isCanObtainProjectOutcomesModel() {
        GradleVersion version = getGradleVersion();
        boolean isMinimumVersionOrHigher = version.compareTo(PROJECT_OUTCOMES_MINIMUM_VERSION) >= 0;
        //noinspection SimplifiableIfStatement
        if (isMinimumVersionOrHigher) {
            return true;
        } else {
            // Special handling for snapshots/RCs of the minimum version
            return version.getVersionBase().equals(PROJECT_OUTCOMES_MINIMUM_VERSION.getVersionBase());
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        } else if (obj instanceof ComparableGradleBuildInvocationSpec) {
            return spec.equals(((ComparableGradleBuildInvocationSpec) obj).spec);
        } else {
            return spec.equals(obj);
        }
    }

    @Override
    public int hashCode() {
        return spec.hashCode();
    }

    public String describeRelativeTo(File relativeTo) {
        return "dir: '" + GFileUtils.relativePath(relativeTo, getProjectDir()) + "'"
                + ", tasks: '" + GUtil.join(getTasks(), " ") + "'"
                + ", arguments: '" + GUtil.join(getArguments(), " ") + "'"
                + ", gradleVersion: " + getGradleVersion();
    }
}
