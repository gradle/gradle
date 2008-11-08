/*
 * Copyright -2008 the original author or authors.
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

package org.gradle.api.internal.dependencies;

import org.apache.ivy.plugins.resolver.FileSystemResolver;
import org.apache.ivy.plugins.resolver.RepositoryResolver;
import org.gradle.api.DependencyManager;

import java.io.File;

/**
 * @author Hans Dockter
 */
public class BuildResolverHandler {
    private LocalReposCacheHandler localReposCacheHandler;
    private File buildResolverDir;

    private FileSystemResolver buildResolverInternal;

    public BuildResolverHandler(File buildResolverDir, LocalReposCacheHandler localReposCacheHandler) {
        this.buildResolverDir = buildResolverDir;
        this.localReposCacheHandler = localReposCacheHandler;
    }

    RepositoryResolver getBuildResolver() {
        if (buildResolverInternal == null) {
            buildResolverInternal = new FileSystemResolver();
            configureBuildResolver(buildResolverInternal);
        }
        return buildResolverInternal;
    }

    private void configureBuildResolver(FileSystemResolver buildResolver) {
        buildResolver.setRepositoryCacheManager(localReposCacheHandler.getCacheManager(new File(buildResolverDir, DependencyManager.DEFAULT_CACHE_DIR_NAME)));
        buildResolver.setName(DependencyManager.BUILD_RESOLVER_NAME);
        String pattern = String.format(getBuildResolverDir().getAbsolutePath() + "/" + DependencyManager.BUILD_RESOLVER_PATTERN);
        buildResolver.addIvyPattern(pattern);
        buildResolver.addArtifactPattern(pattern);
        buildResolver.setValidate(false);
    }

    public File getBuildResolverDir() {
        return buildResolverDir;
    }

    public void setBuildResolverDir(File buildResolverDir) {
        this.buildResolverDir = buildResolverDir;
    }

    public LocalReposCacheHandler getLocalReposCacheHandler() {
        return localReposCacheHandler;
    }

    public void setLocalReposCacheHandler(LocalReposCacheHandler localReposCacheHandler) {
        this.localReposCacheHandler = localReposCacheHandler;
    }
}
