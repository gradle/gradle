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

package org.gradle.api.problems.internal;

/**
 * The project that is being configured, for problem detection.
 */
public class ProjectIdentity {

    private final String buildTreePath;
    private final String buildPath;

    /**
     * @param buildTreePath the path of the project in the build tree
     * @param buildPath the path of the build that owns the project
     */
    public ProjectIdentity(String buildTreePath, String buildPath) {
        this.buildTreePath = buildTreePath;
        this.buildPath = buildPath;
    }

    public String getBuildTreePath() {
        return buildTreePath;
    }

    public String getBuildPath() {
        return buildPath;
    }
}
