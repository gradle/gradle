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

import org.apache.ivy.core.module.descriptor.ExcludeRule;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.LibraryComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.LocalComponentFactory;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.component.local.model.DefaultLibraryComponentSelector;
import org.gradle.internal.component.local.model.DefaultLocalComponentMetaData;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.LocalComponentDependencyMetaData;
import org.gradle.language.base.internal.DependentSourceSetInternal;
import org.gradle.language.base.internal.LanguageSourceSetInternal;
import org.gradle.language.base.internal.model.DefaultLibraryLocalComponentMetaData;
import org.gradle.platform.base.DependencySpec;
import org.gradle.platform.base.DependencySpecContainer;

import java.util.Collections;

public class LanguageSourceSetLocalComponentFactory implements LocalComponentFactory {

    private static final ExcludeRule[] EXCLUDE_RULES = new ExcludeRule[0];

    @Override
    public boolean canConvert(Object source) {
        return source instanceof DefaultLanguageSourceSetResolveContext;
    }

    @Override
    public DefaultLibraryLocalComponentMetaData convert(Object source) {
        DefaultLanguageSourceSetResolveContext context = (DefaultLanguageSourceSetResolveContext) source;
        String projectPath = context.getProjectPath();
        LanguageSourceSetInternal sourceSet = context.getSourceSet();
        String libraryName = sourceSet.getParentName();
        TaskDependency buildDependencies = context.getSourceSet().getBuildDependencies();
        DefaultLibraryLocalComponentMetaData metaData = DefaultLibraryLocalComponentMetaData.newMetaData(projectPath, libraryName, buildDependencies);
        if (sourceSet instanceof DependentSourceSetInternal) {
            addDependencies(projectPath, metaData, ((DependentSourceSetInternal) sourceSet).getDependencies());
        }
        return metaData;
    }

    private void addDependencies(String defaultProject, DefaultLocalComponentMetaData metaData, DependencySpecContainer allDependencies) {
        ModuleVersionIdentifier mvi = metaData.getId();
        for (DependencySpec dependency : allDependencies.getDependencies()) {

            String projectPath = dependency.getProjectPath();
            if (projectPath==null) {
                projectPath = defaultProject;
            }
            String libraryName = dependency.getLibraryName();
            if (libraryName ==null) {
                libraryName = "";
            }
            ComponentSelector selector = new DefaultLibraryComponentSelector(projectPath, libraryName);
            DefaultModuleVersionSelector requested = new DefaultModuleVersionSelector(projectPath, libraryName, mvi.getVersion());
            LocalComponentDependencyMetaData localComponentDependencyMetaData = new LocalComponentDependencyMetaData(
                selector,
                requested,
                LibraryComponentIdentifier.API_CONFIGURATION_NAME,
                LibraryComponentIdentifier.API_CONFIGURATION_NAME,
                Collections.<IvyArtifactName>emptySet(),
                EXCLUDE_RULES,
                false,
                false,
                true);
            metaData.addDependency(localComponentDependencyMetaData);
        }
    }

}
