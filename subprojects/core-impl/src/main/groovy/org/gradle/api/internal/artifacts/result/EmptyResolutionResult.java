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

package org.gradle.api.internal.artifacts.result;

import org.gradle.api.artifacts.Module;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.result.ModuleVersionSelectionReason;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.artifacts.result.ResolvedModuleVersionResult;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons;

import java.util.Collections;
import java.util.Set;

/**
 * by Szczepan Faber, created at: 8/16/12
 */
public class EmptyResolutionResult implements ResolutionResult {

    private final ResolvedModuleVersionResult root;

    public EmptyResolutionResult(final Module module) {
        root = new ResolvedModuleVersionResult() {
            public ModuleVersionIdentifier getId() {
                return DefaultModuleVersionIdentifier.newId(module);
            }

            public Set<? extends ResolvedDependencyResult> getDependencies() {
                return Collections.emptySet();
            }

            public Set<? extends ResolvedDependencyResult> getDependents() {
                return Collections.emptySet();
            }

            public ModuleVersionSelectionReason getSelectionReason() {
                return VersionSelectionReasons.REQUESTED;
            }
        };
    }

    public ResolvedModuleVersionResult getRoot() {
        return root;
    }

    public Set<? extends ResolvedDependencyResult> getAllDependencies() {
        return Collections.emptySet();
    }
}
