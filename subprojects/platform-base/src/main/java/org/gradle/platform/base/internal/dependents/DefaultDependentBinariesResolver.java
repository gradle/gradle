/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.platform.base.internal.dependents;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier;
import org.gradle.internal.Cast;
import org.gradle.platform.base.internal.BinarySpecInternal;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

public class DefaultDependentBinariesResolver implements DependentBinariesResolver {

    private final Map<String, DependentBinariesResolutionStrategy> strategies = Maps.newLinkedHashMap();


    @Override
    public void register(DependentBinariesResolutionStrategy strategy) {
        checkNotNull(strategy, "strategy must not be null");
        strategies.put(strategy.getName(), strategy);
    }

    @Override
    public <T extends DependentBinariesResolutionStrategy> T getStrategy(String name, Class<T> type) {
        return Cast.cast(type, strategies.get(name));
    }

    @Override
    public DependentBinariesResolutionResult resolve(BinarySpecInternal target) {
        List<DependentBinariesResolvedResult> roots = Lists.newArrayList();
        for (DependentBinariesResolutionStrategy strategy : strategies.values()) {
            DependentBinariesResolutionResult result = strategy.resolve(target);
            roots.add(result.getRoot());
        }
        return new DefaultDependentBinariesResolutionResult(mergeResults(roots));
    }

    private DependentBinariesResolvedResult mergeResults(Collection<DependentBinariesResolvedResult> results) {
        DependentBinariesResolvedResult first = results.iterator().next();
        if (results.size() == 1) {
            return first;
        }
        boolean hasNotBuildables = false;
        boolean hasTestSuites = false;
        LinkedListMultimap<LibraryBinaryIdentifier, DependentBinariesResolvedResult> index = LinkedListMultimap.create();
        List<DependentBinariesResolvedResult> allChildren = Lists.newArrayList();
        for (DependentBinariesResolvedResult result : results) {
            if (!result.isBuildable()) {
                hasNotBuildables = true;
            }
            if (result.isTestSuite()) {
                hasTestSuites = true;
            }
            allChildren.addAll(result.getChildren());
            for (DependentBinariesResolvedResult child : result.getChildren()) {
                index.put(child.getId(), child);
            }
        }
        List<DependentBinariesResolvedResult> children = Lists.newArrayList();
        for (Collection<DependentBinariesResolvedResult> childResults : index.asMap().values()) {
            children.add(mergeResults(childResults));
        }
        return new DefaultDependentBinariesResolvedResult(first.getId(), first.getProjectScopedName(), !hasNotBuildables, hasTestSuites, children);
    }
}
