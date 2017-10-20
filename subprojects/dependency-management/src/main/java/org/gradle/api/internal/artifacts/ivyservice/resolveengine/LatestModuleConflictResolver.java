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

import org.gradle.api.GradleException;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.Version;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.internal.component.model.ComponentResolveMetadata;

import java.util.*;

class LatestModuleConflictResolver implements ModuleConflictResolver {
    private final Comparator<Version> versionComparator;

    LatestModuleConflictResolver(VersionComparator versionComparator) {
        this.versionComparator = versionComparator.asVersionComparator();
    }

    @Override
    public <T extends ComponentResolutionState> void select(ConflictResolverDetails<T> details) {
        // Find the candidates with the highest base version
        Version baseVersion = null;
        Map<Version, T> matches = new LinkedHashMap<Version, T>();
        Collection<? extends T> candidates = details.getCandidates();
        for (T candidate : candidates) {
            Version version = VersionParser.INSTANCE.transform(candidate.getVersion());
            if (baseVersion == null || versionComparator.compare(version.getBaseVersion(), baseVersion) > 0) {
                boolean accept = true;
                for (T t : candidates) {
                    if (t != candidate) {
                        VersionSelector rejectedVersionSelector = t.getRejectedVersionSelector();
                        if (rejectedVersionSelector != null && rejectedVersionSelector.accept(version)) {
                            accept = false;
                            break;
                        }
                    }
                }
                baseVersion = version.getBaseVersion();
                if (accept) {
                    matches.put(version, candidate);
                } else {
                    matches.clear();
                }
            } else if (version.getBaseVersion().equals(baseVersion)) {
                matches.put(version, candidate);
            }
        }
        if (matches.isEmpty()) {
            details.fail(new GradleException("Bad luck"));
            return;
        }

        if (matches.size() == 1) {
            details.select(matches.values().iterator().next());
            return;
        }

        // Work backwards from highest version, return the first candidate with qualified version and release status, or candidate with unqualified version
        List<Version> sorted = new ArrayList<Version>(matches.keySet());
        Collections.sort(sorted, Collections.reverseOrder(versionComparator));
        for (Version version : sorted) {
            T component = matches.get(version);
            if (!version.isQualified()) {
                details.select(component);
                return;
            }
            ComponentResolveMetadata metaData = component.getMetaData();
            if (metaData != null && "release".equals(metaData.getStatus())) {
                details.select(component);
                return;
            }
        }

        // Nothing - just return the highest version
        details.select(matches.get(sorted.get(0)));
    }
}
