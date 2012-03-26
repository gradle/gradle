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
import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator;
import org.gradle.api.internal.externalresource.local.CompositeLocallyAvailableResourceFinder;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceFinder;
import org.gradle.api.internal.filestore.ivy.ArtifactRevisionIdFileStore;

import java.io.File;
import java.util.LinkedList;

public class LocallyAvailableResourceFinderBuilder {
    private final LinkedList<LocallyAvailableResourceFinder<ArtifactRevisionId>> hashCaches = new LinkedList<LocallyAvailableResourceFinder<ArtifactRevisionId>>();
    private final File rootCachesDirectory;
    private final LocalMavenRepositoryLocator localMavenRepositoryLocator;

    public LocallyAvailableResourceFinderBuilder(ArtifactCacheMetaData artifactCacheMetaData, LocalMavenRepositoryLocator localMavenRepositoryLocator) {
        this.rootCachesDirectory = artifactCacheMetaData.getCacheDir().getParentFile();
        this.localMavenRepositoryLocator = localMavenRepositoryLocator;
    }

    public void addCurrent(File baseDir) {
        hashCaches.add(ArtifactRevisionIdFileStore.asArtifactCache(baseDir));
    }

    public void addMilestone8and9() {
        addExternalCache(new File(rootCachesDirectory, "artifacts-8"), "filestore/[organisation]/[module](/[branch])/[revision]/[type]/*/[artifact]-[revision](-[classifier])(.[ext])");
    }

    public void addMilestone7() {
        addExternalCache(new File(rootCachesDirectory, "artifacts-7"), "artifacts/*/[organisation]/[module](/[branch])/[revision]/[type]/[artifact]-[revision](-[classifier])(.[ext])");
    }

    public void addMilestone6() {
        addExternalCache(new File(rootCachesDirectory, "artifacts-4"), "[organisation]/[module](/[branch])/*/[type]s/[artifact]-[revision](-[classifier])(.[ext])");
        addExternalCache(new File(rootCachesDirectory, "artifacts-4"), "[organisation]/[module](/[branch])/*/pom.originals/[artifact]-[revision](-[classifier])(.[ext])");
    }

    public void addMilestone3() {
        addExternalCache(new File(rootCachesDirectory, "../cache"), "[organisation]/[module](/[branch])/[type]s/[artifact]-[revision](-[classifier])(.[ext])");
    }

    public void addMavenLocal() {
        File localMavenRepository = localMavenRepositoryLocator.getLocalMavenRepository();
        if (localMavenRepository.exists()) {
            hashCaches.add(new PatternBasedLocallyAvailableResourceFinder(localMavenRepository, "[organisation-path]/[module]/[revision]/[artifact]-[revision](-[classifier])(.[ext])"));
        }
    }

    private void addExternalCache(File baseDir, String pattern) {
        if (baseDir.exists()) {
            hashCaches.add(new PatternBasedLocallyAvailableResourceFinder(baseDir, pattern));
        }
    }

    public LocallyAvailableResourceFinder<ArtifactRevisionId> build() {
        return new CompositeLocallyAvailableResourceFinder<ArtifactRevisionId>(hashCaches);
    }

}
