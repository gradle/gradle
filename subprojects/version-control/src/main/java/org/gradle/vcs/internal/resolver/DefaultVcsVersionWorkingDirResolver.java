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

import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestVersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.Version;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.internal.hash.HashUtil;
import org.gradle.util.GFileUtils;
import org.gradle.vcs.VersionControlSpec;
import org.gradle.vcs.internal.VcsDirectoryLayout;
import org.gradle.vcs.internal.VersionControlSystem;
import org.gradle.vcs.internal.VersionRef;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Set;

public class DefaultVcsVersionWorkingDirResolver implements VcsVersionWorkingDirResolver {
    private final VersionSelectorScheme versionSelectorScheme;
    private final VersionComparator versionComparator;
    private final File baseWorkingDir;
    private final VersionParser versionParser;
    private final VcsVersionSelectionCache inMemoryCache;
    private final PersistentVcsMetadataCache persistentCache;

    public DefaultVcsVersionWorkingDirResolver(VersionSelectorScheme versionSelectorScheme, VersionComparator versionComparator, VcsDirectoryLayout directoryLayout, VersionParser versionParser, VcsVersionSelectionCache inMemoryCache, PersistentVcsMetadataCache persistentCache) {
        this.versionSelectorScheme = versionSelectorScheme;
        this.versionComparator = versionComparator;
        this.baseWorkingDir = directoryLayout.getCheckoutDir();
        this.versionParser = versionParser;
        this.inMemoryCache = inMemoryCache;
        this.persistentCache = persistentCache;
    }

    @Nullable
    @Override
    public File selectVersion(ModuleComponentSelector selector, VersionControlSpec spec, VersionControlSystem versionControlSystem) {
        VersionRef selectedVersion = selectVersionFromRepository(spec, versionControlSystem, selector.getVersionConstraint());
        if (selectedVersion == null) {
            return null;
        }

        File workingDir = prepareWorkingDir(baseWorkingDir, spec, versionControlSystem, selectedVersion);
        persistentCache.putWorkingDirForSelector(spec, selector.getVersionConstraint(), selectedVersion, workingDir);
        return workingDir;
    }

    private File prepareWorkingDir(File baseWorkingDir, VersionControlSpec spec, VersionControlSystem versionControlSystem, VersionRef selectedVersion) {
        // TODO - prevent multiple threads from performing the same checkout at the same time
        File workingDir = inMemoryCache.getWorkingDirForRevision(spec, selectedVersion);
        if (workingDir == null) {
            String versionId = HashUtil.createCompactMD5(spec.getUniqueId() + "-" + selectedVersion.getCanonicalId());
            workingDir = new File(baseWorkingDir, versionId + "/" + spec.getRepoName());
            versionControlSystem.populate(workingDir, selectedVersion, spec);

            // Update timestamp so that working directory is not garbage collected
            GFileUtils.touch(workingDir.getParentFile());
            inMemoryCache.putWorkingDirForRevision(spec, selectedVersion, workingDir);
        }
        return workingDir;
    }

    private VersionRef selectVersionFromRepository(VersionControlSpec spec, VersionControlSystem versionControlSystem, VersionConstraint constraint) {
        // TODO: match with status, order versions correctly

        if (constraint.getBranch() != null) {
            return versionControlSystem.getBranch(spec, constraint.getBranch());
        }

        String version = constraint.getRequiredVersion();
        VersionSelector versionSelector = versionSelectorScheme.parseSelector(version);
        if (versionSelector instanceof LatestVersionSelector && ((LatestVersionSelector) versionSelector).getSelectorStatus().equals("integration")) {
            return versionControlSystem.getDefaultBranch(spec);
        }

        if (versionSelector.requiresMetadata()) {
            // TODO - implement this by moving this resolver to live alongside the external resolvers
            return null;
        }

        Set<VersionRef> versions = inMemoryCache.getVersionsForRepo(spec);
        if (versions == null) {
            versions = versionControlSystem.getAvailableVersions(spec);
            inMemoryCache.putVersionsForRepo(spec, versions);
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
