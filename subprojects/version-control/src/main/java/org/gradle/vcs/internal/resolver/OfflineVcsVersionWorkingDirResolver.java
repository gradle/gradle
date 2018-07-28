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
import org.gradle.vcs.VersionControlSpec;
import org.gradle.vcs.internal.VersionControlSystem;

import javax.annotation.Nullable;
import java.io.File;

public class OfflineVcsVersionWorkingDirResolver implements VcsVersionWorkingDirResolver {
    private final VcsVersionSelectionCache inMemoryCache;
    private final PersistentVcsMetadataCache persistentCache;

    public OfflineVcsVersionWorkingDirResolver(VcsVersionSelectionCache inMemoryCache, PersistentVcsMetadataCache persistentCache) {
        this.inMemoryCache = inMemoryCache;
        this.persistentCache = persistentCache;
    }

    @Nullable
    @Override
    public File selectVersion(ModuleComponentSelector selector, VersionControlSpec spec, VersionControlSystem versionControlSystem) {
        File workingDirForVersion = inMemoryCache.getWorkingDirForSelector(spec, selector.getVersionConstraint());
        if (workingDirForVersion == null) {
            // TODO - prevent multiple threads from performing the same selection at the same time
            workingDirForVersion = persistentCache.getWorkingDirForVersion(spec, selector.getVersionConstraint());
            if (workingDirForVersion == null) {
                throw new ModuleVersionResolveException(selector, String.format("Cannot resolve %s from %s in offline mode.", selector.getDisplayName(), spec.getDisplayName()));
            }

            // Reuse the same location as last build
            inMemoryCache.putWorkingDirForVersion(spec, selector.getVersionConstraint(), workingDirForVersion);
        }
        return new File(workingDirForVersion, spec.getRootDir());
    }
}
