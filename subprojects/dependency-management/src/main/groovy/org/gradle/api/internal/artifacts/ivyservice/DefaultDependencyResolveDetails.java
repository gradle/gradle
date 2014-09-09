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
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.internal.artifacts.DependencyResolveDetailsInternal;
import org.gradle.api.internal.artifacts.dsl.ModuleVersionSelectorParsers;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons;

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector;

public class DefaultDependencyResolveDetails implements DependencyResolveDetailsInternal {
    private final ModuleVersionSelector requested;
    private ComponentSelectionReason selectionReason;
    private ModuleVersionSelector target;

    public DefaultDependencyResolveDetails(ModuleVersionSelector requested) {
        this.requested = requested;
        this.target = requested;
    }

    public ModuleVersionSelector getRequested() {
        return requested;
    }

    public void useVersion(String version) {
        useVersion(version, VersionSelectionReasons.SELECTED_BY_RULE);
    }

    public void useVersion(String version, ComponentSelectionReason selectionReason) {
        assert selectionReason != null;
        if (version == null) {
            throw new IllegalArgumentException("Configuring the dependency resolve details with 'null' version is not allowed.");
        }
        if (!version.equals(target.getVersion())) {
            target = newSelector(target.getGroup(), target.getName(), version);
        }
        this.selectionReason = selectionReason;
    }

    public void useTarget(Object notation) {
        this.target = ModuleVersionSelectorParsers.parser().parseNotation(notation);
        this.selectionReason = VersionSelectionReasons.SELECTED_BY_RULE;
    }

    public ComponentSelectionReason getSelectionReason() {
        return selectionReason;
    }

    public ModuleVersionSelector getTarget() {
        return target;
    }

    public boolean isUpdated() {
        return selectionReason != null;
    }
}