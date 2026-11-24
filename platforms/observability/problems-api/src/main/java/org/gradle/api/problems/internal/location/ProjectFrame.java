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

package org.gradle.api.problems.internal.location;

import org.gradle.internal.code.Locality;

/**
 * A project of the build.
 */
public final class ProjectFrame extends Frame {

    private final String buildTreePath;

    public ProjectFrame(String buildTreePath, Locality locality) {
        super("project " + buildTreePath, locality);
        this.buildTreePath = buildTreePath;
    }

    /**
     * The path of the project in the build tree, such as {@code :lib:core}.
     */
    // Spec Q10 is open: project path or build tree path. The prototype uses the build tree path, as UC9 does.
    public String getBuildTreePath() {
        return buildTreePath;
    }

    @Override
    public boolean isAuthored() {
        return false;
    }
}
