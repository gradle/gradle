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

import org.gradle.api.Action;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationPublications;
import org.gradle.api.artifacts.ConsumableConfiguration;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal;
import org.gradle.api.internal.artifacts.dsl.LazyPublishArtifact;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.DefaultSourceSet;
import org.gradle.api.internal.tasks.JvmConstants;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.internal.JvmPluginsHelper;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.util.internal.TextUtil;
import org.jspecify.annotations.Nullable;

import java.util.Set;

import static org.gradle.api.attributes.DocsType.JAVADOC;
import static org.gradle.api.attributes.DocsType.SOURCES;

/**
 * Represents a generic "Java feature", using the specified source set and its corresponding
 * configurations and compile task. This feature creates a jar task, and
 * can optionally also create consumable javadoc and sources jar variants.
 * <p>
 * A JVM feature represents a complete buildable compilation unit, plus the variants necessary to
 * consume the results of it via dependency management. They can be used to create production libraries,
 * applications, test suites, test fixtures, or any other consumable JVM feature.
 */
public class DefaultJvmFeature implements JvmFeatureInternal {

    private static final String SOURCE_ELEMENTS_VARIANT_NAME_SUFFIX = "SourceElements";

    private final String name;
    private final SourceSet sourceSet;
    private final Set<Capability> capabilities;

    // Services
    private final ProjectInternal project;
    private final JvmPluginServices jvmPluginServices;
    private final JvmLanguageUtilities jvmLanguageUtilities;

    // Tasks
    private final TaskProvider<Jar> jar;
    private final TaskProvider<JavaCompile> compileJava;

    // Dependency configurations
    private final Configuration implementation;
    private final Configuration runtimeOnly;
    private final Configuration compileOnly;

    // Configurable dependency configurations
    private @Nullable Configuration compileOnlyApi;
    private @Nullable Configuration api;

    // Resolvable configurations
    private final Configuration runtimeClasspath;
    private final Configuration compileClasspath;

    // Outgoing variants
    private final Configuration apiElements;
    private final Configuration runtimeElements;

    // Configurable outgoing variants
    private @Nullable Configuration javadocElements;
    private @Nullable Configuration sourcesElements;

    public DefaultJvmFeature(
        String name,
        Set<Capability> capabilities,
        ProjectInternal project,
        @Nullable SourceSet sourceSet
    ) {
        this.name = name;
        this.capabilities = capabilities;
        this.project = project;

        if (sourceSet == null) {
            SourceSetContainer sourceSets = project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets();
            SourceSet existingSourceSet = sourceSets.findByName(name);

            if (existingSourceSet == null) {
                this.sourceSet = sourceSets.create(name);
            } else {
                // TODO : Deprecate this branch -- features should be responsible for creating/owning their backing source sets.
                this.sourceSet = existingSourceSet;
            }
        } else {
            // TODO: Deprecate this branch -- features should be responsible for creating/owning their backing source sets.
            this.sourceSet = sourceSet;
        }

        this.jvmPluginServices = project.getServices().get(JvmPluginServices.class);
        this.jvmLanguageUtilities = project.getServices().get(JvmLanguageUtilities.class);

        RoleBasedConfigurationContainerInternal configurations = project.getConfigurations();
        TaskContainer tasks = project.getTasks();

        this.compileJava = tasks.named(this.sourceSet.getCompileJavaTaskName(), JavaCompile.class);
        this.jar = registerOrGetJarTask(this.sourceSet, tasks);

        this.implementation = getDependencyScope("Implementation", JvmConstants.IMPLEMENTATION_CONFIGURATION_NAME);
        this.compileOnly = getDependencyScope("Compile-only", JvmConstants.COMPILE_ONLY_CONFIGURATION_NAME);
        this.runtimeOnly = getDependencyScope("Runtime-only", JvmConstants.RUNTIME_ONLY_CONFIGURATION_NAME);

        this.runtimeClasspath = configurations.getByName(this.sourceSet.getRuntimeClasspathConfigurationName());
        this.compileClasspath = configurations.getByName(this.sourceSet.getCompileClasspathConfigurationName());

        PublishArtifact jarArtifact = new LazyPublishArtifact(jar, project.getFileResolver(), project.getTaskDependencyFactory());
        this.apiElements = createApiElements(jarArtifact, compileJava);
        this.runtimeElements = createRuntimeElements(jarArtifact, compileJava);

        JavaPluginExtension javaPluginExtension = project.getExtensions().findByType(JavaPluginExtension.class);
        JvmPluginsHelper.configureJavaDocTask("'" + name + "' feature", this.sourceSet, tasks, javaPluginExtension);
    }

