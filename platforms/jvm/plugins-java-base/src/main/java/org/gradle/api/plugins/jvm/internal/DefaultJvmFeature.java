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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ConfigurationPublications;
import org.gradle.api.artifacts.ConsumableConfiguration;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.configurations.ConfigurationRolesForMigration;
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal;
import org.gradle.api.internal.artifacts.dsl.LazyPublishArtifact;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.DefaultSourceSet;
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

import java.util.Set;

import static org.gradle.api.attributes.DocsType.JAVADOC;
import static org.gradle.api.attributes.DocsType.SOURCES;

/**
 * Represents a generic "Java feature", using the specified source set and its corresponding
 * configurations, compile task, and jar task. This feature creates a jar task and javadoc task, and
 * can optionally also create consumable javadoc and sources jar variants.
 *
 * <p>This can be used to create production libraries, applications, test suites, test fixtures,
 * or any other consumable JVM feature.</p>
 *
 * <p>This feature can conditionally be configured to instead "extend" the production code. In that case, this
 * feature creates additional dependency configurations which live adjacent to the main source set's dependency scopes,
 * which allow users to declare optional dependencies that the production code will compile and test against.
 * These extra dependencies are not published as part of the production variants, but as separate apiElements
 * and runtimeElements variants as defined by this feature. Then, users can declare a dependency on this
 * feature to get access to the optional dependencies.</p>
 *
 * <p>This "extending" functionality is fragile, in that it allows the production code to be compiled and
 * tested against dependencies which will not necessarily be present at runtime. For this reason, we are
 * planning to deprecate the "extending" functionality. For more information, see {@link #doExtendProductionCode}.</p>
 *
 * <p>For backwards compatibility reasons, when this feature is operating in the "extending" mode,
 * this feature is able to operate without the presence of the main feature, as long as the user
 * explicitly configures the project by manually creating a main and test source set themselves.
 * In that case, this feature will additionally create the jar and javadoc tasks which the main
 * source set would normally create. Additionally, this extension feature is able to create the
 * sources and javadoc variants that the main feature would also conditionally create.</p>
 */
public class DefaultJvmFeature implements JvmFeatureInternal {
    private static final String SOURCE_ELEMENTS_VARIANT_NAME_SUFFIX = "SourceElements";

    private final String name;
    private final SourceSet sourceSet;
    private final Set<Capability> capabilities;
    private final boolean extendProductionCode;

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
    private Configuration compileOnlyApi;
    private Configuration api;

    // Resolvable configurations
    private final Configuration runtimeClasspath;
    private final Configuration compileClasspath;

    // Outgoing variants
    private final Configuration apiElements;
    private final Configuration runtimeElements;

    // Configurable outgoing variants
    private Configuration javadocElements;
    private Configuration sourcesElements;

    public DefaultJvmFeature(
        String name,
        // Should features just create the sourcesets they are going to use?  How can we ensure the same sourceset isn't used
        // by multiple features (and that the same feature isn't used by multiple components)?
        SourceSet sourceSet,
        Set<Capability> capabilities,
        ProjectInternal project,
        // The elements configurations' roles should always be consumable only, but
        // some users of this class are still migrating towards that. In 9.0, we can remove this
        // parameter and hard-code the elements configurations' roles to consumable only.
        boolean useMigrationRoleForElementsConfigurations,
        boolean extendProductionCode
    ) {
        this.name = name;
        this.sourceSet = sourceSet;
        this.capabilities = capabilities;
        this.project = project;
        this.extendProductionCode = extendProductionCode;

        // TODO: Deprecate allowing user to extend main feature.
        if (extendProductionCode && !SourceSet.isMain(sourceSet)) {
            throw new GradleException("Cannot extend main feature if source set is not also main.");
        }

        this.jvmPluginServices = project.getServices().get(JvmPluginServices.class);
        this.jvmLanguageUtilities = project.getServices().get(JvmLanguageUtilities.class);

        RoleBasedConfigurationContainerInternal configurations = project.getConfigurations();
        TaskContainer tasks = project.getTasks();

        this.compileJava = tasks.named(sourceSet.getCompileJavaTaskName(), JavaCompile.class);
        this.jar = registerOrGetJarTask(sourceSet, tasks);

        // If extendProductionCode=false, the source set has already created these configurations.
        // If extendProductionCode=true, then we create new dependency scopes and later update the main and
        // test source sets to extend from them.
        this.implementation = dependencyScope("Implementation", JvmConstants.IMPLEMENTATION_CONFIGURATION_NAME, extendProductionCode, false);
        this.compileOnly = dependencyScope("Compile-only", JvmConstants.COMPILE_ONLY_CONFIGURATION_NAME, extendProductionCode, false);
        this.runtimeOnly = dependencyScope("Runtime-only", JvmConstants.RUNTIME_ONLY_CONFIGURATION_NAME, extendProductionCode, false);

        this.runtimeClasspath = configurations.getByName(sourceSet.getRuntimeClasspathConfigurationName());
        this.compileClasspath = configurations.getByName(sourceSet.getCompileClasspathConfigurationName());

        PublishArtifact jarArtifact = new LazyPublishArtifact(jar, project.getFileResolver(), project.getTaskDependencyFactory());
        this.apiElements = createApiElements(configurations, jarArtifact, compileJava, useMigrationRoleForElementsConfigurations);
        this.runtimeElements = createRuntimeElements(configurations, jarArtifact, compileJava, useMigrationRoleForElementsConfigurations);

        if (extendProductionCode) {
            doExtendProductionCode();
        }

        JavaPluginExtension javaPluginExtension = project.getExtensions().findByType(JavaPluginExtension.class);
        JvmPluginsHelper.configureJavaDocTask("'" + name + "' feature", sourceSet, tasks, javaPluginExtension);
    }

