/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.isolated.models;

import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.util.Objects;

public class IsolatedModelScope {

    private final Path buildPath;
    @Nullable
    private final Path projectPath;

    public IsolatedModelScope(Path buildPath, @Nullable Path projectPath) {
        this.buildPath = buildPath;
        this.projectPath = projectPath;
    }

    public IsolatedModelScope(Path buildPath) {
        this(buildPath, null);
    }

    public Path getBuildPath() {
        return buildPath;
    }

    @Nullable
    public Path getProjectPath() {
        return projectPath;
    }

    public IsolatedModelScope getBuildScope() {
        return projectPath == null ? this : new IsolatedModelScope(buildPath);
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IsolatedModelScope)) {
            return false;
        }

        IsolatedModelScope that = (IsolatedModelScope) o;
        return buildPath.equals(that.buildPath) && Objects.equals(projectPath, that.projectPath);
    }

    @Override
    public int hashCode() {
        int result = buildPath.hashCode();
        result = 31 * result + Objects.hashCode(projectPath);
        return result;
    }

    @Override
    public String toString() {
        return "IsolatedModelScope(" +
            "build=" + buildPath +
            (projectPath == null ? "" : ",project=" + projectPath)
            + ")";
    }
}
