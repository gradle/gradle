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

package org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution;

import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector;
import org.gradle.api.internal.artifacts.DependencyResolveDetailsInternal;
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal;
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint;
import org.gradle.api.internal.artifacts.dsl.ModuleVersionSelectorParsers;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;

public class DefaultDependencyResolveDetails implements DependencyResolveDetailsInternal {

    private final DependencySubstitutionInternal delegate;
    private ModuleVersionSelector requested;

    public DefaultDependencyResolveDetails(DependencySubstitutionInternal delegate, ModuleVersionSelector requested) {
        this.delegate = delegate;
        this.requested = requested;
    }

    @Override
    public ModuleVersionSelector getRequested() {
        return requested;
    }

    @Override
    public void useVersion(String version) {
        if (version == null) {
            throw new IllegalArgumentException("Configuring the dependency resolve details with 'null' version is not allowed.");
        }
        useVersion(new DefaultMutableVersionConstraint(version), VersionSelectionReasons.SELECTED_BY_RULE);
    }

    @Override
    public void useVersion(VersionConstraint version, ComponentSelectionReason selectionReason) {
        assert selectionReason != null;
        if (version == null) {
            throw new IllegalArgumentException("Configuring the dependency resolve details with 'null' version is not allowed.");
        }

//        ModuleVersionSelector currentTarget = determineTarget(delegate, requested);
        if (delegate.getTarget() instanceof ModuleComponentSelector) {
            ModuleComponentSelector target = (ModuleComponentSelector) delegate.getTarget();
            if (!version.equals(target.getVersionConstraint())) {
                delegate.useTarget(DefaultModuleComponentSelector.newSelector(target.getGroup(), target.getModule(), version), selectionReason);
            } else {
                // Still 'updated' with reason when version remains the same.
                delegate.useTarget(delegate.getTarget(), selectionReason);
            }
        } else {
            // If the current target is a project component, it must be unmodified from the requested
            ModuleComponentSelector newTarget = DefaultModuleComponentSelector.newSelector(requested.getGroup(), requested.getName(), version);
            delegate.useTarget(newTarget, selectionReason);
        }

    }

    @Override
    public void useTarget(Object notation) {
        ModuleVersionSelector newTarget = ModuleVersionSelectorParsers.parser().parseNotation(notation);
        delegate.useTarget(DefaultModuleComponentSelector.newSelector(newTarget), VersionSelectionReasons.SELECTED_BY_RULE);
    }

    @Override
    public ComponentSelectionReason getSelectionReason() {
        return delegate.getSelectionReason();
    }

    @Override
    public ModuleVersionSelector getTarget() {
        if (delegate.getTarget().equals(delegate.getRequested())) {
            return requested;
        }
        // The target may already be modified from the original requested
        if (delegate.getTarget() instanceof ModuleComponentSelector) {
            return DefaultModuleVersionSelector.newSelector((ModuleComponentSelector) delegate.getTarget());
        }
        // If the target is a project component, it has not been modified from the requested
        return requested;
    }

    @Override
    public boolean isUpdated() {
        return delegate.isUpdated();
    }
}
