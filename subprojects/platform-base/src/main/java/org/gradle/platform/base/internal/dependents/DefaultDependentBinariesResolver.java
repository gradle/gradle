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

import com.google.common.collect.Lists;
import org.gradle.platform.base.internal.BinarySpecInternal;

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
    public DependentBinariesResolutionResult resolve(BinarySpecInternal target, boolean includeTestSuites) {
        DependentBinariesResolvedResult root = null;
        for (DependentBinariesResolutionStrategy strategy : strategies) {
            DependentBinariesResolutionResult result = strategy.resolve(target, includeTestSuites);
            if (root == null) {
                root = result.getRoot();
            } else {
                root.getChildren().addAll(result.getRoot().getChildren());
            }
        }
        return new DefaultDependentBinariesResolutionResult(root);
    }
}
