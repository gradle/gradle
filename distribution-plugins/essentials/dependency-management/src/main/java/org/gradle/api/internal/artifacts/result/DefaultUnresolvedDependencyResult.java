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

import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.internal.resolve.ModuleVersionResolveException;

public class DefaultUnresolvedDependencyResult extends AbstractDependencyResult implements UnresolvedDependencyResult {
    private final ComponentSelectionReason reason;
    private final ModuleVersionResolveException failure;

    public DefaultUnresolvedDependencyResult(ComponentSelector requested, boolean constraint, ComponentSelectionReason reason,
                                             ResolvedComponentResult from, ModuleVersionResolveException failure) {
        super(requested, from, constraint);
        this.reason = reason;
        this.failure = failure;
    }

    @Override
    public ModuleVersionResolveException getFailure() {
        return failure;
    }

    @Override
    public ComponentSelector getAttempted() {
        return failure.getSelector();
    }

    @Override
    public ComponentSelectionReason getAttemptedReason() {
        return reason;
    }

    @Override
    public String toString() {
        return getRequested() + " -> " + getAttempted() + " - " + failure.getMessage();
    }
}
