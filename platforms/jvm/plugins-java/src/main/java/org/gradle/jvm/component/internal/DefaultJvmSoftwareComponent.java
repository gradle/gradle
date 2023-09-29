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

import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ConsumableConfiguration;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal;
import org.gradle.api.internal.plugins.DefaultArtifactPublicationSet;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.JvmConstants;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JvmTestSuitePlugin;
import org.gradle.api.plugins.internal.JavaConfigurationVariantMapping;
import org.gradle.api.plugins.jvm.JvmTestSuite;
import org.gradle.api.plugins.jvm.internal.JvmFeatureInternal;
import org.gradle.api.plugins.jvm.internal.JvmPluginServices;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.publish.internal.component.DefaultAdhocSoftwareComponent;
import org.gradle.api.tasks.SourceSet;
import org.gradle.testing.base.TestingExtension;

import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * The software component created by the Java plugin. This component owns the main {@link JvmFeatureInternal} which itself
 * is responsible for compiling and packaging the main production jar. Therefore, this component transitively owns the
 * corresponding source set and any domain objects which are created by the {@link BasePlugin} on the source set's behalf.
 * This includes the source set's resolvable configurations and dependency scopes, as well as any associated tasks.
 */
public class DefaultJvmSoftwareComponent extends DefaultAdhocSoftwareComponent implements JvmSoftwareComponentInternal {

    private static final String SOURCE_ELEMENTS_VARIANT_NAME_SUFFIX = "SourceElements";

    private final RoleBasedConfigurationContainerInternal configurations;

    private final JvmFeatureInternal mainFeature;
    @Nullable private final JvmTestSuite testSuite;

    @Inject
    public DefaultJvmSoftwareComponent(
        String componentName,
        Project project,
        JvmFeatureInternal mainFeature
    ) {
        super(componentName, project.getObjects());

        this.mainFeature = mainFeature;
        this.configurations = ((ProjectInternal) project).getConfigurations();
        this.testSuite = configureBuiltInTest(project);

        DefaultArtifactPublicationSet artifactPublicationSet = project.getExtensions().getByType(DefaultArtifactPublicationSet.class);
        configureFeature(mainFeature, project.getProviders(), ((ProjectInternal) project).getServices().get(JvmPluginServices.class), artifactPublicationSet);
    }

    private ConsumableConfiguration createSourceElements(RoleBasedConfigurationContainerInternal configurations, ProviderFactory providerFactory, JvmFeatureInternal feature, JvmPluginServices jvmPluginServices) {

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

    // Eventually, we'll want to expand this and maybe make it public to support dynamically adding new features,
    // for now, this just isolates what is done to configure the main feature
    private void configureFeature(JvmFeatureInternal feature,
                                  ProviderFactory providerFactory,
                                  JvmPluginServices jvmPluginServices,
                                  DefaultArtifactPublicationSet artifactPublicationSet) {
        // TODO: Should all features also have this variant? Why just the main feature?
        createSourceElements(configurations, providerFactory, feature, jvmPluginServices);

        // Build the main jar when running `assemble`.
        artifactPublicationSet.addCandidate(feature.getRuntimeElementsConfiguration().getArtifacts().iterator().next());

        // Register the consumable configurations as providing variants for consumption.
        addVariantsFromConfiguration(feature.getApiElementsConfiguration(), new JavaConfigurationVariantMapping("compile", false));
        addVariantsFromConfiguration(feature.getRuntimeElementsConfiguration(), new JavaConfigurationVariantMapping("runtime", false));
    }

    @Nullable
    public JvmTestSuite getTestSuite() {
        return testSuite;
    }

    @Override
    public void useCompileClasspathConsistency() {
        if (testSuite != null) {
            configurations.getByName(testSuite.getSources().getCompileClasspathConfigurationName())
                .shouldResolveConsistentlyWith(mainFeature.getCompileClasspathConfiguration());
        }
    }

    @Override
    public void useRuntimeClasspathConsistency() {
        if (testSuite != null) {
            configurations.getByName(testSuite.getSources().getRuntimeClasspathConfigurationName())
                .shouldResolveConsistentlyWith(mainFeature.getRuntimeClasspathConfiguration());
        }
    }

    private JvmTestSuite configureBuiltInTest(Project project) {
        /*
         * At some point we may want to create a test suite per component, but for now we only want to create one for the `java`
         * component, in case multiple DefaultJvmSoftwareComponents are created.
         */
        TestingExtension testing = project.getExtensions().findByType(TestingExtension.class);
        if (null != testing && JvmConstants.JAVA_COMPONENT_NAME.equals(getName())) {
            final NamedDomainObjectProvider<JvmTestSuite> testSuite = testing.getSuites().register(JvmTestSuitePlugin.DEFAULT_TEST_SUITE_NAME, JvmTestSuite.class, suite -> {
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
}
