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

/**
 * Default implementation of {@link UnresolvedDependencyResult}.
 */
public class DefaultUnresolvedDependencyResult implements UnresolvedDependencyResult {

    private final ComponentSelector requested;
    private final ResolvedComponentResult from;
    private final boolean constraint;
    private final ComponentSelectionReason reason;
    private final ModuleVersionResolveException failure;

    private final int hashCode;

    public DefaultUnresolvedDependencyResult(
        ComponentSelector requested,
        ResolvedComponentResult from,
        boolean constraint,
        ModuleVersionResolveException failure,
        ComponentSelectionReason reason
    ) {
        this.requested = requested;
        this.from = from;
        this.constraint = constraint;
        this.failure = failure;
        this.reason = reason;

        this.hashCode = computeHashCode(constraint, failure, from, reason, requested);
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
    public ComponentSelector getAttempted() {
        return failure.getSelector();
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
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultUnresolvedDependencyResult that = (DefaultUnresolvedDependencyResult) o;
        return constraint == that.constraint &&
            requested.equals(that.requested) &&
            from.equals(that.from) &&
            reason.equals(that.reason) &&
            failure.equals(that.failure);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    private static int computeHashCode(
        boolean constraint,
        ModuleVersionResolveException failure,
        ResolvedComponentResult from,
        ComponentSelectionReason reason,
        ComponentSelector requested
    ) {
        int result = requested.hashCode();
        result = 31 * result + from.hashCode();
        result = 31 * result + Boolean.hashCode(constraint);
        result = 31 * result + reason.hashCode();
        result = 31 * result + failure.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return getRequested() + " -> " + getAttempted() + " - " + failure.getMessage();
    }

}
