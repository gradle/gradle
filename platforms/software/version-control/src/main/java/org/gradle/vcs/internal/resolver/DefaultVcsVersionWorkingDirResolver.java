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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.CacheExpirationControl;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ChangingValueDependencyResolutionListener;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestVersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.Version;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.vcs.internal.VersionControlRepositoryConnection;
import org.gradle.vcs.internal.VersionRef;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.time.Duration;
import java.util.Set;

public class DefaultVcsVersionWorkingDirResolver implements VcsVersionWorkingDirResolver {

    // Source-dependency floating refs are re-resolved on every build today (no on-disk metadata TTL).
    // Mirror that semantic for CC: an Expiry of zero forces invalidation of any cached entry that
    // resolved a dynamic source-dep selector. A future cacheDynamicVersionsFor-equivalent for source
    // deps would replace this with a real Duration without changing the writer or checker.
    private static final CacheExpirationControl.Expiry ALWAYS_EXPIRED = new CacheExpirationControl.Expiry() {
        @Override
        public boolean isMustCheck() {
            return true;
        }

        @Override
        public Duration getKeepFor() {
            return Duration.ZERO;
        }
    };

    private final VersionSelectorScheme versionSelectorScheme;
    private final VersionComparator versionComparator;
    private final VersionParser versionParser;
    private final VcsVersionSelectionCache inMemoryCache;
    private final PersistentVcsMetadataCache persistentCache;
    private final ChangingValueDependencyResolutionListener listener;

    public DefaultVcsVersionWorkingDirResolver(VersionSelectorScheme versionSelectorScheme, VersionComparator versionComparator, VersionParser versionParser, VcsVersionSelectionCache inMemoryCache, PersistentVcsMetadataCache persistentCache, ChangingValueDependencyResolutionListener listener) {
        this.versionSelectorScheme = versionSelectorScheme;
        this.versionComparator = versionComparator;
        this.versionParser = versionParser;
        this.inMemoryCache = inMemoryCache;
        this.persistentCache = persistentCache;
        this.listener = listener;
    }

    @Nullable
    @Override
    public File selectVersion(ModuleComponentSelector selector, VersionControlRepositoryConnection repository) {
        VersionConstraint constraint = selector.getVersionConstraint();
        VersionRef selectedVersion = selectVersionFromRepository(repository, constraint);
        if (selectedVersion == null) {
            return null;
        }

        if (isDynamic(constraint)) {
            ModuleVersionIdentifier resolvedId = DefaultModuleVersionIdentifier.newId(selector.getModuleIdentifier(), selectedVersion.getVersion());
            listener.onDynamicVersionSelection(selector, ALWAYS_EXPIRED, ImmutableSet.of(resolvedId));
        }

        File workingDir = prepareWorkingDir(repository, selectedVersion);
        persistentCache.putVersionForSelector(repository, constraint, selectedVersion);
        return workingDir;
    }

    private boolean isDynamic(VersionConstraint constraint) {
        if (constraint.getBranch() != null) {
            return true;
        }
        String version = constraint.getRequiredVersion();
        if (version.isEmpty()) {
            return false;
        }
        return versionSelectorScheme.parseSelector(version).isDynamic();
    }

    private File prepareWorkingDir(VersionControlRepositoryConnection repository, VersionRef selectedVersion) {
        // TODO - prevent multiple threads from performing the same VCS populate operation at the same time
        File workingDir = inMemoryCache.getWorkingDirForRevision(repository, selectedVersion);
        if (workingDir == null) {
            workingDir = repository.populate(selectedVersion);
            inMemoryCache.putWorkingDirForRevision(repository, selectedVersion, workingDir);
        }
        return workingDir;
    }

    private VersionRef selectVersionFromRepository(VersionControlRepositoryConnection repository, VersionConstraint constraint) {
        // TODO: match with status, order versions correctly

        if (constraint.getBranch() != null) {
            return repository.getBranch(constraint.getBranch());
        }

        String version = constraint.getRequiredVersion();
        VersionSelector versionSelector = versionSelectorScheme.parseSelector(version);
        if (versionSelector instanceof LatestVersionSelector && ((LatestVersionSelector) versionSelector).getSelectorStatus().equals("integration")) {
            return repository.getDefaultBranch();
        }

        if (versionSelector.requiresMetadata()) {
            // TODO - implement this by moving this resolver to live alongside the external resolvers
            return null;
        }

        Set<VersionRef> versions = inMemoryCache.getVersionsForRepo(repository);
        if (versions == null) {
            versions = repository.getAvailableVersions();
            inMemoryCache.putVersionsForRepo(repository, versions);
        }
        Version bestVersion = null;
        VersionRef bestCandidate = null;
        for (VersionRef candidate : versions) {
            Version candidateVersion = versionParser.transform(candidate.getVersion());
            if (versionSelector.accept(candidateVersion)) {
                if (bestCandidate == null || versionComparator.asVersionComparator().compare(candidateVersion, bestVersion) > 0) {
                    bestVersion = candidateVersion;
                    bestCandidate = candidate;
                }
            }
        }

        return bestCandidate;
    }
}
