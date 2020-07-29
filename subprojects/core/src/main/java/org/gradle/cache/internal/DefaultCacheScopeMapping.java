/*
 * Copyright 2013 the original author or authors.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.invocation.Gradle;
import org.gradle.cache.GlobalCache;
import org.gradle.util.GradleVersion;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;
import java.util.regex.Pattern;

public class DefaultCacheScopeMapping implements CacheScopeMapping, GlobalCache {

    @VisibleForTesting
    public static final String GLOBAL_CACHE_DIR_NAME = "caches";
    private static final Pattern CACHE_KEY_NAME_PATTERN = Pattern.compile("\\p{Alpha}+[-/.\\w]*");

    private final File globalCacheDir;
    private final File projectCacheDir;
    private final GradleVersion version;

    public DefaultCacheScopeMapping(File userHomeDir, @Nullable File projectCacheDir, GradleVersion version) {
        this.version = version;
        this.globalCacheDir = new File(userHomeDir, GLOBAL_CACHE_DIR_NAME);
        this.projectCacheDir = projectCacheDir;
    }

    @Override
    public File getBaseDirectory(@Nullable Object scope, String key, VersionStrategy versionStrategy) {
        if (key.equalsIgnoreCase("projects") || key.equalsIgnoreCase("tasks") || !CACHE_KEY_NAME_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException(String.format("Unsupported cache key '%s'.", key));
        }
        File cacheRootDir = getRootDirectory(scope);
        String subDir = key;
        if (scope instanceof Project) {
            Project project = (Project) scope;
            subDir = "projects/" + project.getPath().replace(':', '_') + "/" + key;
        }
        if (scope instanceof Task) {
            Task task = (Task) scope;
            subDir = "tasks/" + task.getPath().replace(':', '_') + "/" + key;
        }
        return getCacheDir(cacheRootDir, versionStrategy, subDir);
    }

    @Override
    public File getRootDirectory(@Nullable Object scope) {
        if (scope == null) {
            return globalCacheDir;
        }
        if (scope instanceof File) {
            return (File) scope;
        }
        if (scope instanceof Gradle) {
            Gradle gradle = (Gradle) scope;
            return getBuildCacheDir(gradle.getRootProject());
        }
        if (scope instanceof Project) {
            Project project = (Project) scope;
            return getBuildCacheDir(project.getRootProject());
        }
        if (scope instanceof Task) {
            Task task = (Task) scope;
            return getBuildCacheDir(task.getProject().getRootProject());
        }
        throw new IllegalArgumentException(String.format("Don't know how to determine the cache directory for scope of type %s.", scope.getClass().getSimpleName()));
    }

    private File getCacheDir(File rootDir, VersionStrategy versionStrategy, String subDir) {
        switch (versionStrategy) {
            case CachePerVersion:
                return new File(rootDir, version.getVersion() + "/" + subDir);
            case SharedCache:
                return new File(rootDir, subDir);
            default:
                throw new IllegalArgumentException();
        }
    }

    private File getBuildCacheDir(Project rootProject) {
        if (projectCacheDir != null) {
            return projectCacheDir;
        }
        return new File(rootProject.getProjectDir(), ".gradle");
    }

    @Override
    public List<File> getGlobalCacheRoots() {
        return ImmutableList.of(globalCacheDir);
    }
}
