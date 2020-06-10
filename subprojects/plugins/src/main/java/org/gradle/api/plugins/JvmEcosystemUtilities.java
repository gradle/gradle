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
package org.gradle.api.plugins;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.NonNullApi;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.HasConfigurableAttributes;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.internal.HasInternalProtocol;

import java.util.List;

/**
 * This class exposes a number of utilities which can be used by plugin authors
 * to either replicate what the Gradle JVM plugins are doing, or integrate with
 * the existing Jvm plugins.
 *
 * @since 6.6
 */
@Incubating
@NonNullApi
@HasInternalProtocol
public interface JvmEcosystemUtilities {
    /**
     * Adds an API configuration to a source set, so that API dependencies
     * can be declared.
     * @param sourceSet the source set to add an API for
     */
    void addApiToSourceSet(SourceSet sourceSet);

    /**
     * Registers a source set as contributing classes and exposes them as a variant.
     *
     * @param configurationName the name of the configuration for which a classes variant should be exposed
     * @param sourceSet the source set which will contribute classes to this variant
     */
    void configureClassesDirectoryVariant(String configurationName, SourceSet sourceSet);

    /**
     * Configures a configuration with reasonable defaults to be resolved as a compile classpath.
     *
     * @param configuration the configuration to be configured
     */
    <T> void configureAsCompileClasspath(HasConfigurableAttributes<T> configuration);

    /**
     * Configures a configuration with reasonable defaults to be resolved as a runtime classpath.
     *
     * @param configuration the configuration to be configured
     */
    <T> void configureAsRuntimeClasspath(HasConfigurableAttributes<T> configuration);

    <T> void configureAttributes(HasConfigurableAttributes<T> configurableAttributes, Action<? super JvmEcosystemAttributesDetails> details);

    /**
     * Configures a configuration so that its exposed target jvm version is inferred from
     * the specified source set.
     * @param configuration the configuration to configure
     * @param sourceSet the source set which serves as reference for inference
     */
    void useDefaultTargetPlatformInference(Configuration configuration, SourceSet sourceSet);

    /**
     * Creates an outgoing configuration and configures it with reasonable defaults.
     * @param name the name of the outgoing configurtion
     * @param builder the configuration builder, used to describe what the configuration is used for
     * @return an outgoing (consumable) configuration
     */
    Configuration createOutgoingElements(String name, Action<? super OutgoingElementsBuilder> builder);

    @SuppressWarnings("UnusedReturnValue")
    interface JvmEcosystemAttributesDetails {
        JvmEcosystemAttributesDetails library();
        JvmEcosystemAttributesDetails platform();
        JvmEcosystemAttributesDetails enforcedPlatform();

        JvmEcosystemAttributesDetails providingApi();
        JvmEcosystemAttributesDetails providingRuntime();

        JvmEcosystemAttributesDetails withExternalDependencies();
        JvmEcosystemAttributesDetails withEmbeddedDependencies();
        JvmEcosystemAttributesDetails withShadowedDependencies();

        JvmEcosystemAttributesDetails asJar();
    }

    @SuppressWarnings("UnusedReturnValue")
    interface OutgoingElementsBuilder {
        OutgoingElementsBuilder withDescription(String description);
        OutgoingElementsBuilder forApi();
        OutgoingElementsBuilder forRuntime();
        OutgoingElementsBuilder extendsFrom(Configuration... parentConfigurations);
        OutgoingElementsBuilder fromSourceSet(SourceSet sourceSet);
        OutgoingElementsBuilder addArtifact(TaskProvider<Task> producer);
        OutgoingElementsBuilder attributes(Action<? super JvmEcosystemAttributesDetails> refiner);
        OutgoingElementsBuilder withCapabilities(List<Capability> capabilities);
        OutgoingElementsBuilder withClassDirectoryVariant();
    }

}
