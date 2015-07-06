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
package org.gradle.language.base.internal.resolve;

import com.google.common.base.Strings;
import org.apache.ivy.core.module.descriptor.ExcludeRule;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.LocalComponentConverter;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.component.local.model.DefaultLibraryBinaryIdentifier;
import org.gradle.internal.component.local.model.DefaultLibraryComponentSelector;
import org.gradle.internal.component.local.model.DefaultLocalComponentMetaData;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.LocalComponentDependencyMetaData;
import org.gradle.language.base.internal.DependentSourceSetInternal;
import org.gradle.language.base.internal.model.DefaultLibraryLocalComponentMetaData;
import org.gradle.platform.base.DependencySpec;
import org.gradle.platform.base.DependencySpecContainer;

import java.util.Collections;

public class DependentSourceSetLocalComponentConverter implements LocalComponentConverter {

    private static final ExcludeRule[] EXCLUDE_RULES = new ExcludeRule[0];

    @Override
    public boolean canConvert(Object source) {
        return source instanceof DependentSourceSetResolveContext;
    }

    @Override
    public DefaultLibraryLocalComponentMetaData convert(Object source) {
        DependentSourceSetResolveContext context = (DependentSourceSetResolveContext) source;
        LibraryBinaryIdentifier libraryBinaryIdentifier = context.getComponentId();
        DependentSourceSetInternal sourceSet = context.getSourceSet();
        TaskDependency buildDependencies = context.getSourceSet().getBuildDependencies();
        DefaultLibraryLocalComponentMetaData metaData = DefaultLibraryLocalComponentMetaData.newMetaData(libraryBinaryIdentifier, buildDependencies);
        addDependencies(libraryBinaryIdentifier.getProjectPath(), metaData, sourceSet.getDependencies());
        return metaData;
    }

    private void addDependencies(String defaultProject, DefaultLocalComponentMetaData metaData, DependencySpecContainer allDependencies) {
        ModuleVersionIdentifier mvi = metaData.getId();
        for (DependencySpec dependency : allDependencies.getDependencies()) {

            String projectPath = dependency.getProjectPath();
            if (Strings.isNullOrEmpty(projectPath)) {
                projectPath = defaultProject;
            }
            String libraryName = dependency.getLibraryName();
            // currently we use "null" as variant value, because there's only one variant: API
            ComponentSelector selector = new DefaultLibraryComponentSelector(projectPath, libraryName);
            DefaultModuleVersionSelector requested = new DefaultModuleVersionSelector(
                Strings.nullToEmpty(projectPath), Strings.nullToEmpty(libraryName), mvi.getVersion());
            LocalComponentDependencyMetaData localComponentDependencyMetaData = new LocalComponentDependencyMetaData(
                selector,
                requested,
                DefaultLibraryBinaryIdentifier.CONFIGURATION_NAME,
                DefaultLibraryBinaryIdentifier.CONFIGURATION_NAME,
                Collections.<IvyArtifactName>emptySet(),
                EXCLUDE_RULES,
                false,
                false,
                true);
            metaData.addDependency(localComponentDependencyMetaData);
        }
    }

}
