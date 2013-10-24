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

import org.gradle.api.Project;
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheLayout;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.util.*;

public class CacheLayoutBuilder implements CacheLayout {
    private final GradleVersion version = GradleVersion.current();
    private File baseDir;
    private Project project;
    private String[] pathEntries = new String[0];
    private CacheBuilder.VersionStrategy versionStrategy = CacheBuilder.VersionStrategy.CachePerVersion;

    public CacheLayoutBuilder withGlobalScope() {
        project = null;
        return this;
    }

    public CacheLayoutBuilder withScope(File baseDir) {
        this.baseDir = baseDir;
        return this;
    }

    public CacheLayoutBuilder withBuildScope(Project project) {
        this.project = project.getRootProject();
        return this;
    }

    public CacheLayoutBuilder withPath(String... pathEntries) {
        this.pathEntries = pathEntries;
        return this;
    }

    public CacheLayoutBuilder withSharedCache() {
        this.versionStrategy = CacheBuilder.VersionStrategy.SharedCache;
        return this;
    }

    public CacheLayoutBuilder withSharedCacheThatInvalidatesOnVersionChange() {
        this.versionStrategy = CacheBuilder.VersionStrategy.SharedCacheInvalidateOnVersionChange;
        return this;
    }

    public CacheLayout build() {
        return this;
    }

    public File getCacheDir(File globalCacheDir, File projectCacheDir, String cacheKey) {
        File cacheBaseDir = determineBaseDir(globalCacheDir, projectCacheDir);
        cacheBaseDir = applyVersionStrategy(cacheBaseDir);
        for (String pathEntry : pathEntries) {
            cacheBaseDir = new File(cacheBaseDir, pathEntry);
        }
        return new File(cacheBaseDir, cacheKey);
    }

    private File determineBaseDir(File globalCacheDir, File projectCacheDir) {
        if (baseDir != null) {
            return new File(baseDir, ".gradle");
        }
        if (project != null) {
            return getProjectCacheDir(projectCacheDir);
        }
        return globalCacheDir;
    }

    private File applyVersionStrategy(File cacheBaseDir) {
        switch (versionStrategy) {
            case CachePerVersion:
                return new File(cacheBaseDir, version.getVersion());
            case SharedCacheInvalidateOnVersionChange:
                // Include the 'noVersion' suffix for backwards compatibility
                return  new File(cacheBaseDir, "noVersion");
            case SharedCache:
            default:
                return cacheBaseDir;
        }
    }

    private File getProjectCacheDir(File projectCacheDir) {
        if (projectCacheDir != null) {
            return projectCacheDir;
        }
        return new File(project.getProjectDir(), ".gradle");
    }

    public Map<String, ?> applyLayoutProperties(Map<String, ?> properties) {
        Map<String, Object> layoutProperties = new HashMap<String, Object>(properties);
        if (versionStrategy == CacheBuilder.VersionStrategy.SharedCacheInvalidateOnVersionChange) {
            layoutProperties.put("gradle.version", version.getVersion());
        }
        return layoutProperties;
    }
}
