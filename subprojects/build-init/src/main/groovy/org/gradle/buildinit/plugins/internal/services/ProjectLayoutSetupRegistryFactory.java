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
import org.gradle.api.internal.file.FileResolver;
import org.gradle.buildinit.plugins.internal.BasicProjectGenerator;
import org.gradle.buildinit.plugins.internal.BuildContentGenerator;
import org.gradle.buildinit.plugins.internal.BuildInitializer;
import org.gradle.buildinit.plugins.internal.BuildScriptBuilderFactory;
import org.gradle.buildinit.plugins.internal.CompositeProjectInitDescriptor;
import org.gradle.buildinit.plugins.internal.DefaultTemplateLibraryVersionProvider;
import org.gradle.buildinit.plugins.internal.GitIgnoreGenerator;
import org.gradle.buildinit.plugins.internal.GroovyApplicationProjectInitDescriptor;
import org.gradle.buildinit.plugins.internal.GroovyLibraryProjectInitDescriptor;
import org.gradle.buildinit.plugins.internal.JavaApplicationProjectInitDescriptor;
import org.gradle.buildinit.plugins.internal.JavaLibraryProjectInitDescriptor;
import org.gradle.buildinit.plugins.internal.KotlinApplicationProjectInitDescriptor;
import org.gradle.buildinit.plugins.internal.KotlinLibraryProjectInitDescriptor;
import org.gradle.buildinit.plugins.internal.ProjectGenerator;
import org.gradle.buildinit.plugins.internal.ProjectLayoutSetupRegistry;
import org.gradle.buildinit.plugins.internal.ResourceDirsGenerator;
import org.gradle.buildinit.plugins.internal.ScalaLibraryProjectInitDescriptor;
import org.gradle.buildinit.plugins.internal.SimpleGlobalFilesBuildSettingsDescriptor;
import org.gradle.buildinit.plugins.internal.TemplateOperationFactory;
import org.gradle.buildinit.plugins.internal.maven.PomProjectInitDescriptor;

import java.util.List;

public class ProjectLayoutSetupRegistryFactory {
    private final DocumentationRegistry documentationRegistry;
    private final MavenSettingsProvider mavenSettingsProvider;
    private final FileResolver fileResolver;

    public ProjectLayoutSetupRegistryFactory(MavenSettingsProvider mavenSettingsProvider, DocumentationRegistry documentationRegistry, FileResolver fileResolver) {
        this.mavenSettingsProvider = mavenSettingsProvider;
        this.documentationRegistry = documentationRegistry;
        this.fileResolver = fileResolver;
    }

    public ProjectLayoutSetupRegistry createProjectLayoutSetupRegistry() {
        DefaultTemplateLibraryVersionProvider libraryVersionProvider = new DefaultTemplateLibraryVersionProvider();
        TemplateOperationFactory templateOperationBuilder = new TemplateOperationFactory("/org/gradle/buildinit/tasks/templates", fileResolver, documentationRegistry);
        BuildScriptBuilderFactory scriptBuilderFactory = new BuildScriptBuilderFactory(fileResolver);
        BuildContentGenerator settingsDescriptor = new SimpleGlobalFilesBuildSettingsDescriptor(scriptBuilderFactory, documentationRegistry);
        BuildContentGenerator resourcesGenerator = new ResourceDirsGenerator(fileResolver);
        BuildContentGenerator gitIgnoreGenerator = new GitIgnoreGenerator(fileResolver);
        List<BuildContentGenerator> jvmProjectGenerators = ImmutableList.of(settingsDescriptor, gitIgnoreGenerator, resourcesGenerator);
        List<BuildContentGenerator> commonGenerators = ImmutableList.of(settingsDescriptor, gitIgnoreGenerator);
        BuildInitializer basicType = of(new BasicProjectGenerator(scriptBuilderFactory), commonGenerators);
        PomProjectInitDescriptor mavenBuildConverter = new PomProjectInitDescriptor(fileResolver, mavenSettingsProvider, scriptBuilderFactory);
        ProjectLayoutSetupRegistry registry = new ProjectLayoutSetupRegistry(basicType, mavenBuildConverter);
        registry.add(of(new JavaLibraryProjectInitDescriptor(scriptBuilderFactory, templateOperationBuilder, fileResolver, libraryVersionProvider, documentationRegistry), jvmProjectGenerators));
        registry.add(of(new JavaApplicationProjectInitDescriptor(scriptBuilderFactory, templateOperationBuilder, fileResolver, libraryVersionProvider, documentationRegistry), jvmProjectGenerators));
        registry.add(of(new GroovyApplicationProjectInitDescriptor(scriptBuilderFactory, templateOperationBuilder, fileResolver, libraryVersionProvider, documentationRegistry), jvmProjectGenerators));
        registry.add(of(new GroovyLibraryProjectInitDescriptor(scriptBuilderFactory, templateOperationBuilder, fileResolver, libraryVersionProvider, documentationRegistry), jvmProjectGenerators));
        registry.add(of(new ScalaLibraryProjectInitDescriptor(scriptBuilderFactory, templateOperationBuilder, fileResolver, libraryVersionProvider, documentationRegistry), jvmProjectGenerators));
        registry.add(of(new KotlinApplicationProjectInitDescriptor(scriptBuilderFactory, templateOperationBuilder, fileResolver, libraryVersionProvider), jvmProjectGenerators));
        registry.add(of(new KotlinLibraryProjectInitDescriptor(scriptBuilderFactory, templateOperationBuilder, fileResolver, libraryVersionProvider), jvmProjectGenerators));
        return registry;
    }

    private BuildInitializer of(ProjectGenerator projectGenerator, List<BuildContentGenerator> generators) {
        return new CompositeProjectInitDescriptor(projectGenerator, generators);
    }

}
