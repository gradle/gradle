/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.plugins.jvm.internal;

import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.capabilities.CapabilitiesMetadata;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.configurations.ConfigurationRole;
import org.gradle.api.internal.artifacts.configurations.ConfigurationRoles;
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.JvmConstants;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.internal.JvmPluginsHelper;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.util.internal.TextUtil;

import java.util.List;

import static org.gradle.api.attributes.DocsType.JAVADOC;
import static org.gradle.api.attributes.DocsType.SOURCES;

/**
 * Represents a generic "java feature", using the specified source set and it's corresponding
 * configurations, compile tasks and jar tasks. This feature can optionally also create
 * javadoc and sources jars.
 *
 * This feature treats the {@code main} source set specially, in that it will extend the existing
 * feature represented by the main source set instead of creating a new one. In practice, this
 * means that whenever the main source set is used, this builder will create new configurations
 * which live adjacent to the main source set, but still compile against the main sources. However,
 * when using any other source set, the configurations of the provided source set are used.
 *
 * This can be used to create new tests, test fixtures, or any other Java feature which
 * needs to live alongside the main Java feature.
 */
public class DefaultJvmFeature implements JvmFeatureInternal {

    private final String name;
    private final SourceSet sourceSet;
    private final List<Capability> capabilities;
    private final String displayName;

    // Services
    private final ProjectInternal project;
    private final JvmPluginServices jvmPluginServices;

    // Tasks
    private final TaskProvider<Task> jar;
    private final TaskProvider<JavaCompile> compileJava;

    // Dependency configurations
    private final Configuration implementation;
    private final Configuration runtimeOnly;
    private final Configuration compileOnly;
    private final Configuration compileOnlyApi;
    private final Configuration api;

    // Outgoing variants
    private final Configuration apiElements;
    private final Configuration runtimeElements;

    // Configurable outgoing variants
    private Configuration javadocElements;
    private Configuration sourcesElements;

