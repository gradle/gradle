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
import org.gradle.internal.component.model.ComponentResolveMetadata;

import java.util.*;

class LatestModuleConflictResolver implements ModuleConflictResolver {
    private final Comparator<Version> versionComparator;
    private final VersionParser versionParser = new VersionParser();

    LatestModuleConflictResolver(VersionComparator versionComparator) {
        this.versionComparator = versionComparator.asVersionComparator();
    }

    public <T extends ComponentResolutionState> T select(Collection<? extends T> candidates) {
        // Find the candidates with the highest base version
        Version baseVersion = null;
        Map<Version, T> matches = new LinkedHashMap<Version, T>();
        for (T candidate : candidates) {
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
            return matches.values().iterator().next();
        }

        // Work backwards from highest version, return the first candidate with qualified version and release status, or candidate with unqualified version
        List<Version> sorted = new ArrayList<Version>(matches.keySet());
        Collections.sort(sorted, Collections.reverseOrder(versionComparator));
        for (Version version : sorted) {
            T component = matches.get(version);
            if (!version.isQualified()) {
                return component;
            }
            ComponentResolveMetadata metaData = component.getMetaData();
            if (metaData != null && "release".equals(metaData.getStatus())) {
                return component;
            }
        }

        // Nothing - just return the highest version
        return matches.get(sorted.get(0));
    }
}
