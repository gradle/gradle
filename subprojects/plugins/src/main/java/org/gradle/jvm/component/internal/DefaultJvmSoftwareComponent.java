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

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ConfigurationPublications;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.Usage;
import org.gradle.api.attributes.VerificationType;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.dsl.LazyPublishArtifact;
import org.gradle.api.internal.plugins.DefaultArtifactPublicationSet;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.JvmConstants;
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
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.testing.Test;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.jvm.component.JvmSoftwareComponent;

import javax.inject.Inject;

/**
 * Default implementation of {@link JvmSoftwareComponent}.
 * <p>
 * This component was written assuming it will only be instantiated once, and therefore it hard-codes the names of the
 * domain objects it creates. However, future iterations of this component should allow it to be more generic.
 */
public class DefaultJvmSoftwareComponent extends DefaultAdhocSoftwareComponent implements JvmSoftwareComponent {

    private static final String SOURCE_ELEMENTS_VARIANT_NAME = "mainSourceElements";

    private final JvmPluginServices jvmPluginServices;
    private final ObjectFactory objectFactory;
    private final ProviderFactory providerFactory;

    private final Configuration runtimeClasspath;
    private final Configuration compileClasspath;
    private final Configuration runtimeElements;
    private final SourceSet sourceSet;

    @Inject
    public DefaultJvmSoftwareComponent(
        String componentName,
        JavaPluginExtension javaExtension,
        Project project,
        JvmPluginServices jvmPluginServices,
        ObjectFactory objectFactory,
        ProviderFactory providerFactory,
        Instantiator instantiator
    ) {
        super(componentName, instantiator);

        this.jvmPluginServices = jvmPluginServices;
        this.objectFactory = objectFactory;
        this.providerFactory = providerFactory;

        TaskContainer tasks = project.getTasks();
        ConfigurationContainer configurations = project.getConfigurations();
        PluginContainer plugins = project.getPlugins();
        ExtensionContainer extensions = project.getExtensions();

        this.sourceSet = javaExtension.getSourceSets().create(SourceSet.MAIN_SOURCE_SET_NAME);
        this.runtimeClasspath = configurations.getByName(sourceSet.getRuntimeClasspathConfigurationName());
        this.compileClasspath = configurations.getByName(sourceSet.getCompileClasspathConfigurationName());

        PublishArtifact jarArtifact = configureArchives(project, tasks, extensions, sourceSet);
        JvmPluginsHelper.configureJavaDocTask(null, sourceSet, tasks, javaExtension);
        configurePublishing(plugins, extensions, sourceSet);

        this.runtimeElements = createRuntimeElements(configurations, sourceSet, jarArtifact);
        final Configuration apiElements = createApiElements(sourceSet, jarArtifact);
        final Configuration sourceElements = createSourceElements(configurations, sourceSet);

        // Register the consumable configurations as providing variants for consumption.
        addVariantsFromConfiguration(apiElements, new JavaConfigurationVariantMapping("compile", false));
        addVariantsFromConfiguration(runtimeElements, new JavaConfigurationVariantMapping("runtime", false));
    }

    private static PublishArtifact configureArchives(Project project, TaskContainer tasks, ExtensionContainer extensions, SourceSet sourceSet) {
        TaskProvider<Jar> jarTaskProvider = tasks.register(JvmConstants.JAR_TASK_NAME, Jar.class, jar -> {
            jar.setDescription("Assembles a jar archive containing the main classes.");
            jar.setGroup(BasePlugin.BUILD_GROUP);
            jar.from(sourceSet.getOutput());
        });

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

    private Configuration createRuntimeElements(ConfigurationContainer configurations, final SourceSet sourceSet, PublishArtifact jarArtifact) {
        Configuration implementationConfiguration = configurations.getByName(sourceSet.getImplementationConfigurationName());
        Configuration runtimeOnlyConfiguration = configurations.getByName(sourceSet.getRuntimeOnlyConfigurationName());

        final Configuration runtimeElementsConfiguration = jvmPluginServices.createOutgoingElements(sourceSet.getRuntimeElementsConfigurationName(),
            builder -> builder.fromSourceSet(sourceSet)
                .providesRuntime()
                .withDescription("Elements of runtime for main.")
                .extendsFrom(implementationConfiguration, runtimeOnlyConfiguration));
        ((ConfigurationInternal) runtimeElementsConfiguration).setCanBeDeclaredAgainst(false);

        // Configure variants
        addJarArtifactToConfiguration(runtimeElementsConfiguration, jarArtifact);
        jvmPluginServices.configureClassesDirectoryVariant(runtimeElementsConfiguration, sourceSet);
        jvmPluginServices.configureResourcesDirectoryVariant(runtimeElementsConfiguration, sourceSet);

        return runtimeElementsConfiguration;
    }

    private Configuration createApiElements(SourceSet sourceSet, PublishArtifact jarArtifact) {
        final Configuration apiElementsConfiguration = jvmPluginServices.createOutgoingElements(sourceSet.getApiElementsConfigurationName(),
            builder -> builder.fromSourceSet(sourceSet)
                .providesApi()
                .withDescription("API elements for main."));
        ((ConfigurationInternal) apiElementsConfiguration).setCanBeDeclaredAgainst(false);

        // Configure variants
        addJarArtifactToConfiguration(apiElementsConfiguration, jarArtifact);

        return apiElementsConfiguration;
    }

    private Configuration createSourceElements(ConfigurationContainer configurations, SourceSet sourceSet) {
        final Configuration variant = configurations.create(SOURCE_ELEMENTS_VARIANT_NAME);
        variant.setDescription("List of source directories contained in the Main SourceSet.");
        variant.setVisible(false);
        variant.setCanBeResolved(false);
        variant.setCanBeConsumed(true);
        variant.extendsFrom(configurations.getByName(sourceSet.getImplementationConfigurationName()));

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

    public Configuration getRuntimeClasspath() {
        return runtimeClasspath;
    }

    public Configuration getCompileClasspath() {
        return compileClasspath;
    }

    public Configuration getRuntimeElements() {
        return runtimeElements;
    }

    /**
     * This is only exposed so that we can link the default test suite to this component. Future updates
     * to the instantiation of the default test suite should make exposing the source set of this component
     * unnecessary.
     */
    public SourceSet getSources() {
        return sourceSet;
    }
}
