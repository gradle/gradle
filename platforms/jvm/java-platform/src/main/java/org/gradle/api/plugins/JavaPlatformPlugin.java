/*
 * Copyright 2018 the original author or authors.
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

import com.google.common.collect.Sets;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.component.SoftwareComponentFactory;
import org.gradle.api.internal.artifacts.configurations.ConfigurationRolesForMigration;
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal;
import org.gradle.api.internal.java.DefaultJavaPlatformExtension;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.internal.JavaConfigurationVariantMapping;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.internal.PublicationInternal;
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal;
import org.gradle.api.publish.ivy.IvyPublication;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.plugins.PublishingPlugin;
import org.gradle.internal.component.external.model.ProjectDerivedCapability;
import org.gradle.internal.component.external.model.ShadowedImmutableCapability;

import javax.inject.Inject;
import java.util.Set;

/**
 * The Java platform plugin allows building platform components
 * for Java, which are usually published as BOM files (for Maven)
 * or Gradle platforms (Gradle metadata).
 *
 * @since 5.2
 * @see <a href="https://docs.gradle.org/current/userguide/java_platform_plugin.html">Java Platform plugin reference</a>
 */
public abstract class JavaPlatformPlugin implements Plugin<Project> {
    // Dependency scopes
    public static final String API_CONFIGURATION_NAME = "api";
    public static final String RUNTIME_CONFIGURATION_NAME = "runtime";

    // Consumable configurations
    public static final String API_ELEMENTS_CONFIGURATION_NAME = "apiElements";
    public static final String RUNTIME_ELEMENTS_CONFIGURATION_NAME = "runtimeElements";
    public static final String ENFORCED_API_ELEMENTS_CONFIGURATION_NAME = "enforcedApiElements";
    public static final String ENFORCED_RUNTIME_ELEMENTS_CONFIGURATION_NAME = "enforcedRuntimeElements";

    // Resolvable configurations
    public static final String CLASSPATH_CONFIGURATION_NAME = "classpath";

    private static final String DISALLOW_DEPENDENCIES = "Adding dependencies to platforms is not allowed by default.\n" +
        "Most likely you want to add constraints instead.\n" +
        "If you did this intentionally, you need to configure the platform extension to allow dependencies:\n    javaPlatform.allowDependencies()\n" +
        "Found dependencies in the '%s' configuration.";

    private final SoftwareComponentFactory softwareComponentFactory;

    @Inject
    public JavaPlatformPlugin(SoftwareComponentFactory softwareComponentFactory) {
        this.softwareComponentFactory = softwareComponentFactory;
    }

    @Override
    public void apply(Project project) {
        if (project.getPluginManager().hasPlugin("java")) {
            // This already throws when creating `apiElements` so be eager to have a clear error message
            throw new IllegalStateException(
                "The \"java-platform\" plugin cannot be applied together with the \"java\" (or \"java-library\") plugin. " +
                    "A project is either a platform or a library but cannot be both at the same time."
            );
        }
        project.getPluginManager().apply(BasePlugin.class);
        project.getPluginManager().apply(JvmEcosystemPlugin.class);
        createConfigurations((ProjectInternal) project);
        configureExtension(project);
        configurePublishing(project);
    }

    private void createSoftwareComponent(Project project, Configuration apiElements, Configuration runtimeElements) {
        AdhocComponentWithVariants component = softwareComponentFactory.adhoc("javaPlatform");
        project.getComponents().add(component);
        component.addVariantsFromConfiguration(apiElements, new JavaConfigurationVariantMapping("compile", false, null));
        component.addVariantsFromConfiguration(runtimeElements, new JavaConfigurationVariantMapping("runtime", false, null));
    }

    private void createConfigurations(ProjectInternal project) {
        RoleBasedConfigurationContainerInternal configurations = project.getConfigurations();
        Capability enforcedCapability = new ShadowedImmutableCapability(new ProjectDerivedCapability(project), "-derived-enforced-platform");

        Configuration api = configurations.dependencyScopeUnlocked(API_CONFIGURATION_NAME);
        Configuration apiElements = createConsumableApi(project, api, API_ELEMENTS_CONFIGURATION_NAME, Category.REGULAR_PLATFORM);
        Configuration enforcedApiElements = createConsumableApi(project, api, ENFORCED_API_ELEMENTS_CONFIGURATION_NAME, Category.ENFORCED_PLATFORM);
        enforcedApiElements.getOutgoing().capability(enforcedCapability);

        Configuration runtime = project.getConfigurations().dependencyScopeUnlocked(RUNTIME_CONFIGURATION_NAME);
        runtime.extendsFrom(api);

        Configuration runtimeElements = createConsumableRuntime(project, runtime, RUNTIME_ELEMENTS_CONFIGURATION_NAME, Category.REGULAR_PLATFORM);
        Configuration enforcedRuntimeElements = createConsumableRuntime(project, runtime, ENFORCED_RUNTIME_ELEMENTS_CONFIGURATION_NAME, Category.ENFORCED_PLATFORM);
        enforcedRuntimeElements.getOutgoing().capability(enforcedCapability);

        Configuration classpath = configurations.migratingUnlocked(CLASSPATH_CONFIGURATION_NAME, ConfigurationRolesForMigration.RESOLVABLE_DEPENDENCY_SCOPE_TO_RESOLVABLE);
        classpath.extendsFrom(runtimeElements);
        declareConfigurationUsage(project.getObjects(), classpath, Usage.JAVA_RUNTIME, LibraryElements.JAR);

        createSoftwareComponent(project, apiElements, runtimeElements);
    }

