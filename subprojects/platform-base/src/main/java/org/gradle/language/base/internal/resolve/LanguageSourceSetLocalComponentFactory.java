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
import org.gradle.api.Project;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.LocalComponentFactory;
import org.gradle.internal.component.local.model.*;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.LocalComponentDependencyMetaData;
import org.gradle.language.base.internal.DependentSourceSetInternal;
import org.gradle.language.base.sources.BaseLanguageSourceSet;
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
    public LocalComponentMetaData convert(Object source) {
        DefaultLanguageSourceSetResolveContext context = (DefaultLanguageSourceSetResolveContext) source;
        String projectPath = context.getProject().getPath();
        BaseLanguageSourceSet sourceSet = context.getSourceSet();
        String libraryName = sourceSet.getParentName();
        String version = context.getProject().getVersion().toString();
        ModuleVersionIdentifier id = new DefaultModuleVersionIdentifier(
            projectPath, libraryName, version
        );
        ComponentIdentifier component = new DefaultLibraryComponentIdentifier(projectPath, libraryName);
        DefaultLocalComponentMetaData metaData = new DefaultLocalComponentMetaData(id, component, Project.DEFAULT_STATUS);
        metaData.addConfiguration(context.getName(), "Configuration for "+libraryName, Collections.<String>emptySet(), Collections.singleton(context.getName()), true, true);
        if (sourceSet instanceof DependentSourceSetInternal) {
            addDependencies(projectPath, id, metaData, ((DependentSourceSetInternal) sourceSet).getDependencies());
        }
        return metaData;
    }

    private void addDependencies(String defaultProject, ModuleVersionIdentifier mvi, DefaultLocalComponentMetaData metaData, DependencySpecContainer allDependencies) {
        for (DependencySpec dependency : allDependencies) {
            ComponentSelector selector;
            String projectPath = dependency.getProjectPath();
            if (projectPath==null) {
                projectPath = defaultProject;
            }
            if (dependency.getLibraryName()==null) {
                selector = new DefaultProjectComponentSelector(dependency.getProjectPath());
            } else {
                selector = new DefaultLibraryComponentSelector(projectPath, dependency.getLibraryName());
            }
            DefaultModuleVersionSelector requested = new DefaultModuleVersionSelector(projectPath, dependency.getLibraryName(), mvi.getVersion());
            LocalComponentDependencyMetaData localComponentDependencyMetaData = new LocalComponentDependencyMetaData(
                selector,
                requested,
                DefaultLibraryComponentIdentifier.libraryToConfigurationName(mvi.getGroup(), mvi.getName()),
                DefaultLibraryComponentIdentifier.libraryToConfigurationName(projectPath, dependency.getLibraryName()),
                Collections.<IvyArtifactName>emptySet(),
                EXCLUDE_RULES,
                false,
                false,
                true);
            metaData.addDependency(localComponentDependencyMetaData);
        }
    }

}
