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

package org.gradle.jvm.component.internal;

import com.google.common.collect.ImmutableList;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationPublications;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.Usage;
import org.gradle.api.attributes.VerificationType;
import org.gradle.api.internal.artifacts.configurations.ConfigurationRoles;
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal;
import org.gradle.api.internal.artifacts.configurations.ConfigurationRolesForMigration;
import org.gradle.api.internal.artifacts.dsl.LazyPublishArtifact;
import org.gradle.api.internal.plugins.DefaultArtifactPublicationSet;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.plugins.internal.DefaultAdhocSoftwareComponent;
import org.gradle.api.plugins.internal.JavaConfigurationVariantMapping;
import org.gradle.api.plugins.internal.JvmPluginsHelper;
import org.gradle.api.plugins.jvm.internal.JvmPluginServices;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal;
import org.gradle.api.publish.ivy.IvyPublication;
import org.gradle.api.publish.ivy.internal.publication.IvyPublicationInternal;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.internal.publication.MavenPublicationInternal;
import org.gradle.api.publish.plugins.PublishingPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.SourceSetOutput;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.internal.reflect.Instantiator;

import javax.inject.Inject;

import static org.gradle.api.attributes.DocsType.JAVADOC;
import static org.gradle.api.attributes.DocsType.SOURCES;

/**
 * The software component created by the Java plugin. This component owns the consumable configurations which contribute to
 * this component's variants. Additionally, this component also owns its base {@link SourceSet} and transitively any domain
 * objects which are created by the {@link BasePlugin} on the source set's behalf. This includes the source set's resolvable
 * configurations and buckets, as well as any associated tasks.
 */
public class DefaultJvmSoftwareComponent extends DefaultAdhocSoftwareComponent implements JvmSoftwareComponentInternal {

    private static final String SOURCE_ELEMENTS_VARIANT_NAME_SUFFIX = "SourceElements";

    private final Project project;

    private final JvmPluginServices jvmPluginServices;

    private final SourceSet sourceSet;
    private final Configuration implementation;
    private final Configuration runtimeOnly;
    private final Configuration compileOnly;

    private final Configuration runtimeClasspath;
    private final Configuration compileClasspath;

    private final Configuration runtimeElements;
    private final Configuration apiElements;

    private final TaskProvider<JavaCompile> compileJava;
    private final TaskProvider<Jar> jar;

    @Inject
    public DefaultJvmSoftwareComponent(
        String componentName,
        String sourceSetName,
        Project project,
        JvmPluginServices jvmPluginServices,
        ObjectFactory objectFactory,
        ProviderFactory providerFactory,
        Instantiator instantiator
    ) {
        super(componentName, instantiator);
        this.project = project;

        this.jvmPluginServices = jvmPluginServices;

        TaskContainer tasks = project.getTasks();
        RoleBasedConfigurationContainerInternal configurations = ((ProjectInternal) project).getConfigurations();
        PluginContainer plugins = project.getPlugins();
        ExtensionContainer extensions = project.getExtensions();

        JavaPluginExtension javaExtension = getJavaPluginExtension(extensions);
        this.sourceSet = createSourceSet(sourceSetName, javaExtension.getSourceSets());

        this.compileJava = tasks.named(sourceSet.getCompileJavaTaskName(), JavaCompile.class);
        this.jar = registerJarTask(tasks, sourceSet);

        this.implementation = configurations.getByName(sourceSet.getImplementationConfigurationName());
        this.compileOnly = configurations.getByName(sourceSet.getCompileOnlyConfigurationName());
        this.runtimeOnly = configurations.getByName(sourceSet.getRuntimeOnlyConfigurationName());

        this.runtimeClasspath = configurations.getByName(sourceSet.getRuntimeClasspathConfigurationName());
        this.compileClasspath = configurations.getByName(sourceSet.getCompileClasspathConfigurationName());

        PublishArtifact jarArtifact = configureArchives(project, jar, tasks, extensions);
        this.runtimeElements = createRuntimeElements(configurations, sourceSet, jarArtifact);
        this.apiElements = createApiElements(configurations, sourceSet, jarArtifact);
        createSourceElements(configurations, providerFactory, objectFactory, sourceSet);

        JvmPluginsHelper.configureJavaDocTask(null, sourceSet, tasks, javaExtension);
        configurePublishing(plugins, extensions, sourceSet);

        // Register the consumable configurations as providing variants for consumption.
        addVariantsFromConfiguration(apiElements, new JavaConfigurationVariantMapping("compile", false));
        addVariantsFromConfiguration(runtimeElements, new JavaConfigurationVariantMapping("runtime", false));
    }

