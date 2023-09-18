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

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ConsumableConfiguration;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal;
import org.gradle.api.internal.plugins.DefaultArtifactPublicationSet;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.JvmConstants;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.JavaResolutionConsistency;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.plugins.internal.AbstractJavaResolutionConsistency;
import org.gradle.api.plugins.internal.JavaConfigurationVariantMapping;
import org.gradle.api.plugins.internal.JavaPluginHelper;
import org.gradle.api.plugins.jvm.JvmTestSuite;
import org.gradle.api.plugins.jvm.internal.DefaultJvmFeature;
import org.gradle.api.plugins.jvm.internal.JvmFeatureInternal;
import org.gradle.api.plugins.jvm.internal.JvmPluginServices;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.internal.PublicationInternal;
import org.gradle.api.publish.internal.component.DefaultAdhocSoftwareComponent;
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal;
import org.gradle.api.publish.ivy.IvyPublication;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.plugins.PublishingPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.testing.base.TestingExtension;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Collections;

/**
 * The software component created by the Java plugin. This component owns the main {@link JvmFeatureInternal} which itself
 * is responsible for compiling and packaging the main production jar. Therefore, this component transitively owns the
 * corresponding source set and any domain objects which are created by the {@link BasePlugin} on the source set's behalf.
 * This includes the source set's resolvable configurations and dependency scopes, as well as any associated tasks.
 */
public class DefaultJvmSoftwareComponent extends DefaultAdhocSoftwareComponent implements JvmSoftwareComponentInternal {

    private static final String SOURCE_ELEMENTS_VARIANT_NAME_SUFFIX = "SourceElements";

    private final JvmFeatureInternal mainFeature;
    @Nullable private final JvmTestSuite testSuite;

