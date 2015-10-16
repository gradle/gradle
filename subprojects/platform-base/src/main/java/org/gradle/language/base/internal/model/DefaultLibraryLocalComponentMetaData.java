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
package org.gradle.language.base.internal.model;

import com.google.common.base.Strings;
import org.apache.ivy.core.module.descriptor.ExcludeRule;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.local.model.DefaultLibraryBinaryIdentifier;
import org.gradle.internal.component.local.model.DefaultLibraryComponentSelector;
import org.gradle.internal.component.local.model.DefaultLocalComponentMetaData;
import org.gradle.internal.component.model.DependencyMetaData;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.LocalComponentDependencyMetaData;
import org.gradle.platform.base.DependencySpec;

import java.util.Collections;

public class DefaultLibraryLocalComponentMetaData extends DefaultLocalComponentMetaData {
    private static final String VERSION = "<local component>";
    private static final ExcludeRule[] EXCLUDE_RULES = new ExcludeRule[0];
    private static final String CONFIGURATION_COMPILE = "compile";

    private DefaultLibraryLocalComponentMetaData(ModuleVersionIdentifier id, ComponentIdentifier componentIdentifier) {
        super(id, componentIdentifier, Project.DEFAULT_STATUS);
    }

    public static DefaultLibraryLocalComponentMetaData newMetaData(LibraryBinaryIdentifier componentId, TaskDependency buildDependencies) {
        ModuleVersionIdentifier id = new DefaultModuleVersionIdentifier(
            componentId.getProjectPath(), componentId.getLibraryName(), VERSION
        );
        DefaultLibraryLocalComponentMetaData metaData = new DefaultLibraryLocalComponentMetaData(id, componentId);
        metaData.addConfiguration(
            DefaultLibraryBinaryIdentifier.CONFIGURATION_API,
            String.format("Request metadata: %s", componentId.getDisplayName()),
            Collections.<String>emptySet(),
            Collections.singleton(DefaultLibraryBinaryIdentifier.CONFIGURATION_API),
            true,
            true,
            buildDependencies);
        return metaData;
    }

    // TODO:DAZ: The dependencySpec should be transformed based on defaultProject (and other context) elsewhere.
    public void addDependency(DependencySpec dependency, String defaultProject) {
        String projectPath = dependency.getProjectPath();
        String libraryName = dependency.getLibraryName();
        if (projectPath == null && libraryName != null && libraryName.contains(":")) {
            String[] components = libraryName.split(":");
            ModuleVersionSelector requested = new DefaultModuleVersionSelector(components[0], components[1], components[2]);
            ModuleComponentSelector selector = DefaultModuleComponentSelector.newSelector(requested);
            // TODO:DAZ: This hard-codes the assumption of a 'compile' configuration on the external module
            // Instead, we should be creating an API configuration for each resolved module
            addDependency(createDependencyMetaData(selector, requested, CONFIGURATION_COMPILE));
        } else {
            if (Strings.isNullOrEmpty(projectPath)) {
                projectPath = defaultProject;
            }
            // currently we use "null" as variant value, because there's only one variant: API
            ComponentSelector selector = new DefaultLibraryComponentSelector(projectPath, libraryName);
            DefaultModuleVersionSelector requested = new DefaultModuleVersionSelector(Strings.nullToEmpty(projectPath), Strings.nullToEmpty(libraryName), getId().getVersion());
            DependencyMetaData localComponentDependencyMetaData = createDependencyMetaData(selector, requested, DefaultLibraryBinaryIdentifier.CONFIGURATION_API);
            addDependency(localComponentDependencyMetaData);
        }
    }

    private DependencyMetaData createDependencyMetaData(ComponentSelector selector, ModuleVersionSelector requested, String configuration) {
        return new LocalComponentDependencyMetaData(
                selector, requested, DefaultLibraryBinaryIdentifier.CONFIGURATION_API, configuration,
                Collections.<IvyArtifactName>emptySet(),
                EXCLUDE_RULES,
                false, false, true);
    }

}