    private static JavaPluginExtension getJavaPluginExtension(ExtensionContainer extensions) {
        JavaPluginExtension javaExtension = extensions.findByType(JavaPluginExtension.class);
        if (javaExtension == null) {
            throw new GradleException("The java-base plugin must be applied in order to create instances of " + DefaultJvmSoftwareComponent.class.getSimpleName() + ".");
        }
        return javaExtension;
    }

    private static SourceSet createSourceSet(String name, SourceSetContainer sourceSets) {
        if (sourceSets.findByName(name) != null) {
            throw new GradleException("Cannot create multiple instances of " + DefaultJvmSoftwareComponent.class.getSimpleName() + " with source set name '" + name +"'.");
        }

        return sourceSets.create(name);
    }

    private static TaskProvider<Jar> registerJarTask(TaskContainer tasks, SourceSet sourceSet) {
        return tasks.register(sourceSet.getJarTaskName(), Jar.class, jar -> {
            jar.setDescription("Assembles a jar archive containing the main classes.");
            jar.setGroup(BasePlugin.BUILD_GROUP);
            jar.from(sourceSet.getOutput());
        });
    }

    private static PublishArtifact configureArchives(Project project, TaskProvider<Jar> jarTaskProvider, TaskContainer tasks, ExtensionContainer extensions) {
        /*
         * Unless there are other concerns, we'd prefer to run jar tasks prior to test tasks, as this might offer a small performance improvement
         * for common usage.  In practice, running test tasks tends to take longer than building a jar; especially as a project matures. If tasks
         * in downstream projects require the jar from this project, and the jar and test tasks in this project are available to be run in either order,
         * running jar first so that other projects can continue executing tasks in parallel while this project runs its tests could be an improvement.
         * However, while we want to prioritize cross-project dependencies to maximize parallelism if possible, we don't want to add an explicit
         * dependsOn() relationship between the jar task and the test task, so that any projects which need to run test tasks first will not need modification.
         */
        tasks.withType(Test.class).configureEach(test -> {
            // Attempt to avoid configuring jar task if possible, it will likely be configured anyway the by apiElements variant
            test.shouldRunAfter(tasks.withType(Jar.class));
        });

        PublishArtifact jarArtifact = new LazyPublishArtifact(jarTaskProvider, ((ProjectInternal) project).getFileResolver(), ((ProjectInternal) project).getTaskDependencyFactory());
        extensions.getByType(DefaultArtifactPublicationSet.class).addCandidate(jarArtifact);
        return jarArtifact;
    }

    private static void addJarArtifactToConfiguration(Configuration configuration, PublishArtifact jarArtifact) {
        ConfigurationPublications publications = configuration.getOutgoing();

        // Configure an implicit variant
        publications.getArtifacts().add(jarArtifact);
        publications.getAttributes().attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
    }

    private Configuration createRuntimeElements(
        RoleBasedConfigurationContainerInternal configurations,
        SourceSet sourceSet,
        PublishArtifact jarArtifact
    ) {
        Configuration runtimeElementsConfiguration = configurations.maybeCreateWithRole(
            sourceSet.getRuntimeElementsConfigurationName(), ConfigurationRoles.INTENDED_CONSUMABLE, false, false);

        runtimeElementsConfiguration.setVisible(false);
        jvmPluginServices.useDefaultTargetPlatformInference(runtimeElementsConfiguration, compileJava);
        jvmPluginServices.configureAsRuntimeElements(runtimeElementsConfiguration);
        runtimeElementsConfiguration.setDescription("Elements of runtime for main.");

        runtimeElementsConfiguration.extendsFrom(implementation, runtimeOnly);

        // Configure variants
        addJarArtifactToConfiguration(runtimeElementsConfiguration, jarArtifact);
        jvmPluginServices.configureClassesDirectoryVariant(runtimeElementsConfiguration, sourceSet);
        jvmPluginServices.configureResourcesDirectoryVariant(runtimeElementsConfiguration, sourceSet);

        return runtimeElementsConfiguration;
    }

    private Configuration createApiElements(
        RoleBasedConfigurationContainerInternal configurations,
        SourceSet sourceSet,
        PublishArtifact jarArtifact
    ) {
        Configuration apiElementsConfiguration = configurations.maybeCreateWithRole(
            sourceSet.getApiElementsConfigurationName(), ConfigurationRoles.INTENDED_CONSUMABLE, false, false);

        apiElementsConfiguration.setVisible(false);
        jvmPluginServices.useDefaultTargetPlatformInference(apiElementsConfiguration, compileJava);
        jvmPluginServices.configureAsApiElements(apiElementsConfiguration);
        apiElementsConfiguration.setDescription("API elements for main.");

        // Configure variants
        addJarArtifactToConfiguration(apiElementsConfiguration, jarArtifact);

        return apiElementsConfiguration;
    }