    void doExtendProductionCode() {
        // This method is one of the primary reasons that we want to deprecate the "extending" behavior. It updates
        // the main source set and test source set to "extend" this feature. That means any dependencies declared on
        // this feature's dependency configurations will be available locally, during compilation and runtime, to the main
        // production code and default test suite. However, when publishing the production code, these dependencies will
        // not be included in its consumable variants. Therefore, the main code is compiled _and tested_ against
        // dependencies which will not necessarily be available at runtime when it is consumed from other projects
        // or in its published form.
        //
        // This leads to a case where, in order for the production code to not throw NoClassDefFoundErrors during runtime,
        // it must detect the presence of the dependencies added by this feature, and then conditionally enable and disable
        // certain optional behavior. We do not want to promote this pattern.
        //
        // A much safer pattern would be to create normal features as opposed to an "extending" feature. Then, the normal
        // feature would have a project dependency on the main feature. It would provide an extra jar with any additional code,
        // and also bring along any extra dependencies that code requires. The main feature would then be able to detect the
        // presence of the feature through some {@code ServiceLoader} mechanism, as opposed to detecting the existence of
        // dependencies directly.
        //
        // This pattern is also more flexible than the "extending" pattern in that it allows features to extend arbitrary
        // features as opposed to just the main feature.

        ConfigurationContainer configurations = project.getConfigurations();
        SourceSet mainSourceSet = project.getExtensions().findByType(JavaPluginExtension.class)
            .getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);

