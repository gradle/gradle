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

package org.gradle.api.internal.artifacts.ivyservice;

import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.internal.artifacts.DependencyResolveDetailsInternal;
import org.gradle.api.internal.artifacts.dsl.ComponentSelectorParsers;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.resolve.ModuleVersionResolveException;

public class DefaultDependencyResolveDetails<T extends ComponentSelector> implements DependencyResolveDetailsInternal<T> {
    private final T selector;
    private final ModuleVersionSelector requested;
    private ComponentSelectionReason selectionReason;
    private ComponentSelector target;

    public DefaultDependencyResolveDetails(T selector, ModuleVersionSelector requested) {
        this.selector = selector;
        this.target = selector;
        this.requested = requested;
    }

    @Override
    public T getSelector() {
        return selector;
    }

    @Override
    public ModuleVersionSelector getRequested() {
        return requested;
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
        if (target instanceof ModuleComponentSelector) {
            ModuleComponentSelector moduleTarget = (ModuleComponentSelector) target;
            if (!version.equals(moduleTarget.getVersion())) {
                useTarget(DefaultModuleComponentSelector.newSelector(moduleTarget.getGroup(), moduleTarget.getModule(), version), selectionReason);
            } else {
                useTarget(moduleTarget, selectionReason);
            }
        } else {
            throw new ModuleVersionResolveException(target, "Cannot substitute %s with version '" + version + "'.");
        }
    }

    @Override
    public void useTarget(Object notation) {
        ComponentSelector target = ComponentSelectorParsers.parser().parseNotation(notation);
        useTarget(target, VersionSelectionReasons.SELECTED_BY_RULE);
    }

    private void useTarget(ComponentSelector target, ComponentSelectionReason selectionReason) {
        this.target = target;
        this.selectionReason = selectionReason;
    }

    @Override
    public ComponentSelectionReason getSelectionReason() {
        return selectionReason;
    }

    @Override
    public ComponentSelector getTarget() {
        return target;
    }

    @Override
    public boolean isUpdated() {
        return selectionReason != null;
    }
}