    /**
     * Hack to allow us to create configurations for normal and "extending" features. This should go away.
     */
    private String getConfigurationName(String suffix) {
        return ((DefaultSourceSet) sourceSet).configurationNameOf(suffix);
    }

    private static void addJarArtifactToConfiguration(Configuration configuration, PublishArtifact jarArtifact) {
        ConfigurationPublications publications = configuration.getOutgoing();

        // Configure an implicit variant
        publications.getArtifacts().add(jarArtifact);
        publications.getAttributes().attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
    }

    private Configuration createApiElements(
        PublishArtifact jarArtifact,
        TaskProvider<JavaCompile> compileJava
    ) {
        return createConsumable(JvmConstants.API_ELEMENTS_CONFIGURATION_NAME, apiElements -> {
            jvmLanguageUtilities.useDefaultTargetPlatformInference(apiElements, compileJava);
            jvmPluginServices.configureAsApiElements(apiElements);
            capabilities.forEach(apiElements.getOutgoing()::capability);
            apiElements.setDescription("API elements for the '" + name + "' feature.");

            // Configure artifact sets
            addJarArtifactToConfiguration(apiElements, jarArtifact);
        });
    }

    private Configuration createRuntimeElements(
        PublishArtifact jarArtifact,
        TaskProvider<JavaCompile> compileJava
    ) {
        return createConsumable(JvmConstants.RUNTIME_ELEMENTS_CONFIGURATION_NAME, runtimeElements -> {
            jvmLanguageUtilities.useDefaultTargetPlatformInference(runtimeElements, compileJava);
            jvmPluginServices.configureAsRuntimeElements(runtimeElements);
            capabilities.forEach(runtimeElements.getOutgoing()::capability);
            runtimeElements.setDescription("Runtime elements for the '" + name + "' feature.");

            runtimeElements.extendsFrom(implementation, runtimeOnly);

            // Configure artifact sets
            addJarArtifactToConfiguration(runtimeElements, jarArtifact);
            jvmPluginServices.configureClassesDirectoryVariant(runtimeElements, sourceSet);
            jvmPluginServices.configureResourcesDirectoryVariant(runtimeElements, sourceSet);
        });
    }

    private Configuration createConsumable(String suffix, Action<? super Configuration> action) {
        String configName = getConfigurationName(suffix);

        if (project.getConfigurations().findByName(configName) != null) {
            throw new InvalidUserCodeException(
                "Cannot create feature '" + name + "' for source set '" + sourceSet.getName() + "' since configuration '" + configName + "' already exists. " +
                    "A feature may have already been created with this source set. " +
                    "A source set can only be used by one feature at a time. "
            );
        }

        Configuration conf = project.getConfigurations().consumableLocked(configName, action);
        conf.setVisible(false);
        return conf;
    }

