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

import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.internal.artifacts.ArtifactAttributes;
import org.gradle.api.tasks.SourceSet;

/**
 * <p>A {@link Plugin} which extends the capabilities of the {@link JavaPlugin Java plugin} by cleanly separating
 * the API and implementation dependencies of a library.</p>
 *
 * @since 3.4
 */
@Incubating
public class JavaLibraryPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaPlugin.class);

        JavaPluginConvention convention = (JavaPluginConvention) project.getConvention().getPlugins().get("java");
        ConfigurationContainer configurations = project.getConfigurations();
        addApiToMainSourceSet(convention, configurations);
    }

    private void addApiToMainSourceSet(JavaPluginConvention convention, ConfigurationContainer configurations) {
        SourceSet sourceSet = convention.getSourceSets().getByName("main");

        Configuration apiConfiguration = configurations.maybeCreate(sourceSet.getApiConfigurationName());
        apiConfiguration.setVisible(false);
        apiConfiguration.setDescription("API dependencies for " + sourceSet + ".");
        apiConfiguration.setCanBeResolved(false);
        apiConfiguration.setCanBeConsumed(false);

        Configuration apiElementsConfiguration = configurations.getByName(sourceSet.getApiElementsConfigurationName());
        apiElementsConfiguration.extendsFrom(apiConfiguration);

        // Use a magic type to move the Jar variants out of the way so that the classes variant is used instead
        apiElementsConfiguration.getOutgoing().getAttributes().attribute(ArtifactAttributes.ARTIFACT_FORMAT, JavaPlugin.NON_DEFAULT_JAR_TYPE);

        Configuration implementationConfiguration = configurations.getByName(sourceSet.getImplementationConfigurationName());
        implementationConfiguration.extendsFrom(apiConfiguration);

        Configuration compileConfiguration = configurations.getByName(sourceSet.getCompileConfigurationName());
        apiConfiguration.extendsFrom(compileConfiguration);
    }
}
