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

package org.gradle.nativeplatform.internal.resolve;

import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.resolve.ProjectLocator;

public class DelegatingProjectLocator implements ProjectLocator {
    private final String projectPath;
    private final ProjectLocator delegate;
    private final ProjectFinder finder;

    public DelegatingProjectLocator(String projectPath, ProjectLocator delegate, ProjectFinder finder) {
        this.projectPath = projectPath;
        this.delegate = delegate;
        this.finder = finder;
    }

    public ProjectInternal locateProject(String path) {
        if (path == null || path.length() == 0) {
            return finder.getProject(projectPath);
        }

        return delegate.locateProject(path);
    }
}
