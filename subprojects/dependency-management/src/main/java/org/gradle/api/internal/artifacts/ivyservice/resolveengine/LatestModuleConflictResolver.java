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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine;

import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.Version;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.internal.component.external.model.maven.MavenModuleResolveMetadata;
import org.gradle.internal.component.model.ComponentGraphResolveMetadata;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class LatestModuleConflictResolver<T extends ComponentResolutionState> implements ModuleConflictResolver<T> {
    private final Comparator<Version> versionComparator;
    private final VersionParser versionParser;

    LatestModuleConflictResolver(VersionComparator versionComparator, VersionParser versionParser) {
        this.versionComparator = versionComparator.asVersionComparator();
        this.versionParser = versionParser;
    }

    @Override
    public void select(ConflictResolverDetails<T> details) {
        // Find the candidates with the highest base version
        Version baseVersion = null;
        Map<Version, T> matches = new LinkedHashMap<>();
        for (T candidate : details.getCandidates()) {
            Version version = versionParser.transform(candidate.getVersion());
            if (baseVersion == null || versionComparator.compare(version.getBaseVersion(), baseVersion) > 0) {
                matches.clear();
                baseVersion = version.getBaseVersion();
                matches.put(version, candidate);
            } else if (version.getBaseVersion().equals(baseVersion)) {
                matches.put(version, candidate);
            }
        }

        if (matches.size() == 1) {
            details.select(matches.values().iterator().next());
            return;
        }

        // Work backwards from the highest version, return the first candidate with qualified version and release status, or candidate with unqualified version
        List<Version> sorted = new ArrayList<>(matches.keySet());
        sorted.sort(Collections.reverseOrder(versionComparator));
        T bestComponent = null;
        T unqalifiedComponent = null;
        for (Version version : sorted) {
            T component = matches.get(version);
            if (!version.isQualified()) {
                if (bestComponent == null) {
                    details.select(component);
                    return;
                } else if (unqalifiedComponent == null) {
                    unqalifiedComponent = component;
                }
            }
            // Only care about the first qualified version that matches
            if (bestComponent == null) {
                ComponentGraphResolveMetadata metaData = component.getMetadataOrNull();
                if (hasReleaseStatus(metaData)) {
                    details.select(component);
                    return;
                }
                // Special case Maven snapshots
                if (isMavenSnapshot(metaData)) {
                    bestComponent = component;
                }
            }
        }

        if (unqalifiedComponent != null) {
            // SNAPSHOT seen but there is an unqualified release present - prefer the release
            details.select(unqalifiedComponent);
            return;
        }
        if (bestComponent != null) {
            // Found a snapshot and no unqualified release present
            details.select(bestComponent);
        }

        // Only non releases and no unqualified version or snapshot - just return the highest version
        details.select(matches.get(sorted.get(0)));
    }

    private boolean hasReleaseStatus(@Nullable ComponentGraphResolveMetadata metadata) {
        return metadata != null && "release".equals(metadata.getStatus());
    }

    private boolean isMavenSnapshot(@Nullable ComponentGraphResolveMetadata metadata) {
        if (metadata instanceof MavenModuleResolveMetadata) {
            return ((MavenModuleResolveMetadata) metadata).getSnapshotTimestamp() != null || metadata.getModuleVersionId().getVersion().endsWith("-SNAPSHOT");
        }
        return false;
    }
}
