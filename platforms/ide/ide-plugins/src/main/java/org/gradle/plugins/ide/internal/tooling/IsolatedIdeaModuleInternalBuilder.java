/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.plugins.ide.internal.tooling;

import org.gradle.api.NonNullApi;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.plugins.ide.idea.internal.IdeaModuleInternal;
import org.gradle.plugins.ide.idea.model.Dependency;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.gradle.plugins.ide.internal.tooling.idea.IsolatedIdeaModuleInternal;
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder;

import java.util.Set;

/**
 * Builds the {@link IsolatedIdeaModuleInternal} model that contains information about a project and its tasks.
 */
@NonNullApi
public class IsolatedIdeaModuleInternalBuilder implements ParameterizedToolingModelBuilder<IdeaModelParameter> {

    @Override
    public Class<IdeaModelParameter> getParameterType() {
        return IdeaModelParameter.class;
    }

    @Override
    public boolean canBuild(String modelName) {
        return modelName.equals(IsolatedIdeaModuleInternal.class.getName());
    }

    @Override
    public IsolatedIdeaModuleInternal buildAll(String modelName, IdeaModelParameter parameter, Project project) {
        return build(project, parameter.getOfflineDependencyResolution());
    }

    @Override
    public IsolatedIdeaModuleInternal buildAll(String modelName, Project project) {
        return build(project, false);
    }

    private static IsolatedIdeaModuleInternal build(Project project, boolean offlineDependencyResolution) {
        project.getPluginManager().apply(IdeaPlugin.class);

        IdeaModel ideaModelExt = project.getExtensions().getByType(IdeaModel.class);
        IdeaModuleInternal ideaModuleExt = (IdeaModuleInternal) ideaModelExt.getModule();

        ideaModuleExt.setOffline(offlineDependencyResolution);
        Set<Dependency> resolvedDependencies = ideaModuleExt.resolveDependencies();

        IsolatedIdeaModuleInternal model = new IsolatedIdeaModuleInternal();

        model.setName(ideaModuleExt.getName());
        model.setJdkName(ideaModuleExt.getJdkName());
        model.setContentRoot(IdeaModuleBuilderSupport.buildContentRoot(ideaModuleExt));
        model.setCompilerOutput(IdeaModuleBuilderSupport.buildCompilerOutput(ideaModuleExt));

        // Simulating IdeaPlugin to only expose these values when 'java' plugin is applied
        if (project.getPlugins().hasPlugin(JavaPlugin.class)) {
            model.setExplicitSourceLanguageLevel(ideaModuleExt.getRawLanguageLevel());
            model.setExplicitTargetBytecodeVersion(ideaModuleExt.getRawTargetBytecodeVersion());
        }

        // Simulating IdeaPlugin to only expose these values when 'java-base' plugin is applied
        if (project.getPlugins().hasPlugin(JavaBasePlugin.class)) {
            JavaPluginExtension javaExt = project.getExtensions().getByType(JavaPluginExtension.class);
            model.setJavaSourceCompatibility(javaExt.getSourceCompatibility());
            model.setJavaTargetCompatibility(javaExt.getTargetCompatibility());
        }

        model.setDependencies(IdeaModuleBuilderSupport.buildDependencies(resolvedDependencies));

        return model;
    }

}
