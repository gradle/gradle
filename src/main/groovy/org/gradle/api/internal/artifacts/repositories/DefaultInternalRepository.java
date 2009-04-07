/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories;

import org.apache.ivy.core.cache.DefaultRepositoryCacheManager;
import org.apache.ivy.core.cache.RepositoryCacheManager;
import org.apache.ivy.plugins.lock.NoLockStrategy;
import org.apache.ivy.plugins.resolver.FileSystemResolver;
import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.CacheUsage;
import org.gradle.StartParameter;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ResolverContainer;
import org.gradle.api.artifacts.repositories.InternalRepository;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Build;
import org.gradle.util.GradleUtil;

import java.io.File;

/**
 * @author Hans Dockter
 */
public class DefaultInternalRepository extends FileSystemResolver implements InternalRepository, BuildListener {
    private File dir;

    private void configure(File dir) {
        setRepositoryCacheManager(createCacheManager());
        setName(ResolverContainer.BUILD_RESOLVER_NAME);
        String pattern = String.format(dir.getAbsolutePath() + "/" + ResolverContainer.BUILD_RESOLVER_PATTERN);
        addIvyPattern(pattern);
        addArtifactPattern(pattern);
        setValidate(false);
    }

    private RepositoryCacheManager createCacheManager() {
        File cacheDir = new File(dir, ResolverContainer.DEFAULT_CACHE_DIR_NAME);
        DefaultRepositoryCacheManager cacheManager = new DefaultRepositoryCacheManager();
        cacheManager.setBasedir(cacheDir);
        cacheManager.setName(ResolverContainer.DEFAULT_CACHE_NAME);
        cacheManager.setUseOrigin(true);
        cacheManager.setLockStrategy(new NoLockStrategy());
        cacheManager.setIvyPattern(ResolverContainer.DEFAULT_CACHE_IVY_PATTERN);
        cacheManager.setArtifactPattern(ResolverContainer.DEFAULT_CACHE_ARTIFACT_PATTERN);
        return cacheManager;
    }

    public File getDir() {
        return dir;
    }

    public void buildStarted(StartParameter startParameter) {
    }

    public void settingsEvaluated(Settings settings) {
        dir = new File(settings.getSettingsDir(), Project.TMP_DIR_NAME + "/" + ResolverContainer.BUILD_RESOLVER_NAME);
        if (settings.getStartParameter().getCacheUsage() != CacheUsage.ON) {
            GradleUtil.deleteDir(dir);
        }
        configure(dir);
    }

    public void projectsLoaded(Build build) {
    }

    public void projectsEvaluated(Build build) {
    }

    public void taskGraphPopulated(TaskExecutionGraph graph) {
    }

    public void buildFinished(BuildResult result) {
    }
}
