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
import org.gradle.api.artifacts.result.ModuleVersionSelectionReason;
import org.gradle.api.internal.artifacts.DependencyResolveDetailsInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons;

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector;

/**
* by Szczepan Faber, created at: 11/29/12
*/
public class DefaultDependencyResolveDetails implements DependencyResolveDetailsInternal {
    private final ModuleVersionSelector requested;
    private ModuleVersionSelectionReason selectionReason;
    private ModuleVersionSelector target;

    public DefaultDependencyResolveDetails(ModuleVersionSelector requested) {
        this.requested = requested;
        this.target = requested;
    }

    public ModuleVersionSelector getRequested() {
        return requested;
    }

    public void useVersion(String version) {
        useVersion(version, VersionSelectionReasons.SELECTED_BY_ACTION);
    }

    public void useVersion(String version, ModuleVersionSelectionReason selectionReason) {
        assert selectionReason != null;
        if (version == null) {
            throw new IllegalArgumentException("Configuring the dependency resolve details with 'null' version is not allowed.");
        }
        if (!version.equals(target.getVersion())) {
            target = newSelector(target.getGroup(), target.getName(), version);
        }
        this.selectionReason = selectionReason;
    }

    public ModuleVersionSelectionReason getSelectionReason() {
        return selectionReason;
    }

    public ModuleVersionSelector getTarget() {
        return target;
    }

    public boolean isUpdated() {
        return selectionReason != null;
    }
}