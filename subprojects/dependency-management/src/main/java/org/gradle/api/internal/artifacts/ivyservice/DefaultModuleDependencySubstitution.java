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

import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.internal.artifacts.ModuleDependencySubstitutionInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;

public class DefaultModuleDependencySubstitution extends AbstractDependencySubstitution<ModuleComponentSelector> implements ModuleDependencySubstitutionInternal {

    public DefaultModuleDependencySubstitution(ModuleComponentSelector requested, ModuleVersionSelector oldRequested) {
        super(requested, oldRequested);
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
        ModuleComponentSelector moduleTarget = getRequested();
        if (!version.equals(moduleTarget.getVersion())) {
            useTarget(DefaultModuleComponentSelector.newSelector(moduleTarget.getGroup(), moduleTarget.getModule(), version), selectionReason);
        } else {
            useTarget(moduleTarget, selectionReason);
        }
    }
}