        // Update the main feature's source set to extend our "extension" feature's dependency scopes.
        configurations.getByName(mainSourceSet.getCompileClasspathConfigurationName()).extendsFrom(implementation, compileOnly);
        configurations.getByName(mainSourceSet.getRuntimeClasspathConfigurationName()).extendsFrom(implementation, runtimeOnly);
        // Update the default test suite's source set to extend our "extension" feature's dependency scopes.
        configurations.getByName(JvmConstants.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME).extendsFrom(implementation);
        configurations.getByName(JvmConstants.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME).extendsFrom(implementation, runtimeOnly);
    }

    /**
     * Hack to allow us to create configurations for normal and "extending" features. This should go away.
     */
    private String getConfigurationName(String suffix) {
        if (extendProductionCode) {
            return name + StringUtils.capitalize(suffix);
        } else {
            return ((DefaultSourceSet) sourceSet).configurationNameOf(suffix);
        }
    }

    private static void addJarArtifactToConfiguration(Configuration configuration, PublishArtifact jarArtifact) {
        ConfigurationPublications publications = configuration.getOutgoing();

        // Configure an implicit variant
        publications.getArtifacts().add(jarArtifact);
        publications.getAttributes().attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
    }

    private Configuration createApiElements(
        RoleBasedConfigurationContainerInternal configurations,
        PublishArtifact jarArtifact,
        TaskProvider<JavaCompile> compileJava,
        boolean useMigrationRoleForElementsConfigurations
    ) {
        String configName = getConfigurationName(JvmConstants.API_ELEMENTS_CONFIGURATION_NAME);
        Configuration apiElements = maybeCreateElementsConfiguration(configName, configurations, useMigrationRoleForElementsConfigurations);

        apiElements.setVisible(false);
        jvmLanguageUtilities.useDefaultTargetPlatformInference(apiElements, compileJava);
        jvmPluginServices.configureAsApiElements(apiElements);
        capabilities.forEach(apiElements.getOutgoing()::capability);
        apiElements.setDescription("API elements for the '" + name + "' feature.");

        // Configure variants
        addJarArtifactToConfiguration(apiElements, jarArtifact);

        return apiElements;
    }

    private Configuration createRuntimeElements(
        RoleBasedConfigurationContainerInternal configurations,
        PublishArtifact jarArtifact,
        TaskProvider<JavaCompile> compileJava,
        boolean useMigrationRoleForElementsConfigurations
    ) {
        String configName = getConfigurationName(JvmConstants.RUNTIME_ELEMENTS_CONFIGURATION_NAME);
        Configuration runtimeElements = maybeCreateElementsConfiguration(configName, configurations, useMigrationRoleForElementsConfigurations);

        runtimeElements.setVisible(false);
        jvmLanguageUtilities.useDefaultTargetPlatformInference(runtimeElements, compileJava);
        jvmPluginServices.configureAsRuntimeElements(runtimeElements);
        capabilities.forEach(runtimeElements.getOutgoing()::capability);
        runtimeElements.setDescription("Runtime elements for the '" + name + "' feature.");

        runtimeElements.extendsFrom(implementation, runtimeOnly);

        // Configure variants
        addJarArtifactToConfiguration(runtimeElements, jarArtifact);
        jvmPluginServices.configureClassesDirectoryVariant(runtimeElements, sourceSet);
        jvmPluginServices.configureResourcesDirectoryVariant(runtimeElements, sourceSet);

        return runtimeElements;
    }

    private static Configuration maybeCreateElementsConfiguration(
        String name,
        RoleBasedConfigurationContainerInternal configurations,
        boolean useMigrationRoleForElementsConfigurations
    ) {
        if (useMigrationRoleForElementsConfigurations) {
            return configurations.maybeCreateMigratingUnlocked(name, ConfigurationRolesForMigration.CONSUMABLE_DEPENDENCY_SCOPE_TO_CONSUMABLE);
        } else {
            return configurations.maybeCreateConsumableUnlocked(name);
        }
    }

    @Override
    public void withApi() {
        // If the Kotlin JVM plugin is applied, after it applies the Java plugin, it will create the API configuration.
        // We need to suppress the deprecation warning for creating duplicate configurations or else if the java-library
        // plugin was subsequently applied we'd get a warning that the API configuration was created twice.
        // * If Kotlin is always creating libraries then it should always apply the java-library plugin.
        // * Otherwise, if it could create an application, it should not automatically create the api configuration.
        this.api = dependencyScope("API", JvmConstants.API_CONFIGURATION_NAME, true, false);
        this.compileOnlyApi = dependencyScope("Compile-only API", JvmConstants.COMPILE_ONLY_API_CONFIGURATION_NAME, true, true);

        this.apiElements.extendsFrom(api, compileOnlyApi);
        this.implementation.extendsFrom(api);
        this.compileOnly.extendsFrom(compileOnlyApi);

        // TODO: Why do we not always do this? Why only when we have an API?
        jvmPluginServices.configureClassesDirectoryVariant(apiElements, sourceSet);

        if (extendProductionCode) {
            project.getConfigurations().getByName(JvmConstants.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME).extendsFrom(compileOnlyApi);
        }
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

    private Configuration dependencyScope(String kind, String suffix, boolean create, boolean warnOnDuplicate) {
        String configName = getConfigurationName(suffix);
        Configuration configuration = create
            ? project.getConfigurations().maybeCreateDependencyScopeUnlocked(configName, warnOnDuplicate)
            : project.getConfigurations().getByName(configName);
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

    @Override
    public Configuration getApiConfiguration() {
        return api;
    }

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

    @Override
    public Configuration getJavadocElementsConfiguration() {
        return javadocElements;
    }

    @Override
    public Configuration getSourcesElementsConfiguration() {
        return sourcesElements;
    }

}
