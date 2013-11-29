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

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionMatcher;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.DependencyEdge;
import org.gradle.util.CollectionUtils;

import java.util.Collection;
import java.util.Comparator;

/**
 * Created: 17/08/2012
 */
public class DependencyResultSorter {

    /**
     * sorts by group:name:version mostly.
     * If requested matches selected then it will override the version comparison
     * so that the dependency that was selected is more prominent.
     */
    public static Collection<DependencyEdge> sort(Collection<DependencyEdge> input, VersionMatcher versionMatcher) {
        return CollectionUtils.sort(input, new DependencyComparator(versionMatcher));
    }

    private static class DependencyComparator implements Comparator<DependencyEdge> {
        private final VersionMatcher matcher;

        private DependencyComparator(VersionMatcher matcher) {
            this.matcher = matcher;
        }

        public int compare(DependencyEdge left, DependencyEdge right) {
            ModuleComponentSelector leftRequested = (ModuleComponentSelector)left.getRequested();
            ModuleComponentSelector rightRequested = (ModuleComponentSelector)right.getRequested();
            int byGroup = leftRequested.getGroup().compareTo(rightRequested.getGroup());
            if (byGroup != 0) {
                return byGroup;
            }

            int byModule = leftRequested.getModule().compareTo(rightRequested.getModule());
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
            boolean leftDynamic = matcher.isDynamic(leftRequested.getVersion());
            boolean rightDynamic = matcher.isDynamic(rightRequested.getVersion());
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
                byVersion = matcher.compare(leftRequested.getVersion(), rightRequested.getVersion());
            }
            if (byVersion != 0) {
                return byVersion;
            }

            ModuleComponentIdentifier leftFrom = (ModuleComponentIdentifier)left.getFrom();
            ModuleComponentIdentifier rightFrom = (ModuleComponentIdentifier)right.getFrom();
            byGroup = leftFrom.getGroup().compareTo(rightFrom.getGroup());
            if (byGroup != 0) {
                return byGroup;
            }

            byModule = leftFrom.getModule().compareTo(rightFrom.getModule());
            if (byModule != 0) {
                return byModule;
            }

            return matcher.compare(leftFrom.getVersion(), rightFrom.getVersion());
        }
    }
}
