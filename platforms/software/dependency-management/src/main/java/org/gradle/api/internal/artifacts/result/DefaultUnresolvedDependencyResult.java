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

public class DefaultUnresolvedDependencyResult implements UnresolvedDependencyResult {

    private final ComponentSelector requested;
    private final ResolvedComponentResult from;
    private final boolean constraint;
    private final ComponentSelectionReason reason;
    private final ModuleVersionResolveException failure;

    public DefaultUnresolvedDependencyResult(
        ComponentSelector requested,
        ResolvedComponentResult from,
        boolean constraint,
        ComponentSelectionReason reason,
        ModuleVersionResolveException failure
    ) {
        this.requested = requested;
        this.from = from;
        this.constraint = constraint;
        this.reason = reason;
        this.failure = failure;
    }

    @Override
    public ComponentSelector getRequested() {
        return requested;
    }

    @Override
    public ResolvedComponentResult getFrom() {
        return from;
    }

    @Override
    public boolean isConstraint() {
        return constraint;
    }

    @Override
    public ComponentSelectionReason getAttemptedReason() {
        return reason;
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
    public String toString() {
        return getRequested() + " -> " + getAttempted() + " - " + failure.getMessage();
    }

}
