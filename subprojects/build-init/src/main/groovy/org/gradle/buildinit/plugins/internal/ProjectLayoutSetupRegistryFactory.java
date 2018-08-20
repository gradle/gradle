/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.buildinit.plugins.internal;

import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.artifacts.mvnsettings.MavenSettingsProvider;
import org.gradle.api.internal.file.FileResolver;

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
        PomProjectInitDescriptor mavenBuildConverter = new PomProjectInitDescriptor(fileResolver, mavenSettingsProvider);
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
