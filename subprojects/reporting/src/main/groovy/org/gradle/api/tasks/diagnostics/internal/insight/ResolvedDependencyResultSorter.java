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

import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.internal.artifacts.version.LatestVersionSemanticComparator;
import org.gradle.api.specs.Spec;
import org.gradle.util.CollectionUtils;

import java.util.*;

/**
 * Created: 17/08/2012
 *
 * @author Szczepan Faber
 */
public class ResolvedDependencyResultSorter {

    /**
     * sorts by group:name:version mostly.
     * If requested matches selected then it will override the version comparison
     * so that the dependency that was selected is more prominent.
     */
    public static Collection<ResolvedDependencyResult> sort(Collection<ResolvedDependencyResult> input) {
        //dependencies with the same 'requested' should be presented in a single tree
        final Set<ModuleVersionSelector> uniqueRequested = new HashSet<ModuleVersionSelector>();
        List<ResolvedDependencyResult> out = CollectionUtils.filter(input, new LinkedList<ResolvedDependencyResult>(), new Spec<ResolvedDependencyResult>() {
            public boolean isSatisfiedBy(ResolvedDependencyResult element) {
                return uniqueRequested.add(element.getRequested());
            }
        });
        Collections.sort(out, new DependencyComparator());
        return out;
    }

    private static class DependencyComparator implements Comparator<ResolvedDependencyResult> {

        private final LatestVersionSemanticComparator versionComparator = new LatestVersionSemanticComparator();

        public int compare(ResolvedDependencyResult left, ResolvedDependencyResult right) {
            int byGroup = left.getRequested().getGroup().compareTo(right.getRequested().getGroup());
            if (byGroup != 0) {
                return byGroup;
            }

            int byModule = left.getRequested().getName().compareTo(right.getRequested().getName());
            if (byModule != 0) {
                return byModule;
            }

            //if selected matches requested version comparison is overridden
            if (left.getRequested().getAsSpec().isSatisfiedBy(left.getSelected().getId())) {
                return -1;
            } else if (right.getRequested().getAsSpec().isSatisfiedBy(right.getSelected().getId())) {
                return 1;
            }

            return versionComparator.compare(left.getRequested().getVersion(), right.getRequested().getVersion());
        }
    }
}
