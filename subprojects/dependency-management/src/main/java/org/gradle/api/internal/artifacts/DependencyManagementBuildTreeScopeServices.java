/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.artifacts;

import org.gradle.api.internal.artifacts.vcs.VcsWorkingDirectoryRoot;
import org.gradle.initialization.layout.ProjectCacheDir;

import java.io.File;

/**
 * The set of dependency management services that are created per build tree.
 */
class DependencyManagementBuildTreeScopeServices {
    VcsWorkingDirectoryRoot createVcsWorkingDirectoryRoot(ProjectCacheDir projectCacheDir) {
        return new VcsWorkingDirectoryRoot(new File(projectCacheDir.getDir(), "vcsWorkingDirs"));
    }
}
