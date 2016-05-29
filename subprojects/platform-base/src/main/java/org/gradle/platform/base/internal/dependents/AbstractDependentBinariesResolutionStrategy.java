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

import org.gradle.api.Nullable;
import org.gradle.platform.base.internal.BinarySpecInternal;

import java.util.List;

public abstract class AbstractDependentBinariesResolutionStrategy implements DependentBinariesResolutionStrategy {

    @Override
    public DependentBinariesResolutionResult resolve(BinarySpecInternal target, boolean includeTestSuites) {
        DependentBinariesResolvedResult root = new DefaultDependentBinariesResolvedResult(
            target.getId(),
            target.isBuildable(),
            isTestSuite(target),
            resolveDependents(target, includeTestSuites)
        );
        return new DefaultDependentBinariesResolutionResult(root);
    }

    @Nullable
    protected abstract List<DependentBinariesResolvedResult> resolveDependents(BinarySpecInternal target, boolean includeTestSuites);

    private static boolean isTestSuite(BinarySpecInternal target) {
        // TODO:PM How to test for test-suite? Cannot import TestSuiteBinarySpec here
        return false;
    }

}
