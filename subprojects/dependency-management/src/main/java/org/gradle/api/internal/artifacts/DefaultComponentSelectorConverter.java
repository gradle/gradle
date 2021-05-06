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
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.LibraryComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.internal.artifacts.component.ComponentIdentifierFactory;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentRegistry;
import org.gradle.internal.component.local.model.LocalComponentMetadata;
import org.gradle.util.internal.GUtil;

public class DefaultComponentSelectorConverter implements ComponentSelectorConverter {
    private static final ModuleVersionSelector UNKNOWN_MODULE_VERSION_SELECTOR = DefaultModuleVersionSelector.newSelector(DefaultModuleIdentifier.newId("", "unknown"), "");
    private final ComponentIdentifierFactory componentIdentifierFactory;
    private final LocalComponentRegistry localComponentRegistry;

    public DefaultComponentSelectorConverter(ComponentIdentifierFactory componentIdentifierFactory, LocalComponentRegistry localComponentRegistry) {
        this.componentIdentifierFactory = componentIdentifierFactory;
        this.localComponentRegistry = localComponentRegistry;
    }

    @Override
    public ModuleIdentifier getModule(ComponentSelector componentSelector) {
        if (componentSelector instanceof ModuleComponentSelector) {
            ModuleComponentSelector module = (ModuleComponentSelector) componentSelector;
            return module.getModuleIdentifier();
        }
        ModuleVersionSelector moduleVersionSelector = getSelector(componentSelector);
        return moduleVersionSelector.getModule();
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
                ModuleVersionIdentifier moduleVersionId = projectComponent.getModuleVersionId();
                return DefaultModuleVersionSelector.newSelector(moduleVersionId.getModule(), moduleVersionId.getVersion());
            }
        }
        if (selector instanceof LibraryComponentSelector) {
            LibraryComponentSelector libraryComponentSelector = (LibraryComponentSelector) selector;
            String libraryName = GUtil.elvis(libraryComponentSelector.getLibraryName(), "");
            return DefaultModuleVersionSelector.newSelector(DefaultModuleIdentifier.newId(libraryComponentSelector.getProjectPath(), libraryName), "undefined");
        }
        return UNKNOWN_MODULE_VERSION_SELECTOR;
    }
}
