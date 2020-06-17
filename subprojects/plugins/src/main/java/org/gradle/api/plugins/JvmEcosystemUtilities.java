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

import javax.annotation.Nullable;
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
@SuppressWarnings("UnusedReturnValue")
public interface JvmEcosystemUtilities {
    /**
     * Adds an API configuration to a source set, so that API dependencies
     * can be declared.
     * @param sourceSet the source set to add an API for
     * @return the created API configuration
     */
    Configuration addApiToSourceSet(SourceSet sourceSet);

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
     * Replaces the artifacts of an outgoing configuration with a new set of artifacts.
     * This can be used whenever the default artifacts configured are not the ones you want to publish.
     * If this configuration inherits from other configurations, their artifacts will be removed.
     *
     * @param outgoingConfiguration the configuration for which to replace artifacts
     * @param providers the artifacts or providers of artifacts (e.g tasks providers) which should be associated with this configuration
     */
    void replaceArtifacts(Configuration outgoingConfiguration, Object... providers);

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
     * @param configuration the configuration builder, used to describe what the configuration is used for
     * @return an outgoing (consumable) configuration
     */
    Configuration createOutgoingElements(String name, Action<? super OutgoingElementsBuilder> configuration);

    /**
     * Creates a generic "java component", which implies creation of an underlying source set,
     * compile tasks, jar tasks, possibly javadocs and sources jars and allows configuring if such
     * a component has to be published externally.
     *
     * This can be used to create new tests, test fixtures, or any other Java component which
     * needs to live within the same project as the main component.
     *
     * @param name the name of the component to create
     * @param configuration the configuration for the component to be created
     */
    void createJavaComponent(String name, Action<? super JavaComponentBuilder> configuration);

    /**
     * Allows configuration of attributes used for JVM related components.
     * This can be used both on the producer side, to explain what it provides,
     * or on the consumer side, to express requirements.
     *
     * @since 6.6
     */
    @Incubating
    interface JvmEcosystemAttributesDetails {
        /**
         * Provides or requires a library
         */
        JvmEcosystemAttributesDetails library();

        /**
         * Provides or requires a platform
         */
        JvmEcosystemAttributesDetails platform();

        /**
         * Provides or requires an enforced platform
         */
        JvmEcosystemAttributesDetails enforcedPlatform();

        /**
         * Provides or requires documentation
         * @param docsType the documentation type (javadoc, sources, ...)
         */
        JvmEcosystemAttributesDetails documentation(String docsType);

        /**
         * Provides or requires an API
         */
        JvmEcosystemAttributesDetails apiUsage();

        /**
         * Provides or requires a runtime
         */
        JvmEcosystemAttributesDetails runtimeUsage();

        /**
         * Provides or requires a component which dependencies are found
         * as independent components (typically through external dependencies)
         */
        JvmEcosystemAttributesDetails withExternalDependencies();

        /**
         * Provides or requires a component which dependencies are bundled as part
         * of the main artifact
         */
        JvmEcosystemAttributesDetails withEmbeddedDependencies();

        /**
         * Provides or requires a component which dependencies are bundled as part
         * of the main artifact in a relocated/shadowed form
         */
        JvmEcosystemAttributesDetails withShadowedDependencies();

        /**
         * Provides or requires a complete component (jar) and not just the classes or
         * resources
         */
        JvmEcosystemAttributesDetails asJar();

        /**
         * Configures the target JVM version. For producers of a library, it's in general
         * a better idea to rely on inference which will calculate the target JVM version
         * lazily, for example calling {@link JvmEcosystemUtilities#useDefaultTargetPlatformInference(Configuration, SourceSet)}.
         * For consumers, it makes sense to specify a specific version of JVM they target.
         *
         * @param version the Java version
         */
        JvmEcosystemAttributesDetails withTargetJvmVersion(int version);
    }

    /**
     * A builder to construct an "outgoing elements" configuration, that is to say something
     * which is consumable by other components (other projects or external projects)
     *
     * @since 6.6
     */
    @Incubating
    interface OutgoingElementsBuilder {
        /**
         * Sets the description for this outgoing elements
         * @param description the description
         */
        OutgoingElementsBuilder withDescription(String description);

