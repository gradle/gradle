/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.cache.internal;

import org.gradle.StartParameter;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.initialization.layout.BuildLayout;

import java.io.File;

public class BuildScopeCacheDir {
    private final File cacheDir;

    public BuildScopeCacheDir(
        GradleUserHomeDirProvider userHomeDirProvider,
        BuildLayout buildLayout,
        StartParameter startParameter
    ) {
        if (startParameter.getProjectCacheDir() != null) {
            cacheDir = startParameter.getProjectCacheDir();
        } else if (buildLayout.isBuildDefinitionMissing()) {
            cacheDir = new File(userHomeDirProvider.getGradleUserHomeDirectory(), "undefined-build");
        } else {
            cacheDir = new File(buildLayout.getRootDirectory(), ".gradle");
        }
    }

    public File getDir() {
        return cacheDir;
    }
}
