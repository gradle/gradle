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
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.initialization.layout.BuildLocations;

import java.io.File;

public class BuildScopeCacheDir {
    public static final String UNDEFINED_BUILD = "undefined-build";

    private final File cacheDir;

    public BuildScopeCacheDir(
        GradleUserHomeDirProvider userHomeDirProvider,
        BuildLocations buildLocations,
        StartParameter startParameter
    ) {
        if (startParameter.getProjectCacheDir() != null) {
            cacheDir = startParameter.getProjectCacheDir();
        } else if (!buildLocations.getRootDirectory().getName().equals(SettingsInternal.BUILD_SRC) && buildLocations.isBuildDefinitionMissing()) {
            cacheDir = new File(userHomeDirProvider.getGradleUserHomeDirectory(), UNDEFINED_BUILD);
        } else {
            cacheDir = new File(buildLocations.getRootDirectory(), ".gradle");
        }
        if (cacheDir.exists() && !cacheDir.isDirectory()) {
            throw new UncheckedIOException(String.format("Cache directory '%s' exists and is not a directory.", cacheDir));
        }
    }

    public File getDir() {
        return cacheDir;
    }
}