    private Configuration createSourceElements(RoleBasedConfigurationContainerInternal configurations, ProviderFactory providerFactory, ObjectFactory objectFactory, SourceSet sourceSet) {

        // TODO: Why are we using this non-standard name? For the `java` component, this
        // equates to `mainSourceElements` instead of `sourceElements` as one would expect.
        // Can we change this name without breaking compatibility? Is the variant name part
        // of the component's API?
        String variantName = sourceSet.getName() + SOURCE_ELEMENTS_VARIANT_NAME_SUFFIX;

        @SuppressWarnings("deprecation") Configuration variant = configurations.createWithRole(variantName, ConfigurationRolesForMigration.INTENDED_CONSUMABLE_BUCKET_TO_INTENDED_CONSUMABLE);
        variant.setDescription("List of source directories contained in the Main SourceSet.");
        variant.setVisible(false);
        variant.extendsFrom(implementation);

        variant.attributes(attributes -> {
            attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, objectFactory.named(Bundling.class, Bundling.EXTERNAL));
            attributes.attribute(Category.CATEGORY_ATTRIBUTE, objectFactory.named(Category.class, Category.VERIFICATION));
            attributes.attribute(VerificationType.VERIFICATION_TYPE_ATTRIBUTE, objectFactory.named(VerificationType.class, VerificationType.MAIN_SOURCES));
        });

        variant.getOutgoing().artifacts(
            sourceSet.getAllSource().getSourceDirectories().getElements().flatMap(e -> providerFactory.provider(() -> e)),
            artifact -> artifact.setType(ArtifactTypeDefinition.DIRECTORY_TYPE)
        );

        return variant;
    }

    private static void configurePublishing(PluginContainer plugins, ExtensionContainer extensions, SourceSet sourceSet) {
        plugins.withType(PublishingPlugin.class, plugin -> {
            PublishingExtension publishing = extensions.getByType(PublishingExtension.class);

            // Set up the default configurations used when mapping to resolved versions
            publishing.getPublications().withType(IvyPublication.class, publication -> {
                VersionMappingStrategyInternal strategy = ((IvyPublicationInternal) publication).getVersionMappingStrategy();
                strategy.defaultResolutionConfiguration(Usage.JAVA_API, sourceSet.getCompileClasspathConfigurationName());
                strategy.defaultResolutionConfiguration(Usage.JAVA_RUNTIME, sourceSet.getRuntimeClasspathConfigurationName());
            });
            publishing.getPublications().withType(MavenPublication.class, publication -> {
                VersionMappingStrategyInternal strategy = ((MavenPublicationInternal) publication).getVersionMappingStrategy();
                strategy.defaultResolutionConfiguration(Usage.JAVA_API, sourceSet.getCompileClasspathConfigurationName());
                strategy.defaultResolutionConfiguration(Usage.JAVA_RUNTIME, sourceSet.getRuntimeClasspathConfigurationName());
            });
        });
    }

    @Override
    public void enableJavadocJarVariant() {
        if (project.getConfigurations().findByName(sourceSet.getJavadocElementsConfigurationName()) != null) {
            return;
        }
        Configuration javadocVariant = JvmPluginsHelper.createDocumentationVariantWithArtifact(
            sourceSet.getJavadocElementsConfigurationName(),
            null,
            JAVADOC,
            ImmutableList.of(),
            sourceSet.getJavadocJarTaskName(),
            project.getTasks().named(sourceSet.getJavadocTaskName()),
            (ProjectInternal) project
        );
        addVariantsFromConfiguration(javadocVariant, new JavaConfigurationVariantMapping("runtime", true));
    }

    @Override
    public void enableSourcesJarVariant() {
        if (project.getConfigurations().findByName(sourceSet.getSourcesElementsConfigurationName()) != null) {
            return;
        }
        Configuration sourcesVariant = JvmPluginsHelper.createDocumentationVariantWithArtifact(
            sourceSet.getSourcesElementsConfigurationName(),
            null,
            SOURCES,
            ImmutableList.of(),
            sourceSet.getSourcesJarTaskName(),
            sourceSet.getAllSource(),
            (ProjectInternal) project
        );
        addVariantsFromConfiguration(sourcesVariant, new JavaConfigurationVariantMapping("runtime", true));
    }

    @Override
    public TaskProvider<Jar> getMainJarTask() {
        return jar;
    }

    @Override
    public TaskProvider<JavaCompile> getMainCompileJavaTask() {
        return compileJava;
    }

    @Override
    public SourceSetOutput getMainOutput() {
        return sourceSet.getOutput();
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
    public Configuration getRuntimeClasspathConfiguration() {
        return runtimeClasspath;
    }

    @Override
    public Configuration getCompileClasspathConfiguration() {
        return compileClasspath;
    }

    @Override
    public Configuration getRuntimeElementsConfiguration() {
        return runtimeElements;
    }

    @Override
    public Configuration getApiElementsConfiguration() {
        return apiElements;
    }
}
