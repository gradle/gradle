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
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ConfigurationVariant;
import org.gradle.api.attributes.Usage;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.language.jvm.tasks.ProcessResources;

import java.io.File;

/**
 * <p>A {@link Plugin} which extends the capabilities of the {@link JavaPlugin Java plugin} by cleanly separating
 * the API and implementation dependencies of a library.</p>
 */
public class JavaLibraryPlugin implements Plugin<Project> {

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(JavaPlugin.class);

        JavaPluginConvention convention = (JavaPluginConvention) project.getConvention().getPlugins().get("java");
        final ConfigurationContainer configurations = project.getConfigurations();
        addApiToMainSourceSet(project, convention, configurations);
    }

    private void addApiToMainSourceSet(final Project project, JavaPluginConvention convention, final ConfigurationContainer configurations) {
        convention.getSourceSets().getByName("main", new Action<SourceSet>() {
            @Override
            public void execute(final SourceSet sourceSet) {
                defineApiConfigurationsForSourceSet(sourceSet, configurations);
                String apiElementsConfigurationName = sourceSet.getApiElementsConfigurationName();
                NamedDomainObjectContainer<ConfigurationVariant> variants = configurations.findByName(apiElementsConfigurationName).getOutgoing().getVariants();
                JavaCompile javaCompile = (JavaCompile) project.getTasks().findByName(sourceSet.getCompileJavaTaskName());
                ProcessResources processResources = (ProcessResources) project.getTasks().findByName(sourceSet.getProcessResourcesTaskName());
                // todo: we define 2 variants, but 99% of usages will be on the classes variant. The resources one is there in case a user would
                // like to process the resources of the API dependencies, which is still unlikely to happen.
                // however, you will notice that the following code doesn't define which variant should win in case we don't tell.
                // The current behavior of Gradle is to silently select the first matching variant, which happens to be the first in order,
                // where order is the container order. In this case, it's a lexicographical sort, so classes come first.
                // We shouldn't rely on such an order, but instead define what is the default variant.
                addVariant("classes", JavaPlugin.CLASS_DIRECTORY, variants, sourceSet, javaCompile, javaCompile.getDestinationDir());
                addVariant("resources", JavaPlugin.RESOURCES_DIRECTORY, variants, sourceSet, processResources, processResources.getDestinationDir());
            }

            private void addVariant(String variant, final String type, NamedDomainObjectContainer<ConfigurationVariant> variants, final SourceSet sourceSet, final Task builtBy, final File file) {
                variants.create(variant, new Action<ConfigurationVariant>() {
                    @Override
                    public void execute(ConfigurationVariant configurationVariant) {
                        configurationVariant.artifact(ImmutableMap.of(
                            "file", file,
                            "type", type,
                            "builtBy", builtBy));
                    }
                });
            }
        });
    }

    private void defineApiConfigurationsForSourceSet(SourceSet sourceSet, ConfigurationContainer configurations) {
        Configuration apiConfiguration = configurations.maybeCreate(sourceSet.getApiConfigurationName());
        apiConfiguration.setVisible(false);
        apiConfiguration.setDescription("API dependencies for " + sourceSet + ".");
        apiConfiguration.setCanBeResolved(false);
        apiConfiguration.setCanBeConsumed(false);

        Configuration apiElementsConfiguration = configurations.maybeCreate(sourceSet.getApiElementsConfigurationName());
        apiElementsConfiguration.setVisible(false);
        apiElementsConfiguration.setDescription("API compile classpath for " + sourceSet + ".");
        apiElementsConfiguration.setCanBeResolved(false);
        apiElementsConfiguration.setCanBeConsumed(true);
        apiElementsConfiguration.setTransitive(false);
        apiElementsConfiguration.attribute(Usage.USAGE_ATTRIBUTE, Usage.FOR_COMPILE);
        apiElementsConfiguration.extendsFrom(apiConfiguration);

        Configuration implementationConfiguration = configurations.maybeCreate(sourceSet.getImplementationConfigurationName());
        implementationConfiguration.extendsFrom(apiConfiguration);

        Configuration compileConfiguration = configurations.findByName(sourceSet.getCompileConfigurationName());
        apiConfiguration.extendsFrom(compileConfiguration);
    }
}