    public DefaultJvmFeature(
        String name,
        SourceSet sourceSet,
        List<Capability> capabilities,
        ProjectInternal project,
        String displayName,
        // The elements configurations' roles should always be consumable only, but
        // some users of this class are still migrating towards that. In 9.0, we can remove this
        // parameter and hard-code the elements configurations' roles to consumable only.
        ConfigurationRole elementsConfigurationRole
    ) {
        this.name = name;
        this.sourceSet = sourceSet;
        this.capabilities = capabilities;
        this.project = project;
        this.displayName = displayName;

        this.jvmPluginServices = project.getServices().get(JvmPluginServices.class);
        RoleBasedConfigurationContainerInternal configurations = project.getConfigurations();
        TaskContainer tasks = project.getTasks();

        this.compileJava = tasks.named(sourceSet.getCompileJavaTaskName(), JavaCompile.class);
        this.jar = registerOrGetJarTask(sourceSet, tasks, displayName);

        String apiConfigurationName;
        String implementationConfigurationName;
        String apiElementsConfigurationName;
        String runtimeElementsConfigurationName;
        String compileOnlyConfigurationName;
        String compileOnlyApiConfigurationName;
        String runtimeOnlyConfigurationName;
        if (SourceSet.isMain(sourceSet)) {
            apiConfigurationName = name + "Api";
            implementationConfigurationName = name + "Implementation";
            apiElementsConfigurationName = apiConfigurationName + "Elements";
            runtimeElementsConfigurationName = name + "RuntimeElements";
            compileOnlyConfigurationName = name + "CompileOnly";
            compileOnlyApiConfigurationName = name + "CompileOnlyApi";
            runtimeOnlyConfigurationName = name + "RuntimeOnly";
        } else {
            apiConfigurationName = sourceSet.getApiConfigurationName();
            implementationConfigurationName = sourceSet.getImplementationConfigurationName();
            apiElementsConfigurationName = sourceSet.getApiElementsConfigurationName();
            runtimeElementsConfigurationName = sourceSet.getRuntimeElementsConfigurationName();
            compileOnlyConfigurationName = sourceSet.getCompileOnlyConfigurationName();
            compileOnlyApiConfigurationName = sourceSet.getCompileOnlyApiConfigurationName();
            runtimeOnlyConfigurationName = sourceSet.getRuntimeOnlyConfigurationName();
        }

        // In the general case, the following configurations are already created
        // but if we're using the "main" source set, it means that the component we're creating shares
        // the same source set (main) but declares its dependencies in its own buckets, so we need
        // to create them
        this.implementation = bucket("Implementation", configurations, implementationConfigurationName, displayName);
        this.compileOnly = bucket("Compile-Only", configurations, compileOnlyConfigurationName, displayName);
        this.runtimeOnly = bucket("Runtime-Only", configurations, runtimeOnlyConfigurationName, displayName);

        this.api = bucket("API", configurations, apiConfigurationName, displayName);
        this.compileOnlyApi = bucket("Compile-Only API", configurations, compileOnlyApiConfigurationName, displayName);

        // TODO: compileOnly should probably also extend from compileOnlyApi
        this.implementation.extendsFrom(api);

        this.apiElements = createApiElements(configurations, apiElementsConfigurationName, jar, compileJava, elementsConfigurationRole);
        this.runtimeElements = createRuntimeElements(configurations, runtimeElementsConfigurationName, jar, compileJava, elementsConfigurationRole);

        // TODO: This behavior is weird. Specifically for the main source set, we do this thing where
        // main classpaths "extend" the new feature. What is the use case for this? Any new features would
        // get their own buckets, but share the same source set, compile task, jar task, sources jar, and javadoc jar.
        // The documentation provides an example of creating multiple features with the main source set, but the
        // use case is still confusing to consider. It seems this is some overly complex alternative to optional dependencies. See:
        // https://docs.gradle.org/current/userguide/feature_variants.html#sec:feature_variant_source_set
        if (SourceSet.isMain(sourceSet)) {
            // we need to wire the compile only and runtime only to the classpath configurations
            configurations.getByName(sourceSet.getCompileClasspathConfigurationName()).extendsFrom(implementation, compileOnly);
            configurations.getByName(sourceSet.getRuntimeClasspathConfigurationName()).extendsFrom(implementation, runtimeOnly);
            // and we also want the feature dependencies to be available on the test classpath
            configurations.getByName(JvmConstants.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME).extendsFrom(implementation, compileOnlyApi);
            configurations.getByName(JvmConstants.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME).extendsFrom(implementation, runtimeOnly);
        }

        JavaPluginExtension javaPluginExtension = project.getExtensions().findByType(JavaPluginExtension.class);
        JvmPluginsHelper.configureJavaDocTask(displayName, sourceSet, tasks, javaPluginExtension);
    }

    private Configuration createApiElements(
        RoleBasedConfigurationContainerInternal configurations,
        String apiElementsConfigurationName,
        TaskProvider<Task> jarTask,
        TaskProvider<JavaCompile> compileJava,
        ConfigurationRole elementsRole
    ) {
        Configuration apiElements = configurations.maybeCreateWithRole(apiElementsConfigurationName, elementsRole, false, false);

        apiElements.setVisible(false);
        jvmPluginServices.useDefaultTargetPlatformInference(apiElements, compileJava);
        jvmPluginServices.configureAsApiElements(apiElements);
        capabilities.forEach(apiElements.getOutgoing()::capability);
        apiElements.setDescription("API elements for " + displayName + ".");

        apiElements.extendsFrom(api, compileOnlyApi);

        // Configure variants
        apiElements.getOutgoing().artifact(jarTask);
        jvmPluginServices.configureClassesDirectoryVariant(apiElements, sourceSet);

        return apiElements;
    }

