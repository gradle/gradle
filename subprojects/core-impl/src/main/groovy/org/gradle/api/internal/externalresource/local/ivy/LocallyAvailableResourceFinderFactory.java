/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.externalresource.local.ivy;

import org.apache.ivy.core.module.id.ArtifactRevisionId;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheMetaData;
import org.gradle.api.internal.artifacts.mvnsettings.CannotLocateLocalMavenRepositoryException;
import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator;
import org.gradle.api.internal.artifacts.repositories.resolver.IvyResourcePattern;
import org.gradle.api.internal.artifacts.repositories.resolver.M2ResourcePattern;
import org.gradle.api.internal.artifacts.repositories.resolver.ResourcePattern;
import org.gradle.api.internal.externalresource.local.*;
import org.gradle.api.internal.filestore.FileStoreSearcher;
import org.gradle.internal.Factory;
import org.gradle.util.hash.HashValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

public class LocallyAvailableResourceFinderFactory implements Factory<LocallyAvailableResourceFinder<ArtifactRevisionId>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocallyAvailableResourceFinderFactory.class);

    private final File rootCachesDirectory;
    private final LocalMavenRepositoryLocator localMavenRepositoryLocator;
    private final FileStoreSearcher<ArtifactRevisionId> fileStore;

    public LocallyAvailableResourceFinderFactory(
            ArtifactCacheMetaData artifactCacheMetaData, LocalMavenRepositoryLocator localMavenRepositoryLocator, FileStoreSearcher<ArtifactRevisionId> fileStore) {
        this.rootCachesDirectory = artifactCacheMetaData.getCacheDir().getParentFile();
        this.localMavenRepositoryLocator = localMavenRepositoryLocator;
        this.fileStore = fileStore;
    }

    public LocallyAvailableResourceFinder<ArtifactRevisionId> create() {
        List<LocallyAvailableResourceFinder<ArtifactRevisionId>> finders = new LinkedList<LocallyAvailableResourceFinder<ArtifactRevisionId>>();

        // Order is important here, because they will be searched in that order

        // The current filestore
        finders.add(new LocallyAvailableResourceFinderSearchableFileStoreAdapter<ArtifactRevisionId>(fileStore));

        // 1.3
        addForPattern(finders, "artifacts-15", "filestore/[organisation]/[module](/[branch])/[revision]/[type]/*/[artifact]-[revision](-[classifier])(.[ext])");

        // 1.1, 1.2
        addForPattern(finders, "artifacts-14", "filestore/[organisation]/[module](/[branch])/[revision]/[type]/*/[artifact]-[revision](-[classifier])(.[ext])");

        // rc-1, 1.0
        addForPattern(finders, "artifacts-13", "filestore/[organisation]/[module](/[branch])/[revision]/[type]/*/[artifact]-[revision](-[classifier])(.[ext])");

        // Milestone 8 and 9
        addForPattern(finders, "artifacts-8", "filestore/[organisation]/[module](/[branch])/[revision]/[type]/*/[artifact]-[revision](-[classifier])(.[ext])");

        // Milestone 7
        addForPattern(finders, "artifacts-7", "artifacts/*/[organisation]/[module](/[branch])/[revision]/[type]/[artifact]-[revision](-[classifier])(.[ext])");

        // Milestone 6
        addForPattern(finders, "artifacts-4", "[organisation]/[module](/[branch])/*/[type]s/[artifact]-[revision](-[classifier])(.[ext])");
        addForPattern(finders, "artifacts-4", "[organisation]/[module](/[branch])/*/pom.originals/[artifact]-[revision](-[classifier])(.[ext])");

        // Milestone 3
        addForPattern(finders, "../cache", "[organisation]/[module](/[branch])/[type]s/[artifact]-[revision](-[classifier])(.[ext])");

        // Maven local
        try {
            File localMavenRepository = localMavenRepositoryLocator.getLocalMavenRepository();
            if (localMavenRepository.exists()) {
                addForPattern(finders, localMavenRepository, new M2ResourcePattern("[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier])(.[ext])"));
            }
        } catch (CannotLocateLocalMavenRepositoryException ex) {
            finders.add(new NoMavenLocalRepositoryResourceFinder(ex));
        }
        return new CompositeLocallyAvailableResourceFinder<ArtifactRevisionId>(finders);
    }

    private void addForPattern(List<LocallyAvailableResourceFinder<ArtifactRevisionId>> finders, String path, String pattern) {
        addForPattern(finders, new File(rootCachesDirectory, path), new IvyResourcePattern(pattern));
    }

    private void addForPattern(List<LocallyAvailableResourceFinder<ArtifactRevisionId>> finders, File baseDir, ResourcePattern pattern) {
        if (baseDir.exists()) {
            finders.add(new PatternBasedLocallyAvailableResourceFinder(baseDir, pattern));
        }
    }

    private class NoMavenLocalRepositoryResourceFinder implements LocallyAvailableResourceFinder<ArtifactRevisionId> {
        private final CannotLocateLocalMavenRepositoryException ex;
        private boolean logged;

        public NoMavenLocalRepositoryResourceFinder(CannotLocateLocalMavenRepositoryException ex) {
            this.ex = ex;
        }

        public LocallyAvailableResourceCandidates findCandidates(ArtifactRevisionId criterion) {
            if(!logged){
                LOGGER.warn("Unable to locate local Maven repository.");
                LOGGER.debug("Problems while locating local maven repository.", ex);
                logged = true;
            }
            return new LocallyAvailableResourceCandidates() {
                public boolean isNone() {
                    return true;
                }

                public LocallyAvailableResource findByHashValue(HashValue hashValue) {
                    return null;
                }
            };
        }
    }
}