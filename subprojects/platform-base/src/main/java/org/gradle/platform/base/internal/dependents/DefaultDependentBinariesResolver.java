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
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier;
import org.gradle.platform.base.internal.BinarySpecInternal;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

public class DefaultDependentBinariesResolver implements DependentBinariesResolver {

    private final List<DependentBinariesResolutionStrategy> strategies = Lists.newArrayList();

    @Override
    public void register(DependentBinariesResolutionStrategy strategy) {
        checkNotNull(strategy, "strategy must not be null");
        strategies.add(strategy);
    }

    @Override
    public DependentBinariesResolutionResult resolve(BinarySpecInternal target) {
        DependentBinariesResolvedResult root = null;
        for (DependentBinariesResolutionStrategy strategy : strategies) {
            DependentBinariesResolutionResult result = strategy.resolve(target);
            if (root == null) {
                root = result.getRoot();
            } else {
                root = mergeResults(Arrays.asList(root, result.getRoot()));
            }
        }
        return new DefaultDependentBinariesResolutionResult(root);
    }

    private DependentBinariesResolvedResult mergeResults(Iterable<DependentBinariesResolvedResult> results) {
        DependentBinariesResolvedResult first = results.iterator().next();
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
