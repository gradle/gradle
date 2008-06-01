/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.internal.dependencies

import org.apache.ivy.plugins.resolver.FileSystemResolver
import org.apache.ivy.plugins.resolver.RepositoryResolver
import org.gradle.api.DependencyManager

/**
 * @author Hans Dockter
 */
class BuildResolverHandler {
    File buildResolverDir

    LocalReposCacheHandler localReposCacheHandler

    BuildResolverHandler() {

    }

    BuildResolverHandler(LocalReposCacheHandler localReposCacheHandler) {
        this.localReposCacheHandler = localReposCacheHandler
    }

    RepositoryResolver buildResolverInternal

    RepositoryResolver getBuildResolver() {
        if (!buildResolverInternal) {
            assert buildResolverDir
            buildResolverInternal = new FileSystemResolver()
            configureBuildResolver(buildResolverInternal)
        }
        buildResolverInternal
    }

    private void configureBuildResolver(FileSystemResolver buildResolver) {
        buildResolver.setRepositoryCacheManager(localReposCacheHandler.cacheManager)
        buildResolver.name = DependencyManager.BUILD_RESOLVER_NAME
        String pattern = "$buildResolverDir.absolutePath/$DependencyManager.BUILD_RESOLVER_PATTERN"
        buildResolver.addIvyPattern(pattern)
        buildResolver.addArtifactPattern(pattern)
        buildResolver.validate = false
    }
}
