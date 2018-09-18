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
import org.gradle.api.plugins.buildcomparison.gradle.GradleBuildInvocationSpec;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.util.CollectionUtils;
import org.gradle.util.GradleVersion;

import javax.inject.Inject;
import java.io.File;
import java.util.LinkedList;
import java.util.List;

public class DefaultGradleBuildInvocationSpec implements GradleBuildInvocationSpec {

    private PathToFileResolver fileResolver;
    private Object projectDir;
    private String gradleVersion = GradleVersion.current().getVersion();
    private List<String> tasks = new LinkedList<String>();
    private List<String> arguments = new LinkedList<String>();

    @Inject
    public DefaultGradleBuildInvocationSpec(PathToFileResolver fileResolver, Object projectDir) {
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

    public String getGradleVersion() {
        return gradleVersion;
    }

    public void setGradleVersion(String gradleVersion) {
        if (gradleVersion == null) {
            throw new IllegalArgumentException("gradleVersion cannot be null");
        }
        GradleVersion version = GradleVersion.version(gradleVersion);
        this.gradleVersion = version.getVersion();
    }

    public List<String> getTasks() {
        return tasks;
    }

    public void setTasks(Iterable<String> tasks) {
        this.tasks = tasks == null ? new LinkedList<String>() : Lists.newLinkedList(tasks);
    }

    public List<String> getArguments() {
        return arguments;
    }

    public void setArguments(Iterable<String> arguments) {
        this.arguments = arguments == null ? new LinkedList<String>() : Lists.newLinkedList(arguments);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultGradleBuildInvocationSpec that = (DefaultGradleBuildInvocationSpec) o;

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

    @Override
    public String toString() {
        return "{"
                + "dir: '" + getProjectDir().getAbsolutePath() + "'"
                + ", tasks: '" + CollectionUtils.join(" ", getTasks()) + "'"
                + ", arguments: '" + CollectionUtils.join(" ", getArguments()) + "'"
                + ", gradleVersion: " + getGradleVersion()
                + "}";
    }
}
