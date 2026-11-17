/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.buildinit.plugins.internal.services;

import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.artifacts.mvnsettings.MavenSettingsProvider;
import org.gradle.buildinit.plugins.internal.BasicBuildGenerator;
import org.gradle.buildinit.plugins.internal.BuildContentGenerator;
import org.gradle.buildinit.plugins.internal.BuildGenerator;
import org.gradle.buildinit.plugins.internal.BuildInitializer;
import org.gradle.buildinit.plugins.internal.BuildScriptBuilderFactory;
import org.gradle.buildinit.plugins.internal.DefaultTemplateLibraryVersionProvider;
import org.gradle.buildinit.plugins.internal.GitAttributesGenerator;
import org.gradle.buildinit.plugins.internal.GitIgnoreGenerator;
import org.gradle.buildinit.plugins.internal.GradlePropertiesGenerator;
import org.gradle.buildinit.plugins.internal.LanguageSpecificAdaptor;
import org.gradle.buildinit.plugins.internal.LanguageSpecificProjectGenerator;
import org.gradle.buildinit.plugins.internal.ProjectGenerator;
import org.gradle.buildinit.plugins.internal.ProjectLayoutSetupRegistry;
import org.gradle.buildinit.plugins.internal.ProjectTypeContributor;
import org.gradle.buildinit.plugins.internal.ResourceDirsGenerator;
import org.gradle.buildinit.plugins.internal.SimpleGlobalFilesBuildSettingsDescriptor;
import org.gradle.buildinit.plugins.internal.SourceGeneratingBuildGenerator;
import org.gradle.buildinit.plugins.internal.TemplateLibraryVersionProvider;
import org.gradle.buildinit.plugins.internal.TemplateOperationFactory;
import org.gradle.unexported.buildinit.plugins.internal.maven.PomProjectInitDescriptor;
import org.gradle.workers.WorkerExecutor;

import java.util.ArrayList;
import java.util.List;

public class ProjectLayoutSetupRegistryFactory {
    private final DocumentationRegistry documentationRegistry;
    private final MavenSettingsProvider mavenSettingsProvider;
    private final BuildScriptBuilderFactory scriptBuilderFactory;
    private final TemplateOperationFactory templateOperationBuilder;
    private final WorkerExecutor workerExecutor;
    private final List<ProjectTypeContributor> projectTypeContributors;

    public ProjectLayoutSetupRegistryFactory(MavenSettingsProvider mavenSettingsProvider, DocumentationRegistry documentationRegistry, WorkerExecutor workerExecutor, List<ProjectTypeContributor> projectTypeContributors) {
        this.mavenSettingsProvider = mavenSettingsProvider;
        this.documentationRegistry = documentationRegistry;
        this.workerExecutor = workerExecutor;
        this.projectTypeContributors = projectTypeContributors;
        this.scriptBuilderFactory = new BuildScriptBuilderFactory(documentationRegistry);
        this.templateOperationBuilder = new TemplateOperationFactory("/org/gradle/buildinit/tasks/templates", documentationRegistry);
    }

    public ProjectLayoutSetupRegistry createProjectLayoutSetupRegistry() {
        DefaultTemplateLibraryVersionProvider libraryVersionProvider = new DefaultTemplateLibraryVersionProvider();
        TemplateOperationFactory templateOperationBuilder = this.templateOperationBuilder;
        BuildContentGenerator settingsDescriptor = new SimpleGlobalFilesBuildSettingsDescriptor(scriptBuilderFactory, documentationRegistry);
        BuildContentGenerator resourcesGenerator = new ResourceDirsGenerator();
        BuildContentGenerator gitIgnoreGenerator = new GitIgnoreGenerator();
        BuildContentGenerator gitAttributesGenerator = new GitAttributesGenerator();
        BuildContentGenerator gradlePropertiesGenerator = new GradlePropertiesGenerator();
        List<BuildContentGenerator> jvmProjectGenerators = ImmutableList.of(settingsDescriptor, gitIgnoreGenerator, gitAttributesGenerator, gradlePropertiesGenerator, resourcesGenerator);
        List<BuildContentGenerator> commonGenerators = ImmutableList.of(settingsDescriptor, gitIgnoreGenerator, gitAttributesGenerator, gradlePropertiesGenerator);
        BuildGenerator basicType = new BasicBuildGenerator(scriptBuilderFactory, documentationRegistry, commonGenerators);
        PomProjectInitDescriptor mavenBuildConverter = new PomProjectInitDescriptor(mavenSettingsProvider, documentationRegistry, workerExecutor);
        List<BuildInitializer> projectTypes = new ArrayList<>();
        ProjectTypeContributor.Registrar registrar = new ProjectTypeContributor.Registrar() {
            @Override
            public TemplateLibraryVersionProvider getLibraryVersionProvider() {
                return libraryVersionProvider;
            }

            @Override
            public TemplateOperationFactory getTemplateOperationFactory() {
                return templateOperationBuilder;
            }

            @Override
            public void registerJvmProjectType(LanguageSpecificProjectGenerator projectGenerator) {
                projectTypes.add(of(projectGenerator, jvmProjectGenerators, libraryVersionProvider));
            }

            @Override
            public void registerProjectType(LanguageSpecificProjectGenerator projectGenerator) {
                projectTypes.add(of(projectGenerator, commonGenerators, libraryVersionProvider));
            }
        };
        for (ProjectTypeContributor contributor : projectTypeContributors) {
            contributor.contribute(registrar);
        }
        return new ProjectLayoutSetupRegistry(basicType, mavenBuildConverter, templateOperationBuilder, projectTypes);
    }

    private BuildGenerator of(ProjectGenerator projectGenerator, List<BuildContentGenerator> generators) {
        return new SourceGeneratingBuildGenerator(projectGenerator, generators);
    }

    private BuildInitializer of(LanguageSpecificProjectGenerator projectGenerator, List<BuildContentGenerator> generators, TemplateLibraryVersionProvider libraryVersionProvider) {
        return of(new LanguageSpecificAdaptor(projectGenerator, scriptBuilderFactory, templateOperationBuilder, libraryVersionProvider), generators);
    }

}
