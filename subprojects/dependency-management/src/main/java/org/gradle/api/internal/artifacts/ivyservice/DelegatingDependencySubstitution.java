/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice;

import org.gradle.api.artifacts.DependencySubstitution;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal;

public class DelegatingDependencySubstitution implements DependencySubstitutionInternal {
    private final DependencySubstitutionInternal delegate;

    public DelegatingDependencySubstitution(DependencySubstitution delegate) {
        this.delegate = (DependencySubstitutionInternal) delegate;
    }

    @Override
    public ComponentSelector getRequested() {
        return delegate.getRequested();
    }

    @Override
    public ModuleVersionSelector getOldRequested() {
        return delegate.getOldRequested();
    }

    @Override
    public void useTarget(Object notation) {
        delegate.useTarget(notation);
    }

    @Override
    public void useTarget(Object notation, ComponentSelectionReason selectionReason) {
        delegate.useTarget(notation, selectionReason);
    }

    @Override
    public ComponentSelector getTarget() {
        return delegate.getTarget();
    }

    @Override
    public ComponentSelectionReason getSelectionReason() {
        return delegate.getSelectionReason();
    }

    @Override
    public boolean isUpdated() {
        return delegate.isUpdated();
    }
}
