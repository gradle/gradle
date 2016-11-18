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
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.JavaCompile;

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
            public void execute(SourceSet sourceSet) {
                defineApiConfigurationsForSourceSet(sourceSet, configurations);
                JavaCompile javaCompile = (JavaCompile) project.getTasks().findByName(sourceSet.getCompileJavaTaskName());
                project.getArtifacts().add(sourceSet.getApiCompileConfigurationName(), ImmutableMap.of(
                    "file", javaCompile.getDestinationDir(),
                    "builtBy", javaCompile ));
            }
        });
    }

    private void defineApiConfigurationsForSourceSet(SourceSet sourceSet, ConfigurationContainer configurations) {
        Configuration apiConfiguration = configurations.maybeCreate(sourceSet.getApiConfigurationName());
        apiConfiguration.setVisible(false);
        apiConfiguration.setDescription("API dependencies for " + sourceSet + ".");
        apiConfiguration.setCanBeResolved(false);
        apiConfiguration.setCanBeConsumed(false);
        apiConfiguration.setTransitive(false);

        Configuration apiCompileConfiguration = configurations.maybeCreate(sourceSet.getApiCompileConfigurationName());
        apiCompileConfiguration.setVisible(false);
        apiCompileConfiguration.setDescription("API compile classpath for " + sourceSet + ".");
        apiCompileConfiguration.setCanBeResolved(true);
        apiCompileConfiguration.setCanBeConsumed(true);
        apiCompileConfiguration.setTransitive(false);
        apiCompileConfiguration.attribute("usage", "api");
        apiCompileConfiguration.extendsFrom(apiConfiguration);

        Configuration implementationConfiguration = configurations.maybeCreate(sourceSet.getImplementationConfigurationName());
        implementationConfiguration.setVisible(false);
        implementationConfiguration.setDescription("Implementation only dependencies for " + sourceSet + ".");
        implementationConfiguration.setCanBeConsumed(false);
        implementationConfiguration.setCanBeResolved(false);
        implementationConfiguration.extendsFrom(apiConfiguration);

        Configuration compileConfiguration = configurations.findByName(sourceSet.getCompileConfigurationName());
        compileConfiguration.extendsFrom(implementationConfiguration);

    }
}