        /**
         * Tells that this elements configuration provides an API
         */
        OutgoingElementsBuilder providesApi();

        /**
         * Tells that this elements configuration provides a runtime
         */
        OutgoingElementsBuilder providesRuntime();

        /**
         * Allows setting the configurations this outgoing elements will inherit from.
         * Those configurations are typically buckets of dependencies
         * @param parentConfigurations the parent configurations
         */
        OutgoingElementsBuilder extendsFrom(Configuration... parentConfigurations);

        /**
         * If this method is called, the outgoing elements configuration will be automatically
         * configured to export the output of the source set.
         * @param sourceSet the source set which consistutes an output to share with this configuration
         */
        OutgoingElementsBuilder fromSourceSet(SourceSet sourceSet);

        /**
         * Registers an artifact to be attached to this configuration. The artifact needs
         * to be produced by a task.
         * @param producer the producer task
         */
        OutgoingElementsBuilder addArtifact(TaskProvider<Task> producer);

        /**
         * Allows refining the attributes of this configuration in case the defaults are not
         * sufficient. The refiner will be called after the default attributes are set.
         * @param refiner the attributes refiner configuration
         */
        OutgoingElementsBuilder attributes(Action<? super JvmEcosystemAttributesDetails> refiner);

        /**
         * Allows declaring the capabilities this outgoing configuration provides
         * @param capabilities the capabilities
         */
        OutgoingElementsBuilder withCapabilities(List<Capability> capabilities);

        /**
         * Configures this outgoing configuration to provides a "classes directory" variant, which
         * is useful for intra and inter-project optimization, avoiding the creation of jar tasks
         * when the only thing which is required is the API of a component.
         * This should only be called in association with {@link #fromSourceSet(SourceSet)}
         */
        OutgoingElementsBuilder withClassDirectoryVariant();

        /**
         * Configures this outgoing variant for publication. A published outgoing variant
         * configured this way will be mapped to the "optional" scope, meaning that its
         * dependencies will appear as optional in the generated POM file.
         */
        OutgoingElementsBuilder published();
    }

    /**
     * A Java component builder, allowing the automatic creation of a number of configurations,
     * tasks, ...
     *
     * @since 6.6
     */
    @Incubating
    @HasInternalProtocol
    interface JavaComponentBuilder {
        /**
         * If this method is called, this allows using an existing source set instead
         * of relying on automatic creation of a source set
         * @param sourceSet the existing source set to use
         */
        JavaComponentBuilder usingSourceSet(SourceSet sourceSet);

        /**
         * Sets a display name for this component
         * @param displayName the display name
         */
        JavaComponentBuilder withDisplayName(String displayName);

        /**
         * Tells that this component exposes an API, in which case a configuration
         * to declare API dependencies will be automatically created.
         */
        JavaComponentBuilder exposesApi();

        /**
         * Tells that this component should build a jar
         */
        JavaComponentBuilder withJar();

        /**
         * Tells that this component should build a javadoc jar too
         */
        JavaComponentBuilder withJavadocJar();

        /**
         * Tells that this component should build a sources jar too
         */
        JavaComponentBuilder withSourcesJar();

        /**
         * Explicitly declares a capability provided by this component
         * @param group the capability group
         * @param name the capability name
         * @param version the capability version
         */
        JavaComponentBuilder capability(String group, String name, @Nullable String version);

        /**
         * Tells that this component is not the main component and corresponds to a different "thing"
         * than the main one. For example, test fixtures are different than the component method.
         * It will implicitly declare a capability which name is derived from the project name and
         * this component name. For example, for project "lib" and a component named "languageSupport",
         * the capability name for this component will be "lib-language-support"
         */
        JvmEcosystemUtilities.JavaComponentBuilder secondaryComponent();

        /**
         * If this method is called, then this component will automatically be
         * published externally if a publishing plugin is applied.
         */
        JavaComponentBuilder published();
    }

}