    public Configuration createRuntimeElements(
        RoleBasedConfigurationContainerInternal configurations,
        String runtimeElementsConfigurationName,
        TaskProvider<Task> jarTask,
        TaskProvider<JavaCompile> compileJava,
        ConfigurationRole elementsRole
    ) {
        Configuration runtimeElements = configurations.maybeCreateWithRole(runtimeElementsConfigurationName, elementsRole, false, false);

        runtimeElements.setVisible(false);
        jvmPluginServices.useDefaultTargetPlatformInference(runtimeElements, compileJava);
        jvmPluginServices.configureAsRuntimeElements(runtimeElements);
        capabilities.forEach(runtimeElements.getOutgoing()::capability);
        runtimeElements.setDescription("Runtime elements for " + displayName + ".");

        runtimeElements.extendsFrom(implementation, runtimeOnly);

        // Configure variants
        runtimeElements.getOutgoing().artifact(jarTask);

        return runtimeElements;
    }

    @Override
    public void withJavadocJar() {
        if (javadocElements != null) {
            return;
        }
        this.javadocElements = JvmPluginsHelper.createDocumentationVariantWithArtifact(
            sourceSet.getJavadocElementsConfigurationName(),
            SourceSet.isMain(sourceSet) ? null : name,
            JAVADOC,
            capabilities,
            sourceSet.getJavadocJarTaskName(),
            project.getTasks().named(sourceSet.getJavadocTaskName()),
            project
        );
    }

    @Override
    public void withSourcesJar() {
        if (sourcesElements != null) {
            return;
        }
        this.sourcesElements = JvmPluginsHelper.createDocumentationVariantWithArtifact(
            sourceSet.getSourcesElementsConfigurationName(),
            SourceSet.isMain(sourceSet) ? null : name,
            SOURCES,
            capabilities,
            sourceSet.getSourcesJarTaskName(),
            sourceSet.getAllSource(),
            project
        );
    }

    private static Configuration bucket(String kind, RoleBasedConfigurationContainerInternal configurations, String configName, String displayName) {
        Configuration configuration = configurations.maybeCreateWithRole(configName, ConfigurationRoles.INTENDED_BUCKET, false, false);
        configuration.setDescription(kind + " dependencies for the " + displayName + ".");
        configuration.setVisible(false);
        return configuration;
    }

    private TaskProvider<Task> registerOrGetJarTask(SourceSet sourceSet, TaskContainer tasks, String displayName) {
        String jarTaskName = sourceSet.getJarTaskName();
        if (!tasks.getNames().contains(jarTaskName)) {
            tasks.register(jarTaskName, Jar.class, jar -> {
                jar.setDescription("Assembles a jar archive containing the classes of the " + displayName + ".");
                jar.setGroup(BasePlugin.BUILD_GROUP);
                jar.from(sourceSet.getOutput());
                jar.getArchiveClassifier().set(TextUtil.camelToKebabCase(name));
            });
        }
        return tasks.named(jarTaskName);
    }

    @Override
    public CapabilitiesMetadata getCapabilities() {
        return ImmutableCapabilities.of(capabilities);
    }

    @Override
    public Configuration getImplementationConfiguration() {
        return implementation;
    }

    @Override
    public Configuration getRuntimeOnlyConfiguration() {
        return runtimeOnly;
    }

    @Override
    public Configuration getCompileOnlyConfiguration() {
        return compileOnly;
    }

    @Override
    public Configuration getCompileOnlyApiConfiguration() {
        return compileOnlyApi;
    }

    @Override
    public Configuration getApiConfiguration() {
        return api;
    }

    @Override
    public Configuration getApiElementsConfiguration() {
        return apiElements;
    }

    @Override
    public Configuration getRuntimeElementsConfiguration() {
        return runtimeElements;
    }

    @Override
    public Configuration getJavadocElementsConfiguration() {
        return javadocElements;
    }

    @Override
    public Configuration getSourcesElementsConfiguration() {
        return sourcesElements;
    }

}
