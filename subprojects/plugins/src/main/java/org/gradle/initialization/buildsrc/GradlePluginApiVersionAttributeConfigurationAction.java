/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.initialization.buildsrc;

import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.attributes.plugin.GradlePluginApiVersion;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.util.GradleVersion;

/**
 * Sets the attribute {@link GradlePluginApiVersion} to the current Gradle version on the project's
 * compile classpath and runtime classpath configurations as soon as the java-base plugin is applied,
 * so that those configurations resolve Gradle multi-variant plugin dependencies for the current API version.
 */
public class GradlePluginApiVersionAttributeConfigurationAction implements BuildSrcProjectConfigurationAction {

    @Override
    public void execute(ProjectInternal project) {
        project.getPlugins().withType(JavaBasePlugin.class, javaBasePlugin -> addGradlePluginApiVersionAttributeToClasspath(project));
    }

    private void addGradlePluginApiVersionAttributeToClasspath(ProjectInternal project) {
        NamedObjectInstantiator instantiator = project.getServices().get(NamedObjectInstantiator.class);
        ConfigurationContainer configurations = project.getConfigurations();

        project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets().all(sourceSet ->
            setAttributeForSourceSet(sourceSet, configurations, instantiator)
        );
    }

    private void setAttributeForSourceSet(SourceSet sourceSet, ConfigurationContainer configurations, NamedObjectInstantiator instantiator) {
        setAttributeForConfiguration(configurations.named(sourceSet.getCompileClasspathConfigurationName()), instantiator);
        setAttributeForConfiguration(configurations.named(sourceSet.getRuntimeClasspathConfigurationName()), instantiator);
    }

    private static void setAttributeForConfiguration(NamedDomainObjectProvider<Configuration> configurationProvider, NamedObjectInstantiator instantiator) {
        configurationProvider.configure(configuration ->
            configuration.getAttributes().attribute(
                GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE,
                instantiator.named(GradlePluginApiVersion.class, GradleVersion.current().getVersion())
            )
        );
    }
}
