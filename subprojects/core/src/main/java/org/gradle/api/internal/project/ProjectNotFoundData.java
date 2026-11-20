/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.project;

import org.gradle.api.problems.AdditionalData;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Additional data of a problem reporting a project path that could not be found.
 */
public interface ProjectNotFoundData extends AdditionalData {

    /**
     * The project path as requested, possibly relative.
     */
    String getRequestedPath();

    void setRequestedPath(String requestedPath);

    /**
     * Absolute paths of existing projects close to the requested path, within the build of {@link #getBuildPath()}.
     */
    List<String> getCandidates();

    void setCandidates(List<String> candidates);

    /**
     * The path of the build that the candidates belong to, such as {@code :} for the root build.
     * Absent when the requested path and the candidates are build tree paths, which include the path of their build.
     */
    @Nullable
    String getBuildPath();

    void setBuildPath(@Nullable String buildPath);
}
