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

import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheMetaData;
import org.gradle.api.internal.artifacts.metadata.ModuleVersionArtifactMetaData;
import org.gradle.api.internal.artifacts.mvnsettings.CannotLocateLocalMavenRepositoryException;
import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator;
import org.gradle.api.internal.artifacts.repositories.resolver.IvyResourcePattern;
import org.gradle.api.internal.artifacts.repositories.resolver.M2ResourcePattern;
import org.gradle.api.internal.artifacts.repositories.resolver.ResourcePattern;
import org.gradle.api.internal.externalresource.local.CompositeLocallyAvailableResourceFinder;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceCandidates;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceFinder;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceFinderSearchableFileStoreAdapter;
import org.gradle.internal.Factory;
import org.gradle.internal.filestore.FileStoreSearcher;
import org.gradle.internal.hash.HashValue;
import org.gradle.internal.resource.local.LocallyAvailableResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

public class LocallyAvailableResourceFinderFactory implements Factory<LocallyAvailableResourceFinder<ModuleVersionArtifactMetaData>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocallyAvailableResourceFinderFactory.class);

    private final File rootCachesDirectory;
    private final LocalMavenRepositoryLocator localMavenRepositoryLocator;
    private final FileStoreSearcher<ModuleVersionArtifactMetaData> fileStore;

    public LocallyAvailableResourceFinderFactory(
            ArtifactCacheMetaData artifactCacheMetaData, LocalMavenRepositoryLocator localMavenRepositoryLocator, FileStoreSearcher<ModuleVersionArtifactMetaData> fileStore) {
        this.rootCachesDirectory = artifactCacheMetaData.getCacheDir().getParentFile();
        this.localMavenRepositoryLocator = localMavenRepositoryLocator;
        this.fileStore = fileStore;
    }

    public LocallyAvailableResourceFinder<ModuleVersionArtifactMetaData> create() {
        List<LocallyAvailableResourceFinder<ModuleVersionArtifactMetaData>> finders = new LinkedList<LocallyAvailableResourceFinder<ModuleVersionArtifactMetaData>>();

        // Order is important here, because they will be searched in that order

        // The current filestore
        finders.add(new LocallyAvailableResourceFinderSearchableFileStoreAdapter<ModuleVersionArtifactMetaData>(fileStore));

        // 1.9
//        addForPattern(finders, "modules-2/files-2.1/[organisation]/[module](/[branch])/[revision]/*/[artifact]-[revision](-[classifier])(.[ext])");

        // 1.8
        addForPattern(finders, "artifacts-26/filestore/[organisation]/[module](/[branch])/[revision]/[type]/*/[artifact]-[revision](-[classifier])(.[ext])");

        // 1.5
        addForPattern(finders, "artifacts-24/filestore/[organisation]/[module](/[branch])/[revision]/[type]/*/[artifact]-[revision](-[classifier])(.[ext])");

        // 1.4
        addForPattern(finders, "artifacts-23/filestore/[organisation]/[module](/[branch])/[revision]/[type]/*/[artifact]-[revision](-[classifier])(.[ext])");

        // 1.3
        addForPattern(finders, "artifacts-15/filestore/[organisation]/[module](/[branch])/[revision]/[type]/*/[artifact]-[revision](-[classifier])(.[ext])");

        // 1.1, 1.2
        addForPattern(finders, "artifacts-14/filestore/[organisation]/[module](/[branch])/[revision]/[type]/*/[artifact]-[revision](-[classifier])(.[ext])");

        // rc-1, 1.0
        addForPattern(finders, "artifacts-13/filestore/[organisation]/[module](/[branch])/[revision]/[type]/*/[artifact]-[revision](-[classifier])(.[ext])");

        // Milestone 8 and 9
        addForPattern(finders, "artifacts-8/filestore/[organisation]/[module](/[branch])/[revision]/[type]/*/[artifact]-[revision](-[classifier])(.[ext])");

        // Milestone 7
        addForPattern(finders, "artifacts-7/artifacts/*/[organisation]/[module](/[branch])/[revision]/[type]/[artifact]-[revision](-[classifier])(.[ext])");

        // Milestone 6
        addForPattern(finders, "artifacts-4/[organisation]/[module](/[branch])/*/[type]s/[artifact]-[revision](-[classifier])(.[ext])");
        addForPattern(finders, "artifacts-4/[organisation]/[module](/[branch])/*/pom.originals/[artifact]-[revision](-[classifier])(.[ext])");

        // Milestone 3
        addForPattern(finders, "../cache/[organisation]/[module](/[branch])/[type]s/[artifact]-[revision](-[classifier])(.[ext])");

        // Maven local
        try {
            File localMavenRepository = localMavenRepositoryLocator.getLocalMavenRepository();
            if (localMavenRepository.exists()) {
                addForPattern(finders, localMavenRepository, new M2ResourcePattern("[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier])(.[ext])"));
            }
        } catch (CannotLocateLocalMavenRepositoryException ex) {
            finders.add(new NoMavenLocalRepositoryResourceFinder(ex));
        }
        return new CompositeLocallyAvailableResourceFinder<ModuleVersionArtifactMetaData>(finders);
    }

    private void addForPattern(List<LocallyAvailableResourceFinder<ModuleVersionArtifactMetaData>> finders, String pattern) {
        int wildcardPos = pattern.indexOf("/*/");
        int patternPos = pattern.indexOf("/[");
        if (wildcardPos < 0 && patternPos < 0) {
            throw new IllegalArgumentException(String.format("Unsupported pattern '%s'", pattern));
        }
        int chopAt;
        if (wildcardPos >= 0 && patternPos >= 0) {
            chopAt = Math.min(wildcardPos, patternPos);
        } else if (wildcardPos >= 0) {
            chopAt = wildcardPos;
        } else {
            chopAt = patternPos;
        }
        String pathPart = pattern.substring(0, chopAt);
        String patternPart = pattern.substring(chopAt + 1);
        addForPattern(finders, new File(rootCachesDirectory, pathPart), new IvyResourcePattern(patternPart));
    }

    private void addForPattern(List<LocallyAvailableResourceFinder<ModuleVersionArtifactMetaData>> finders, File baseDir, ResourcePattern pattern) {
        if (baseDir.exists()) {
            finders.add(new PatternBasedLocallyAvailableResourceFinder(baseDir, pattern));
        }
    }

    private class NoMavenLocalRepositoryResourceFinder implements LocallyAvailableResourceFinder<ModuleVersionArtifactMetaData> {
        private final CannotLocateLocalMavenRepositoryException ex;
        private boolean logged;

        public NoMavenLocalRepositoryResourceFinder(CannotLocateLocalMavenRepositoryException ex) {
            this.ex = ex;
        }

        public LocallyAvailableResourceCandidates findCandidates(ModuleVersionArtifactMetaData criterion) {
            if(!logged){
                LOGGER.warn("Unable to locate local Maven repository.");
                LOGGER.debug("Problems while locating local Maven repository.", ex);
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