    @Override
    public void withApi() {
        // If the Kotlin JVM plugin is applied, after it applies the Java plugin, it will create the API configuration.
        // We need to suppress the deprecation warning for creating duplicate configurations or else if the java-library
        // plugin was subsequently applied we'd get a warning that the API configuration was created twice.
        // * If Kotlin is always creating libraries then it should always apply the java-library plugin.
        // * Otherwise, if it could create an application, it should not automatically create the api configuration.
        this.api = maybeCreateDependencyScope("API", JvmConstants.API_CONFIGURATION_NAME, false);
        this.compileOnlyApi = maybeCreateDependencyScope("Compile-only API", JvmConstants.COMPILE_ONLY_API_CONFIGURATION_NAME, true);

        this.apiElements.extendsFrom(api, compileOnlyApi);
        this.implementation.extendsFrom(api);
        this.compileOnly.extendsFrom(compileOnlyApi);

        // TODO: Why do we not always do this? Why only when we have an API?
        jvmPluginServices.configureClassesDirectoryVariant(apiElements, sourceSet);
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

    @Override
    public void withSourceElements() {
        // TODO: Why are we using this non-standard name? For the `java` component, this
        // equates to `mainSourceElements` instead of `sourceElements` as one would expect.
        // Can we change this name without breaking compatibility? Is the variant name part
        // of the component's API?
        String variantName = getSourceSet().getName() + SOURCE_ELEMENTS_VARIANT_NAME_SUFFIX;

        ConsumableConfiguration variant = project.getConfigurations().consumable(variantName).get();
        variant.setDescription("List of source directories contained in the Main SourceSet.");
        variant.setVisible(false);
        variant.extendsFrom(getImplementationConfiguration());

        jvmPluginServices.configureAsSources(variant);

        variant.getOutgoing().artifacts(
            getSourceSet().getAllSource().getSourceDirectories().getElements().flatMap(e -> project.provider(() -> e)),
            artifact -> artifact.setType(ArtifactTypeDefinition.DIRECTORY_TYPE)
        );
    }

    private Configuration getDependencyScope(String kind, String suffix) {
        String configName = getConfigurationName(suffix);
        Configuration configuration = project.getConfigurations().getByName(configName);
        configuration.setDescription(kind + " dependencies for the '" + name + "' feature.");
        configuration.setVisible(false);
        return configuration;
    }

    private Configuration maybeCreateDependencyScope(String kind, String suffix, boolean warnOnDuplicate) {
        String configName = getConfigurationName(suffix);
        Configuration configuration = project.getConfigurations().maybeCreateDependencyScopeLocked(configName, warnOnDuplicate);
        configuration.setDescription(kind + " dependencies for the '" + name + "' feature.");
        configuration.setVisible(false);
        return configuration;
    }

    private TaskProvider<Jar> registerOrGetJarTask(SourceSet sourceSet, TaskContainer tasks) {
        String jarTaskName = sourceSet.getJarTaskName();
        if (!tasks.getNames().contains(jarTaskName)) {
            return tasks.register(jarTaskName, Jar.class, jar -> {
                jar.setDescription("Assembles a jar archive containing the classes of the '" + name + "' feature.");
                jar.setGroup(BasePlugin.BUILD_GROUP);
                jar.from(sourceSet.getOutput());
                if (!capabilities.isEmpty()) {
                    jar.getArchiveClassifier().set(TextUtil.camelToKebabCase(name));
                }
            });
        }
        return tasks.named(jarTaskName, Jar.class);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public ImmutableCapabilities getCapabilities() {
        return ImmutableCapabilities.of(capabilities);
    }

    @Override
    public TaskProvider<Jar> getJarTask() {
        return jar;
    }

    @Override
    public TaskProvider<JavaCompile> getCompileJavaTask() {
        return compileJava;
    }

    @Override
    public SourceSet getSourceSet() {
        return sourceSet;
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

    @Nullable
    @Override
    public Configuration getApiConfiguration() {
        return api;
    }

    @Nullable
    @Override
    public Configuration getCompileOnlyApiConfiguration() {
        return compileOnlyApi;
    }

    @Override
    public Configuration getRuntimeClasspathConfiguration() {
        return runtimeClasspath;
    }

    @Override
    public Configuration getCompileClasspathConfiguration() {
        return compileClasspath;
    }

    @Override
    public Configuration getApiElementsConfiguration() {
        return apiElements;
    }

    @Override
    public Configuration getRuntimeElementsConfiguration() {
        return runtimeElements;
    }

    @Nullable
    @Override
    public Configuration getJavadocElementsConfiguration() {
        return javadocElements;
    }

    @Nullable
    @Override
    public Configuration getSourcesElementsConfiguration() {
        return sourcesElements;
    }

}
