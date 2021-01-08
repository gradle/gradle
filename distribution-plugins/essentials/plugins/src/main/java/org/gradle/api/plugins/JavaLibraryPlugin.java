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

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.plugins.internal.JvmPluginsHelper;
import org.gradle.api.plugins.jvm.internal.JvmEcosystemUtilities;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.internal.deprecation.DeprecatableConfiguration;

import javax.inject.Inject;

import static org.gradle.api.plugins.JavaPlugin.COMPILE_ONLY_API_CONFIGURATION_NAME;
import static org.gradle.api.plugins.JavaPlugin.TEST_COMPILE_ONLY_CONFIGURATION_NAME;

/**
 * <p>A {@link Plugin} which extends the capabilities of the {@link JavaPlugin Java plugin} by cleanly separating
 * the API and implementation dependencies of a library.</p>
 *
 * @since 3.4
 * @see <a href="https://docs.gradle.org/current/userguide/java_library_plugin.html">Java Library plugin reference</a>
 */
public class JavaLibraryPlugin implements Plugin<Project> {

    private final JvmEcosystemUtilities jvmEcosystemUtilities;

    @Inject
    public JavaLibraryPlugin(JvmEcosystemUtilities jvmEcosystemUtilities) {
        this.jvmEcosystemUtilities = jvmEcosystemUtilities;
    }

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaPlugin.class);

        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        ConfigurationContainer configurations = project.getConfigurations();
        SourceSet sourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        JvmPluginsHelper.addApiToSourceSet(sourceSet, configurations);
        makeCompileOnlyApiVisibleToTests(configurations);
        jvmEcosystemUtilities.configureClassesDirectoryVariant(sourceSet.getApiElementsConfigurationName(), sourceSet);
        deprecateConfigurationsForDeclaration(sourceSets, configurations);
    }

    private void makeCompileOnlyApiVisibleToTests(ConfigurationContainer configurations) {
        Configuration testCompileOnly = configurations.getByName(TEST_COMPILE_ONLY_CONFIGURATION_NAME);
        Configuration compileOnlyApi = configurations.getByName(COMPILE_ONLY_API_CONFIGURATION_NAME);
        testCompileOnly.extendsFrom(compileOnlyApi);
    }

    private void deprecateConfigurationsForDeclaration(SourceSetContainer sourceSets, ConfigurationContainer configurations) {
        SourceSet sourceSet = sourceSets.getByName("main");

        DeprecatableConfiguration apiElementsConfiguration = (DeprecatableConfiguration) configurations.getByName(sourceSet.getApiElementsConfigurationName());
        DeprecatableConfiguration runtimeElementsConfiguration = (DeprecatableConfiguration) configurations.getByName(sourceSet.getRuntimeElementsConfigurationName());
        DeprecatableConfiguration compileClasspathConfiguration = (DeprecatableConfiguration) configurations.getByName(sourceSet.getCompileClasspathConfigurationName());
        DeprecatableConfiguration runtimeClasspathConfiguration = (DeprecatableConfiguration) configurations.getByName(sourceSet.getRuntimeClasspathConfigurationName());

        String implementationConfigurationName = sourceSet.getImplementationConfigurationName();
        String compileOnlyConfigurationName = sourceSet.getCompileOnlyConfigurationName();
        String runtimeOnlyConfigurationName = sourceSet.getRuntimeOnlyConfigurationName();
        String apiConfigurationName = sourceSet.getApiConfigurationName();

        apiElementsConfiguration.deprecateForDeclaration(implementationConfigurationName, apiConfigurationName, compileOnlyConfigurationName);
        runtimeElementsConfiguration.deprecateForDeclaration(implementationConfigurationName, apiConfigurationName, compileOnlyConfigurationName, runtimeOnlyConfigurationName);

        compileClasspathConfiguration.deprecateForDeclaration(implementationConfigurationName, apiConfigurationName, compileOnlyConfigurationName);
        runtimeClasspathConfiguration.deprecateForDeclaration(implementationConfigurationName, apiConfigurationName, compileOnlyConfigurationName, runtimeOnlyConfigurationName);
    }
}
