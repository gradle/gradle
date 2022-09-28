/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder;

import com.google.common.collect.Lists;
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.ExactVersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestVersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.Version;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.selectors.ResolvableSelectorState;
import org.gradle.internal.component.model.IvyArtifactName;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class ModuleSelectors<T extends ResolvableSelectorState> implements Iterable<T> {

    private final Version emptyVersion;

    private final VersionParser versionParser;
    private final List<T> selectors = Lists.newArrayList();
    private boolean deferSelection;
    private boolean forced;
    private final Comparator<ResolvableSelectorState> selectorComparator;

    public ModuleSelectors(Comparator<Version> versionComparator, VersionParser versionParser) {
        this.versionParser = versionParser;
        this.emptyVersion = versionParser.transform("");
        this.selectorComparator = new SelectorComparator(versionComparator);
    }

    private class SelectorComparator implements Comparator<ResolvableSelectorState> {
        private final Comparator<Version> versionComparator;

        private SelectorComparator(Comparator<Version> versionComparator) {
            this.versionComparator = versionComparator;
        }

        @Override
        public int compare(ResolvableSelectorState left, ResolvableSelectorState right) {
            if (right.isProject() == left.isProject()) {
                if (right.isFromLock() == left.isFromLock()) {
                    if (hasLatestSelector(right) == hasLatestSelector(left)) {
                        if (isDynamicSelector(right) == isDynamicSelector(left)) {
                            Version o1RequiredVersion = ModuleSelectors.this.requiredVersion(right);
                            Version o2RequiredVersion = ModuleSelectors.this.requiredVersion(left);
                            int compareRequiredVersion = versionComparator.compare(o1RequiredVersion, o2RequiredVersion);
                            if (compareRequiredVersion == 0) {
                                Version o1Version = ModuleSelectors.this.preferredVersion(right);
                                Version o2Version = ModuleSelectors.this.preferredVersion(left);
                                return versionComparator.compare(o1Version, o2Version);
                            } else {
                                return compareRequiredVersion;
                            }
                        } else {
                            return Boolean.compare(isDynamicSelector(left), isDynamicSelector(right));
                        }
                    } else {
                        return Boolean.compare(hasLatestSelector(right), hasLatestSelector(left));
                    }
                } else {
                    return Boolean.compare(right.isFromLock(), left.isFromLock());
                }
            }
            return Boolean.compare(right.isProject(), left.isProject());
        }
    }

    public boolean checkDeferSelection() {
        if (deferSelection) {
            deferSelection = false;
            return true;
        }
        return false;
    }

    @Override
    public Iterator<T> iterator() {
        return selectors.iterator();
    }

    public void add(T selector, boolean deferSelection) {
        this.deferSelection = deferSelection;
        if (selectors.isEmpty() || forced) {
            selectors.add(selector);
        } else {
            doAdd(selector);
        }
        forced = forced || selector.isForce();
    }

    private void doAdd(T selector) {
        int size = selectors.size();
        if (size == 1) {
            doAddWhenListHasOneElement(selector);
        } else {
            doAddWhenListHasManyElements(selectors, selector, size);
        }
    }

    private <T extends ResolvableSelectorState> void doAddWhenListHasManyElements(List<T> selectors, T selector, int size) {
        int insertionPoint = Collections.binarySearch(selectors, selector, selectorComparator);
        insertionPoint = advanceToPreserveOrder(selectors, selector, size, insertionPoint);
        if (insertionPoint < 0) {
            insertionPoint = ~insertionPoint;
        }
        selectors.add(insertionPoint, selector);
    }

    private <T extends ResolvableSelectorState> int advanceToPreserveOrder(List<T> selectors, T selector, int size, int insertionPoint) {
        while (insertionPoint > 0 && insertionPoint < size && selectorComparator.compare(selectors.get(insertionPoint), selector) == 0) {
            insertionPoint++;
        }
        return insertionPoint;
    }

    private void doAddWhenListHasOneElement(T selector) {
        T first = selectors.get(0);
        int c = selectorComparator.compare(first, selector);
        if (c <= 0) {
            selectors.add(selector);
        } else {
            selectors.add(0, selector);
        }
    }

    public boolean remove(T selector) {
        return selectors.remove(selector);
    }

    private static boolean isDynamicSelector(ResolvableSelectorState selector) {
        return selector.getVersionConstraint() != null && selector.getVersionConstraint().isDynamic();
    }

    private static boolean hasLatestSelector(ResolvableSelectorState selector) {
        return selector.getVersionConstraint() != null
            && hasLatestSelector(selector.getVersionConstraint());
    }

    private static boolean hasLatestSelector(ResolvedVersionConstraint vc) {
        // Latest is only given priority if it's in a require
        return hasLatestSelector(vc.getRequiredSelector());
    }

    private static boolean hasLatestSelector(@Nullable VersionSelector versionSelector) {
        return versionSelector instanceof LatestVersionSelector;
    }

    private Version requiredVersion(ResolvableSelectorState selector) {
        ResolvedVersionConstraint versionConstraint = selector.getVersionConstraint();
        if (versionConstraint == null) {
            return emptyVersion;
        }
        return versionOf(versionConstraint.getRequiredSelector());
    }

    private Version preferredVersion(ResolvableSelectorState selector) {
        ResolvedVersionConstraint versionConstraint = selector.getVersionConstraint();
        if (versionConstraint == null) {
            return emptyVersion;
        }
        return versionOf(versionConstraint.getPreferredSelector());
    }

    private Version versionOf(@Nullable VersionSelector selector) {
        if (!(selector instanceof ExactVersionSelector)) {
            return emptyVersion;
        }
        return versionParser.transform(selector.getSelector());
    }

    public int size() {
        return selectors.size();
    }

    @Nullable
    public T first() {
        if (size() == 0) {
            return null;
        }
        return selectors.get(0);
    }

    @Nullable
    public IvyArtifactName getFirstDependencyArtifact() {
        for (T selector: selectors) {
            IvyArtifactName artifact = selector.getFirstDependencyArtifact();
            if (artifact != null) {
                return artifact;
            }
        }
        return null;
    }
}
