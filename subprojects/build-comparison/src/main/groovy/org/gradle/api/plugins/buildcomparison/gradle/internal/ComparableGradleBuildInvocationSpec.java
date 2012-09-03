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

import com.google.common.collect.Lists;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.plugins.buildcomparison.gradle.GradleBuildInvocationSpec;
import org.gradle.util.GFileUtils;
import org.gradle.util.GUtil;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class ComparableGradleBuildInvocationSpec implements GradleBuildInvocationSpec {

    public static final List<String> DEFAULT_TASKS = Arrays.asList("clean", "assemble");
    public static final GradleVersion PROJECT_OUTCOMES_MINIMUM_VERSION = GradleVersion.version("1.2");
    public static final GradleVersion EXEC_MINIMUM_VERSION = GradleVersion.version("1.0");

    private FileResolver fileResolver;
    private Object projectDir;
    private GradleVersion gradleVersion = GradleVersion.current();
    private List<String> tasks = new LinkedList<String>(DEFAULT_TASKS);
    private List<String> arguments = new LinkedList<String>();

    public ComparableGradleBuildInvocationSpec(FileResolver fileResolver, Object projectDir) {
        this.fileResolver = fileResolver;
        this.projectDir = projectDir;
    }

    public File getProjectDir() {
        return fileResolver.resolve(projectDir);
    }

    public void setProjectDir(Object projectDir) {
        if (projectDir == null) {
            throw new IllegalArgumentException("projectDir cannot be null");
        }
        this.projectDir = projectDir;
    }

    public GradleVersion getGradleVersion() {
        return gradleVersion;
    }

    public void setGradleVersion(String gradleVersion) {
        if (gradleVersion == null) {
            throw new IllegalArgumentException("gradleVersion cannot be null");
        }
        this.gradleVersion = GradleVersion.version(gradleVersion);
    }

    public List<String> getTasks() {
        return tasks;
    }

    public void setTasks(Iterable<String> tasks) {
        this.tasks = tasks == null ? Collections.<String>emptyList() : Lists.newLinkedList(tasks);
    }

    public List<String> getArguments() {
        return arguments;
    }

    public void setArguments(Iterable<String> arguments) {
        this.arguments = arguments == null ? Collections.<String>emptyList() : Lists.newLinkedList(arguments);
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
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ComparableGradleBuildInvocationSpec that = (ComparableGradleBuildInvocationSpec) o;

        if (!getArguments().equals(that.getArguments())) {
            return false;
        }
        if (!getGradleVersion().equals(that.getGradleVersion())) {
            return false;
        }
        if (!getProjectDir().equals(that.getProjectDir())) {
            return false;
        }
        if (!getTasks().equals(that.getTasks())) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = getProjectDir().hashCode();
        result = 31 * result + getGradleVersion().hashCode();
        result = 31 * result + getTasks().hashCode();
        result = 31 * result + getArguments().hashCode();
        return result;
    }

    public String describeRelativeTo(File relativeTo) {
        return "dir: '" + GFileUtils.relativePath(relativeTo, getProjectDir()) + "'"
                + ", tasks: '" + GUtil.join(getTasks(), " ") + "'"
                + ", arguments: '" + GUtil.join(getArguments(), " ") + "'"
                + ", gradleVersion: " + getGradleVersion();
    }
}