    private Configuration createConsumableRuntime(ProjectInternal project, Configuration apiElements, String name, String platformKind) {
        Configuration runtimeElements = project.getConfigurations().migratingUnlocked(name, ConfigurationRolesForMigration.CONSUMABLE_DEPENDENCY_SCOPE_TO_CONSUMABLE);
        runtimeElements.setVisible(false);
        runtimeElements.extendsFrom(apiElements);
        declareConfigurationUsage(project.getObjects(), runtimeElements, Usage.JAVA_RUNTIME);
        declareConfigurationCategory(project.getObjects(), runtimeElements, platformKind);
        return runtimeElements;
    }

    private Configuration createConsumableApi(ProjectInternal project, Configuration api, String name, String platformKind) {
        Configuration apiElements = project.getConfigurations().migratingUnlocked(name, ConfigurationRolesForMigration.CONSUMABLE_DEPENDENCY_SCOPE_TO_CONSUMABLE);
        apiElements.setVisible(false);
        apiElements.extendsFrom(api);
        declareConfigurationUsage(project.getObjects(), apiElements, Usage.JAVA_API);
        declareConfigurationCategory(project.getObjects(), apiElements, platformKind);
        return apiElements;
    }

    private void declareConfigurationCategory(ObjectFactory objectFactory, Configuration configuration, String value) {
        configuration.getAttributes().attribute(Category.CATEGORY_ATTRIBUTE, objectFactory.named(Category.class, value));
    }

    private void declareConfigurationUsage(ObjectFactory objectFactory, Configuration configuration, String usage, String libraryContents) {
        declareConfigurationUsage(objectFactory, configuration, usage);
        configuration.getAttributes().attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objectFactory.named(LibraryElements.class, libraryContents));
    }

    private void declareConfigurationUsage(ObjectFactory objectFactory, Configuration configuration, String usage) {
        configuration.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, usage));
    }

    private void configureExtension(Project project) {
        final DefaultJavaPlatformExtension platformExtension = (DefaultJavaPlatformExtension) project.getExtensions().create(JavaPlatformExtension.class, "javaPlatform", DefaultJavaPlatformExtension.class);
        project.afterEvaluate(project1 -> {
            if (!platformExtension.isAllowDependencies()) {
                checkNoDependencies(project1);
            }
        });
    }

    private void checkNoDependencies(Project project) {
        checkNoDependencies(project.getConfigurations().getByName(RUNTIME_CONFIGURATION_NAME), Sets.newHashSet());
    }

    private void checkNoDependencies(Configuration configuration, Set<Configuration> visited) {
        if (visited.add(configuration)) {
            if (!configuration.getDependencies().isEmpty()) {
                throw new InvalidUserCodeException(String.format(DISALLOW_DEPENDENCIES, configuration.getName()));
            }
            Set<Configuration> extendsFrom = configuration.getExtendsFrom();
            for (Configuration parent : extendsFrom) {
                checkNoDependencies(parent, visited);
            }
        }
    }

    private void configurePublishing(Project project) {
        project.getPlugins().withType(PublishingPlugin.class, plugin -> {
            PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);

            // Set up the default configurations used when mapping to resolved versions
            publishing.getPublications().withType(IvyPublication.class, publication -> {
                VersionMappingStrategyInternal strategy = ((PublicationInternal<?>) publication).getVersionMappingStrategy();
                strategy.defaultResolutionConfiguration(Usage.JAVA_API, CLASSPATH_CONFIGURATION_NAME);
                strategy.defaultResolutionConfiguration(Usage.JAVA_RUNTIME, CLASSPATH_CONFIGURATION_NAME);
            });
            publishing.getPublications().withType(MavenPublication.class, publication -> {
                VersionMappingStrategyInternal strategy = ((PublicationInternal<?>) publication).getVersionMappingStrategy();
                strategy.defaultResolutionConfiguration(Usage.JAVA_API, CLASSPATH_CONFIGURATION_NAME);
                strategy.defaultResolutionConfiguration(Usage.JAVA_RUNTIME, CLASSPATH_CONFIGURATION_NAME);
            });

        });
    }
}
