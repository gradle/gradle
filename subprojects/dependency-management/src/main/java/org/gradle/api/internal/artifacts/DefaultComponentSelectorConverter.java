/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.artifacts;

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.internal.artifacts.component.ComponentIdentifierFactory;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentRegistry;
import org.gradle.internal.component.local.model.LocalComponentMetadata;

public class DefaultComponentSelectorConverter implements ComponentSelectorConverter {
    private static final ModuleVersionSelector UNKNOWN_MODULE_VERSION_SELECTOR = DefaultModuleVersionSelector.newSelector("", "unknown", "");
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final ComponentIdentifierFactory componentIdentifierFactory;
    private final LocalComponentRegistry localComponentRegistry;

    public DefaultComponentSelectorConverter(ImmutableModuleIdentifierFactory moduleIdentifierFactory, ComponentIdentifierFactory componentIdentifierFactory, LocalComponentRegistry localComponentRegistry) {
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.componentIdentifierFactory = componentIdentifierFactory;
        this.localComponentRegistry = localComponentRegistry;
    }

    @Override
    public ModuleIdentifier getModule(ComponentSelector componentSelector) {
        if (componentSelector instanceof ModuleComponentSelector) {
            ModuleComponentSelector module = (ModuleComponentSelector) componentSelector;
            return moduleIdentifierFactory.module(module.getGroup(), module.getModule());
        }
        ModuleVersionSelector moduleVersionSelector = getSelector(componentSelector);
        return moduleIdentifierFactory.module(moduleVersionSelector.getGroup(), moduleVersionSelector.getName());
    }

    @Override
    public ModuleVersionSelector getSelector(ComponentSelector selector) {
        if (selector instanceof ModuleComponentSelector) {
            return DefaultModuleVersionSelector.newSelector((ModuleComponentSelector) selector);
        }
        if (selector instanceof ProjectComponentSelector) {
            ProjectComponentSelector projectSelector = (ProjectComponentSelector) selector;
            ProjectComponentIdentifier projectId = componentIdentifierFactory.createProjectComponentIdentifier(projectSelector);
            LocalComponentMetadata projectComponent = localComponentRegistry.getComponent(projectId);
            if (projectComponent != null) {
                return DefaultModuleVersionSelector.newSelector(projectComponent.getId().getGroup(), projectComponent.getId().getName(), projectComponent.getId().getVersion());
            }
        }
        return UNKNOWN_MODULE_VERSION_SELECTOR;
    }
}
