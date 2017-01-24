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
package org.gradle.api.plugins;

import com.google.common.collect.ImmutableMap;
import org.gradle.api.Incubating;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ConfigurationVariant;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.language.jvm.tasks.ProcessResources;

import java.io.File;

/**
 * <p>A {@link Plugin} which extends the capabilities of the {@link JavaPlugin Java plugin} by cleanly separating
 * the API and implementation dependencies of a library.</p>
 *
 * @since 3.4
 */
@Incubating
public class JavaLibraryPlugin implements Plugin<Project> {

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(JavaPlugin.class);

        JavaPluginConvention convention = (JavaPluginConvention) project.getConvention().getPlugins().get("java");
        final ConfigurationContainer configurations = project.getConfigurations();
        addApiToMainSourceSet(project, convention, configurations);
    }

    private void addApiToMainSourceSet(final Project project, JavaPluginConvention convention, final ConfigurationContainer configurations) {
        SourceSet sourceSet = convention.getSourceSets().getByName("main");
        defineApiConfigurationsForSourceSet(sourceSet, configurations);
        String apiElementsConfigurationName = sourceSet.getApiElementsConfigurationName();
        NamedDomainObjectContainer<ConfigurationVariant> variants = configurations.findByName(apiElementsConfigurationName).getOutgoing().getVariants();
        JavaCompile javaCompile = (JavaCompile) project.getTasks().findByName(sourceSet.getCompileJavaTaskName());
        ProcessResources processResources = (ProcessResources) project.getTasks().findByName(sourceSet.getProcessResourcesTaskName());
        /*
         * TODO since we don't have disambiguation for variants yet, the lexicographical order is important.
         * Classes should be the default, so we cannot have any variants whose name would be sorted before 'classes'
         */
        addVariant("classes", JavaPlugin.CLASS_DIRECTORY, variants, javaCompile, javaCompile.getDestinationDir());
        addVariant("resources", JavaPlugin.RESOURCES_DIRECTORY, variants, processResources, processResources.getDestinationDir());
    }

    private void addVariant(String variant, final String type, NamedDomainObjectContainer<ConfigurationVariant> variants, final Task builtBy, final File file) {
        ConfigurationVariant configurationVariant = variants.create(variant);
        configurationVariant.artifact(ImmutableMap.of(
            "file", file,
            "type", type,
            "builtBy", builtBy));
    }

    private void defineApiConfigurationsForSourceSet(SourceSet sourceSet, ConfigurationContainer configurations) {
        Configuration apiConfiguration = configurations.maybeCreate(sourceSet.getApiConfigurationName());
        apiConfiguration.setVisible(false);
        apiConfiguration.setDescription("API dependencies for " + sourceSet + ".");
        apiConfiguration.setCanBeResolved(false);
        apiConfiguration.setCanBeConsumed(false);

        Configuration apiElementsConfiguration = configurations.getByName(sourceSet.getApiElementsConfigurationName());
        apiElementsConfiguration.extendsFrom(apiConfiguration);

        Configuration implementationConfiguration = configurations.maybeCreate(sourceSet.getImplementationConfigurationName());
        implementationConfiguration.extendsFrom(apiConfiguration);

        Configuration compileConfiguration = configurations.findByName(sourceSet.getCompileConfigurationName());
        apiConfiguration.extendsFrom(compileConfiguration);
    }
}