    @Inject
    public DefaultJvmSoftwareComponent(
        String componentName,
        String sourceSetName,
        Project project,
        ObjectFactory objectFactory,
        ProviderFactory providerFactory,
        JvmPluginServices jvmPluginServices
    ) {
        super(componentName, objectFactory);

        RoleBasedConfigurationContainerInternal configurations = ((ProjectInternal) project).getConfigurations();
        PluginContainer plugins = project.getPlugins();
        ExtensionContainer extensions = project.getExtensions();

        JavaPluginExtension javaExtension = getJavaPluginExtension(extensions);
        SourceSet sourceSet = createSourceSet(sourceSetName, javaExtension.getSourceSets());

        this.mainFeature = new DefaultJvmFeature(
            sourceSetName, sourceSet, Collections.emptyList(),
            (ProjectInternal) project, false, false);

        // TODO: Should all features also have this variant? Why just the main feature?
        createSourceElements(configurations, providerFactory, objectFactory, mainFeature, jvmPluginServices);

        // Build the main jar when running `assemble`.
        extensions.getByType(DefaultArtifactPublicationSet.class)
            .addCandidate(mainFeature.getRuntimeElementsConfiguration().getArtifacts().iterator().next());

        configurePublishing(plugins, extensions, sourceSet);

        // Register the consumable configurations as providing variants for consumption.
        addVariantsFromConfiguration(mainFeature.getApiElementsConfiguration(), new JavaConfigurationVariantMapping("compile", false));
        addVariantsFromConfiguration(mainFeature.getRuntimeElementsConfiguration(), new JavaConfigurationVariantMapping("runtime", false));

        this.testSuite = configureBuiltInTest(project);
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

    private ConsumableConfiguration createSourceElements(RoleBasedConfigurationContainerInternal configurations, ProviderFactory providerFactory, ObjectFactory objectFactory, JvmFeatureInternal feature, JvmPluginServices jvmPluginServices) {

        // TODO: Why are we using this non-standard name? For the `java` component, this
        // equates to `mainSourceElements` instead of `sourceElements` as one would expect.
        // Can we change this name without breaking compatibility? Is the variant name part
        // of the component's API?
        String variantName = feature.getSourceSet().getName() + SOURCE_ELEMENTS_VARIANT_NAME_SUFFIX;

        ConsumableConfiguration variant = configurations.consumable(variantName).get();
        variant.setDescription("List of source directories contained in the Main SourceSet.");
        variant.setVisible(false);
        variant.extendsFrom(mainFeature.getImplementationConfiguration());

        jvmPluginServices.configureAsSources(variant);

        variant.getOutgoing().artifacts(
            feature.getSourceSet().getAllSource().getSourceDirectories().getElements().flatMap(e -> providerFactory.provider(() -> e)),
            artifact -> artifact.setType(ArtifactTypeDefinition.DIRECTORY_TYPE)
        );

        return variant;
    }

    // TODO: This approach is not necessarily correct for non-main features. All publications will attempt to use the main feature's
    // compile and runtime classpaths for version mapping, even if a non-main feature is being published.
    private static void configurePublishing(PluginContainer plugins, ExtensionContainer extensions, SourceSet sourceSet) {
        plugins.withType(PublishingPlugin.class, plugin -> {
            PublishingExtension publishing = extensions.getByType(PublishingExtension.class);

            // Set up the default configurations used when mapping to resolved versions
            publishing.getPublications().withType(IvyPublication.class, publication -> {
                VersionMappingStrategyInternal strategy = ((PublicationInternal<?>) publication).getVersionMappingStrategy();
                strategy.defaultResolutionConfiguration(Usage.JAVA_API, sourceSet.getCompileClasspathConfigurationName());
                strategy.defaultResolutionConfiguration(Usage.JAVA_RUNTIME, sourceSet.getRuntimeClasspathConfigurationName());
            });
            publishing.getPublications().withType(MavenPublication.class, publication -> {
                VersionMappingStrategyInternal strategy = ((PublicationInternal<?>) publication).getVersionMappingStrategy();
                strategy.defaultResolutionConfiguration(Usage.JAVA_API, sourceSet.getCompileClasspathConfigurationName());
                strategy.defaultResolutionConfiguration(Usage.JAVA_RUNTIME, sourceSet.getRuntimeClasspathConfigurationName());
            });
        });
    }

    // TODO: The component itself should not be concerned with configuring the sources and javadoc jars
    // of its features. It should lazily react to the variants of the feature being added and configure
    // itself to in turn advertise those variants. However, this requires a more complete variant API,
    // which is still being designed. For now, we'll add the variants manually.

    @Override
    public void withJavadocJar() {
        mainFeature.withJavadocJar();

        Configuration javadocElements = mainFeature.getJavadocElementsConfiguration();
        if (!isRegisteredAsLegacyVariant(javadocElements)) {
            addVariantsFromConfiguration(javadocElements, new JavaConfigurationVariantMapping("runtime", true));
        }
    }

    @Override
    public void withSourcesJar() {
        mainFeature.withSourcesJar();

        Configuration sourcesElements = mainFeature.getSourcesElementsConfiguration();
        if (!isRegisteredAsLegacyVariant(sourcesElements)) {
            addVariantsFromConfiguration(sourcesElements, new JavaConfigurationVariantMapping("runtime", true));
        }
    }

    @Override
    public JvmFeatureInternal getMainFeature() {
        return mainFeature;
    }

    @Nullable
    public JvmTestSuite getTestSuite() {
        return testSuite;
    }

    @Override
    public void consistentResolution(Action<? super JavaResolutionConsistency> action, Project project) {
        action.execute(new IntraComponentJavaResolutionConsistency(project.getExtensions().getByType(SourceSetContainer.class), project.getConfigurations()));
    }

    private JvmTestSuite configureBuiltInTest(Project project) {
        /*
         * At some point we may want to create a test suite per component, but for now we only want to create one for the `java`
         * component, in case multiple DefaultJvmSoftwareComponents are created.
         */
        TestingExtension testing = project.getExtensions().findByType(TestingExtension.class);
        if (null != testing && JvmConstants.JAVA_COMPONENT_NAME.equals(getName())) {
            final NamedDomainObjectProvider<JvmTestSuite> testSuite = testing.getSuites().register(JavaPluginHelper.DEFAULT_TEST_SUITE_NAME, JvmTestSuite.class, suite -> {
                final SourceSet testSourceSet = suite.getSources();
                ConfigurationContainer configurations = project.getConfigurations();

                Configuration testImplementationConfiguration = configurations.getByName(testSourceSet.getImplementationConfigurationName());
                Configuration testRuntimeOnlyConfiguration = configurations.getByName(testSourceSet.getRuntimeOnlyConfigurationName());
                Configuration testCompileClasspathConfiguration = configurations.getByName(testSourceSet.getCompileClasspathConfigurationName());
                Configuration testRuntimeClasspathConfiguration = configurations.getByName(testSourceSet.getRuntimeClasspathConfigurationName());

                // We cannot reference the main source set lazily (via a callable) since the IntelliJ model builder
                // relies on the main source set being created before the tests. So, this code here cannot live in the
                // JvmTestSuitePlugin and must live here, so that we can ensure we register this test suite after we've
                // created the main source set.
                final SourceSet mainSourceSet = getMainFeature().getSourceSet();
                final FileCollection mainSourceSetOutput = mainSourceSet.getOutput();
                final FileCollection testSourceSetOutput = testSourceSet.getOutput();
                testSourceSet.setCompileClasspath(project.getObjects().fileCollection().from(mainSourceSetOutput, testCompileClasspathConfiguration));
                testSourceSet.setRuntimeClasspath(project.getObjects().fileCollection().from(testSourceSetOutput, mainSourceSetOutput, testRuntimeClasspathConfiguration));

                testImplementationConfiguration.extendsFrom(configurations.getByName(mainSourceSet.getImplementationConfigurationName()));
                testRuntimeOnlyConfiguration.extendsFrom(configurations.getByName(mainSourceSet.getRuntimeOnlyConfigurationName()));
            });

            // Force the realization of this test suite, targets and task
            JvmTestSuite suite = testSuite.get();

            project.getTasks().named(JavaBasePlugin.CHECK_TASK_NAME, task -> task.dependsOn(testSuite));

            return suite;
        } else {
            return null;
        }
    }

    /**
     * Extends {@link org.gradle.api.plugins.internal.AbstractJavaResolutionConsistency} to function on
     * the {@link org.gradle.jvm.component.internal.JvmSoftwareComponentInternal} that contains this instance.
     */
    private class IntraComponentJavaResolutionConsistency extends AbstractJavaResolutionConsistency {
        @Inject
        public IntraComponentJavaResolutionConsistency(SourceSetContainer sourceSets, ConfigurationContainer configurations) {
            super(mainFeature.getCompileClasspathConfiguration(),
                mainFeature.getRuntimeClasspathConfiguration(),
                configurations.getByName(getTestSuite().getSources().getCompileClasspathConfigurationName()),
                configurations.getByName(getTestSuite().getSources().getRuntimeClasspathConfigurationName()),
                sourceSets, configurations);
        }
    }
}
