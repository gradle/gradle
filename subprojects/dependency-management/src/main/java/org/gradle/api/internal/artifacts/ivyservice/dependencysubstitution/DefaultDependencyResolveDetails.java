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
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector;
import org.gradle.api.internal.artifacts.DependencyResolveDetailsInternal;
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal;
import org.gradle.api.internal.artifacts.dsl.ModuleVersionSelectorParsers;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;

public class DefaultDependencyResolveDetails implements DependencyResolveDetailsInternal {

    private final DependencySubstitutionInternal delegate;
    private ModuleVersionSelector target;

    public DefaultDependencyResolveDetails(DependencySubstitutionInternal delegate) {
        this.delegate = delegate;
        target = determineTarget(delegate);
    }

    private static ModuleVersionSelector determineTarget(DependencySubstitutionInternal delegate) {
        // Temporary logic until we add DependencySubstitution back in
        if (delegate.getTarget() instanceof ModuleComponentSelector) {
            ModuleComponentSelector moduleComponentSelector = (ModuleComponentSelector) delegate.getTarget();
            return DefaultModuleVersionSelector.newSelector(moduleComponentSelector.getGroup(), moduleComponentSelector.getModule(), moduleComponentSelector.getVersion());
        }
        // If the target is a project component, it must be unmodified from the requested
        return delegate.getOldRequested();
    }

    @Override
    public ModuleVersionSelector getRequested() {
        return delegate.getOldRequested();
    }

    @Override
    public void useVersion(String version) {
        useVersion(version, VersionSelectionReasons.SELECTED_BY_RULE);
    }

    @Override
    public void useVersion(String version, ComponentSelectionReason selectionReason) {
        assert selectionReason != null;
        if (version == null) {
            throw new IllegalArgumentException("Configuring the dependency resolve details with 'null' version is not allowed.");
        }

        if (!version.equals(target.getVersion())) {
            target = DefaultModuleVersionSelector.newSelector(target.getGroup(), target.getName(), version);
            delegate.useTarget(DefaultModuleComponentSelector.newSelector(target), selectionReason);
        } else {
            // Still 'updated' with reason when version remains the same.
            delegate.useTarget(delegate.getTarget(), selectionReason);
        }
    }

    @Override
    public void useTarget(Object notation) {
        target = ModuleVersionSelectorParsers.parser().parseNotation(notation);
        delegate.useTarget(DefaultModuleComponentSelector.newSelector(target), VersionSelectionReasons.SELECTED_BY_RULE);
    }

    @Override
    public ComponentSelectionReason getSelectionReason() {
        return delegate.getSelectionReason();
    }

    @Override
    public ModuleVersionSelector getTarget() {
        return target;
    }

    @Override
    public boolean isUpdated() {
        return delegate.isUpdated();
    }
}
