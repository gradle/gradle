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

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.VersionInfo;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.DependencyEdge;
import org.gradle.util.CollectionUtils;

import java.util.Collection;
import java.util.Comparator;

public class DependencyResultSorter {
    /**
     * sorts by group:name:version mostly.
     * If requested matches selected then it will override the version comparison
     * so that the dependency that was selected is more prominent.
     */
    public static Collection<DependencyEdge> sort(Collection<DependencyEdge> input, VersionSelectorScheme versionSelectorScheme, VersionComparator versionComparator) {
        return CollectionUtils.sort(input, new DependencyComparator(versionSelectorScheme, versionComparator));
    }

    private static class DependencyComparator implements Comparator<DependencyEdge> {
        private final VersionSelectorScheme versionSelectorScheme;
        private final VersionComparator versionComparator;

        private DependencyComparator(VersionSelectorScheme versionSelectorScheme, VersionComparator versionComparator) {
            this.versionSelectorScheme = versionSelectorScheme;
            this.versionComparator = versionComparator;
        }

        @Override
        public int compare(DependencyEdge left, DependencyEdge right) {
            checkRequestedComponentSelectorType(left);
            checkRequestedComponentSelectorType(right);

            if(isLeftProjectButRightIsModuleComponentSelector(left, right)) {
                return -1;
            }

            if(isLeftModuleButRightIsProjectComponentSelector(left, right)) {
                return 1;
            }

            if(isLeftAndRightProjectComponentSelector(left, right)) {
                return compareRequestedProjectComponentSelectors(left, right);
            }

            if(isLeftAndRightModuleComponentSelector(left, right)) {
                return compareModuleComponentSelectors(left, right);
            }

            return 0;
        }

        private void checkRequestedComponentSelectorType(DependencyEdge dependencyEdge) {
            if(dependencyEdge == null || dependencyEdge.getRequested() == null) {
                throw new IllegalArgumentException("Dependency edge or the requested component selector may not be null");
            }

            ComponentSelector requested = dependencyEdge.getRequested();

            if(!isExpectedComponentSelector(requested)) {
                throw new IllegalArgumentException("Unexpected component selector type for dependency edge: " + requested.getClass());
            }
        }

        private boolean isExpectedComponentSelector(ComponentSelector componentSelector) {
            return componentSelector instanceof ProjectComponentSelector || componentSelector instanceof ModuleComponentSelector;
        }

        private boolean isLeftProjectButRightIsModuleComponentSelector(DependencyEdge left, DependencyEdge right) {
            return left.getRequested() instanceof ProjectComponentSelector && right.getRequested() instanceof ModuleComponentSelector;
        }

        private boolean isLeftModuleButRightIsProjectComponentSelector(DependencyEdge left, DependencyEdge right) {
            return left.getRequested() instanceof ModuleComponentSelector && right.getRequested() instanceof ProjectComponentSelector;
        }

        private boolean isLeftAndRightProjectComponentSelector(DependencyEdge left, DependencyEdge right) {
            return left.getRequested() instanceof ProjectComponentSelector && right.getRequested() instanceof ProjectComponentSelector;
        }

        private boolean isLeftAndRightModuleComponentSelector(DependencyEdge left, DependencyEdge right) {
            return left.getRequested() instanceof ModuleComponentSelector && right.getRequested() instanceof ModuleComponentSelector;
        }

        private int compareModuleComponentSelectors(DependencyEdge left, DependencyEdge right) {
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
            boolean leftDynamic = versionSelectorScheme.parseSelector(leftRequested.getVersionConstraint().getPreferredVersion()).isDynamic();
            boolean rightDynamic = versionSelectorScheme.parseSelector(rightRequested.getVersionConstraint().getPreferredVersion()).isDynamic();
            if (leftDynamic && !rightDynamic) {
                return 1;
            } else if (!leftDynamic && rightDynamic) {
                return -1;
            }

            int byVersion;
            if (leftDynamic && rightDynamic) {
                // order dynamic selectors lexicographically
                byVersion = leftRequested.getVersionConstraint().getPreferredVersion().compareTo(rightRequested.getVersionConstraint().getPreferredVersion());
            } else {
                // order static selectors semantically
                byVersion = compareVersions(leftRequested.getVersionConstraint().getPreferredVersion(), rightRequested.getVersionConstraint().getPreferredVersion());
            }
            if (byVersion != 0) {
                return byVersion;
            }

            return compareFromComponentIdentifiers(left.getFrom(), right.getFrom());
        }

        private int compareRequestedProjectComponentSelectors(DependencyEdge left, DependencyEdge right) {
            ProjectComponentSelector leftRequested = (ProjectComponentSelector)left.getRequested();
            ProjectComponentSelector rightRequested = (ProjectComponentSelector)right.getRequested();
            return leftRequested.getProjectPath().compareTo(rightRequested.getProjectPath());
        }

        public int compareFromComponentIdentifiers(ComponentIdentifier left, ComponentIdentifier right) {
            if(isLeftAndRightFromProjectComponentIdentifier(left, right)) {
                return compareFromProjectComponentIdentifiers(left, right);
            }

            if(isLeftAndRightFromModuleComponentIdentifier(left, right)) {
                return compareFromModuleComponentIdentifiers(left, right);
            }

            return isLeftFromProjectButRightIsModuleComponentIdentifier(left, right) ? -1 : 1;
        }

        private int compareFromProjectComponentIdentifiers(ComponentIdentifier left, ComponentIdentifier right) {
            ProjectComponentIdentifier leftFrom = (ProjectComponentIdentifier)left;
            ProjectComponentIdentifier rightFrom = (ProjectComponentIdentifier)right;
            return leftFrom.getProjectPath().compareTo(rightFrom.getProjectPath());
        }

        private int compareFromModuleComponentIdentifiers(ComponentIdentifier left, ComponentIdentifier right) {
            ModuleComponentIdentifier leftFrom = (ModuleComponentIdentifier)left;
            ModuleComponentIdentifier rightFrom = (ModuleComponentIdentifier)right;
            int byGroup = leftFrom.getGroup().compareTo(rightFrom.getGroup());
            if (byGroup != 0) {
                return byGroup;
            }

            int byModule = leftFrom.getModule().compareTo(rightFrom.getModule());
            if (byModule != 0) {
                return byModule;
            }

            return compareVersions(leftFrom.getVersion(), rightFrom.getVersion());
        }

        private int compareVersions(String left, String right) {
            return versionComparator.compare(new VersionInfo(left), new VersionInfo(right));
        }

        private boolean isLeftAndRightFromProjectComponentIdentifier(ComponentIdentifier left, ComponentIdentifier right) {
            return left instanceof ProjectComponentIdentifier && right instanceof ProjectComponentIdentifier;
        }

        private boolean isLeftAndRightFromModuleComponentIdentifier(ComponentIdentifier left, ComponentIdentifier right) {
            return left instanceof ModuleComponentIdentifier && right instanceof ModuleComponentIdentifier;
        }

        private boolean isLeftFromProjectButRightIsModuleComponentIdentifier(ComponentIdentifier left, ComponentIdentifier right) {
            return left instanceof ProjectComponentIdentifier && right instanceof ModuleComponentIdentifier;
        }
    }
}
