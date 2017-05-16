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
import org.gradle.api.artifacts.ConfigurationPublications;
import org.gradle.api.artifacts.ConfigurationVariant;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Usage;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.JavaCompile;

import javax.inject.Inject;
import java.io.File;

import static org.gradle.api.plugins.JavaPlugin.COMPILE_JAVA_TASK_NAME;

/**
 * <p>A {@link Plugin} which extends the capabilities of the {@link JavaPlugin Java plugin} by cleanly separating
 * the API and implementation dependencies of a library.</p>
 *
 * @since 3.4
 */
@Incubating
public class JavaLibraryPlugin implements Plugin<Project> {
    private final ObjectFactory objectFactory;

    @Inject
    public JavaLibraryPlugin(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory;
    }

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaPlugin.class);

        JavaPluginConvention convention = (JavaPluginConvention) project.getConvention().getPlugins().get("java");
        ConfigurationContainer configurations = project.getConfigurations();
        addApiToMainSourceSet(project, convention, configurations);
    }

    private void addApiToMainSourceSet(Project project, JavaPluginConvention convention, ConfigurationContainer configurations) {
        SourceSet sourceSet = convention.getSourceSets().getByName("main");

        Configuration apiConfiguration = configurations.maybeCreate(sourceSet.getApiConfigurationName());
        apiConfiguration.setVisible(false);
        apiConfiguration.setDescription("API dependencies for " + sourceSet + ".");
        apiConfiguration.setCanBeResolved(false);
        apiConfiguration.setCanBeConsumed(false);

        Configuration apiElementsConfiguration = configurations.getByName(sourceSet.getApiElementsConfigurationName());
        apiElementsConfiguration.extendsFrom(apiConfiguration);

        final JavaCompile javaCompile = (JavaCompile) project.getTasks().getByPath(COMPILE_JAVA_TASK_NAME);

        // Define a classes variant to use for compilation
        ConfigurationPublications publications = apiElementsConfiguration.getOutgoing();
        ConfigurationVariant variant = publications.getVariants().create("classes");
        variant.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.JAVA_API_CLASSES));
        variant.artifact(new JavaPlugin.IntermediateJavaArtifact(ArtifactTypeDefinition.JVM_CLASS_DIRECTORY, javaCompile) {
            @Override
            public File getFile() {
                return javaCompile.getDestinationDir();
            }
        });

        Configuration implementationConfiguration = configurations.getByName(sourceSet.getImplementationConfigurationName());
        implementationConfiguration.extendsFrom(apiConfiguration);

        Configuration compileConfiguration = configurations.getByName(sourceSet.getCompileConfigurationName());
        apiConfiguration.extendsFrom(compileConfiguration);
    }
}
