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
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.vcs.internal.VersionControlRepositoryConnection;
import org.gradle.vcs.internal.VersionRef;

import javax.annotation.Nullable;
import java.io.File;

public class OfflineVcsVersionWorkingDirResolver implements VcsVersionWorkingDirResolver {
    private final PersistentVcsMetadataCache persistentCache;

    public OfflineVcsVersionWorkingDirResolver(PersistentVcsMetadataCache persistentCache) {
        this.persistentCache = persistentCache;
    }

    @Nullable
    @Override
    public File selectVersion(ModuleComponentSelector selector, VersionControlRepositoryConnection repository) {
        VersionRef previousVersion = persistentCache.getVersionForSelector(repository, selector.getVersionConstraint());
        if (previousVersion == null) {
            throw new ModuleVersionResolveException(selector, () -> String.format("Cannot resolve %s from %s in offline mode.", selector.getDisplayName(), repository.getDisplayName()));
        }

        // Reuse the same version as last build
        return repository.populate(previousVersion);
    }
}
