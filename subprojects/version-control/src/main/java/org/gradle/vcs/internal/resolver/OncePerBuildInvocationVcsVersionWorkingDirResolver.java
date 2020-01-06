/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.vcs.internal.resolver;

import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.cache.internal.ProducerGuard;
import org.gradle.vcs.internal.VersionControlRepositoryConnection;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.io.File;
import java.util.function.Supplier;

/**
 * Ensures that a given resolution from (repo + selector) -> working dir is performed once per build invocation. Allows resolution for different repos to happen in parallel.
 */
@ThreadSafe
public class OncePerBuildInvocationVcsVersionWorkingDirResolver implements VcsVersionWorkingDirResolver {
    private final ProducerGuard<String> perRepoGuard = ProducerGuard.adaptive();
    private final VcsVersionSelectionCache inMemoryCache;
    private final VcsVersionWorkingDirResolver delegate;

    public OncePerBuildInvocationVcsVersionWorkingDirResolver(VcsVersionSelectionCache inMemoryCache, VcsVersionWorkingDirResolver delegate) {
        this.inMemoryCache = inMemoryCache;
        this.delegate = delegate;
    }

    @Nullable
    @Override
    public File selectVersion(final ModuleComponentSelector selector, final VersionControlRepositoryConnection repository) {
        // Perform the work per repository
        return perRepoGuard.guardByKey(repository.getUniqueId(), new Supplier<File>() {
            @Nullable
            @Override
            public File get() {
                File workingDir = inMemoryCache.getWorkingDirForSelector(repository, selector.getVersionConstraint());
                if (workingDir == null) {
                    workingDir = delegate.selectVersion(selector, repository);
                    if (workingDir == null) {
                        return null;
                    }

                    inMemoryCache.putWorkingDirForSelector(repository, selector.getVersionConstraint(), workingDir);
                }
                return workingDir;
            }
        });
    }
}
