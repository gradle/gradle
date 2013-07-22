/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.tasks.diagnostics.internal.insight;

import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.plugins.version.ChainVersionMatcher;
import org.apache.ivy.plugins.version.LatestVersionMatcher;
import org.apache.ivy.plugins.version.SubVersionMatcher;
import org.apache.ivy.plugins.version.VersionRangeMatcher;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.internal.artifacts.version.LatestVersionSemanticComparator;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.DependencyEdge;

import java.util.*;

/**
 * Created: 17/08/2012
 */
public class DependencyResultSorter {

    /**
     * sorts by group:name:version mostly.
     * If requested matches selected then it will override the version comparison
     * so that the dependency that was selected is more prominent.
     */
    public static Collection<DependencyEdge> sort(Collection<DependencyEdge> input) {
        List<DependencyEdge> out = new ArrayList<DependencyEdge>(input);
        Collections.sort(out, new DependencyComparator());
        return out;
    }

    private static class DependencyComparator implements Comparator<DependencyEdge> {

        private final LatestVersionSemanticComparator versionComparator = new LatestVersionSemanticComparator();
        private final ChainVersionMatcher matcher;

        private DependencyComparator() {
            matcher = new ChainVersionMatcher();
            matcher.add(new VersionRangeMatcher());
            matcher.add(new SubVersionMatcher());
            matcher.add(new LatestVersionMatcher());
        }

        public int compare(DependencyEdge left, DependencyEdge right) {
            ModuleVersionSelector leftRequested = left.getRequested();
            ModuleVersionSelector rightRequested = right.getRequested();
            int byGroup = leftRequested.getGroup().compareTo(rightRequested.getGroup());
            if (byGroup != 0) {
                return byGroup;
            }

            int byModule = leftRequested.getName().compareTo(rightRequested.getName());
            if (byModule != 0) {
                return byModule;
            }

            //if selected matches requested version comparison is overridden
            boolean leftMatches = leftRequested.matchesStrictly(left.getActual());
            boolean rightMatches = rightRequested.matchesStrictly(right.getActual());
            if (leftMatches && !rightMatches) {
                return -1;
            } else if (!leftMatches && rightMatches) {
                return 1;
            }

            //order dynamic selectors after static selectors
            boolean leftDynamic = matcher.isDynamic(ModuleRevisionId.newInstance(leftRequested.getGroup(), leftRequested.getName(), leftRequested.getVersion()));
            boolean rightDynamic = matcher.isDynamic(ModuleRevisionId.newInstance(rightRequested.getGroup(), rightRequested.getName(), rightRequested.getVersion()));
            if (leftDynamic && !rightDynamic) {
                return 1;
            } else if (!leftDynamic && rightDynamic) {
                return -1;
            }

            int byVersion;
            if (leftDynamic && rightDynamic) {
                // order dynamic selectors lexicographically
                byVersion = leftRequested.getVersion().compareTo(rightRequested.getVersion());
            } else {
                // order static selectors semantically
                byVersion = versionComparator.compare(leftRequested.getVersion(), rightRequested.getVersion());
            }
            if (byVersion != 0) {
                return byVersion;
            }

            ModuleVersionIdentifier leftFrom = left.getFrom();
            ModuleVersionIdentifier rightFrom = right.getFrom();
            byGroup = leftFrom.getGroup().compareTo(rightFrom.getGroup());
            if (byGroup != 0) {
                return byGroup;
            }

            byModule = leftFrom.getName().compareTo(rightFrom.getName());
            if (byModule != 0) {
                return byModule;
            }

            return versionComparator.compare(leftFrom.getVersion(), rightFrom.getVersion());
        }
    }
}
