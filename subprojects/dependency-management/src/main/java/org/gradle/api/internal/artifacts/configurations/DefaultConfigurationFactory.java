/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.artifacts.configurations;

import org.gradle.api.GradleException;
import org.gradle.api.artifacts.ConfigurablePublishArtifact;
import org.gradle.api.artifacts.DependencyResolutionListener;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.ConfigurationResolver;
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParserFactory;
import org.gradle.api.internal.artifacts.dsl.PublishArtifactNotationParserFactory;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.RootComponentMetadataBuilder;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.configuration.internal.UserCodeApplicationContext;
import org.gradle.internal.Factory;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.work.WorkerThreadRegistry;

import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

/**
 * Factory for creating {@link org.gradle.api.artifacts.Configuration} instances.
 */
@ThreadSafe
public class DefaultConfigurationFactory {

    private final Instantiator instantiator;
    private final ConfigurationResolver resolver;
    private final ListenerManager listenerManager;
    private final DependencyMetaDataProvider metaDataProvider;
    private final DomainObjectContext domainObjectContext;
    private final FileCollectionFactory fileCollectionFactory;
    private final BuildOperationExecutor buildOperationExecutor;
    private final NotationParser<Object, ConfigurablePublishArtifact> artifactNotationParser;
    private final NotationParser<Object, Capability> capabilityNotationParser;
    private final ImmutableAttributesFactory attributesFactory;
    private final DocumentationRegistry documentationRegistry;
    private final UserCodeApplicationContext userCodeApplicationContext;
    private final ProjectStateRegistry projectStateRegistry;
    private final WorkerThreadRegistry workerThreadRegistry;
    private final DomainObjectCollectionFactory domainObjectCollectionFactory;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;

    @Inject
    public DefaultConfigurationFactory(
        Instantiator instantiator,
        ConfigurationResolver resolver,
        ListenerManager listenerManager,
        DependencyMetaDataProvider metaDataProvider,
        DomainObjectContext domainObjectContext,
        FileCollectionFactory fileCollectionFactory,
        BuildOperationExecutor buildOperationExecutor,
        PublishArtifactNotationParserFactory artifactNotationParserFactory,
        ImmutableAttributesFactory attributesFactory,
        DocumentationRegistry documentationRegistry,
        UserCodeApplicationContext userCodeApplicationContext,
        ProjectStateRegistry projectStateRegistry,
        WorkerThreadRegistry workerThreadRegistry,
        DomainObjectCollectionFactory domainObjectCollectionFactory,
        CalculatedValueContainerFactory calculatedValueContainerFactory
    ) {
        this.instantiator = instantiator;
        this.resolver = resolver;
        this.listenerManager = listenerManager;
        this.metaDataProvider = metaDataProvider;
        this.domainObjectContext = domainObjectContext;
        this.fileCollectionFactory = fileCollectionFactory;
        this.buildOperationExecutor = buildOperationExecutor;
        this.artifactNotationParser = artifactNotationParserFactory.create();
        this.capabilityNotationParser = new CapabilityNotationParserFactory(true).create();
        this.attributesFactory = attributesFactory;
        this.documentationRegistry = documentationRegistry;
        this.userCodeApplicationContext = userCodeApplicationContext;
        this.projectStateRegistry = projectStateRegistry;
        this.workerThreadRegistry = workerThreadRegistry;
        this.domainObjectCollectionFactory = domainObjectCollectionFactory;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
    }

    /**
     * Creates a new {@link DefaultConfiguration} instance.
     */
    DefaultConfiguration create(
        String name,
        ConfigurationsProvider configurationsProvider,
        Factory<ResolutionStrategyInternal> resolutionStrategyFactory,
        RootComponentMetadataBuilder rootComponentMetadataBuilder
    ) {
        ListenerBroadcast<DependencyResolutionListener> dependencyResolutionListeners =
            listenerManager.createAnonymousBroadcaster(DependencyResolutionListener.class);
        return instantiator.newInstance(
            DefaultConfiguration.class,
            domainObjectContext,
            name,
            configurationsProvider,
            resolver,
            dependencyResolutionListeners,
            listenerManager.getBroadcaster(ProjectDependencyObservedListener.class),
            metaDataProvider,
            resolutionStrategyFactory,
            fileCollectionFactory,
            buildOperationExecutor,
            instantiator,
            artifactNotationParser,
            capabilityNotationParser,
            attributesFactory,
            rootComponentMetadataBuilder,
            documentationRegistry,
            userCodeApplicationContext,
            projectStateRegistry,
            workerThreadRegistry,
            domainObjectCollectionFactory,
            calculatedValueContainerFactory,
            this
        );
    }

    /**
     * Assigns a usage role to a configuration, by setting internal usage flags (e.g. {@link ConfigurationInternal#isCanBeResolved()})
     * and/or marking such usages as deprecated.
     *
     * @return the given configuration; now configured for a role
     */
    @SuppressWarnings({"fallthrough", "deprecation"})
    public static ConfigurationInternal assignRole(ConfigurationInternal configuration, ConfigurationRoles role) {
        switch (role) {
            case LEGACY:
                configuration.setCanBeConsumed(true);
                configuration.setCanBeResolved(true);
                configuration.setCanBeDeclaredAgainst(true);
                break;

            case INTENDED_BUCKET:
                configuration.setCanBeConsumed(false);
                configuration.setCanBeResolved(false);
                configuration.setCanBeDeclaredAgainst(true);
                break;

            case DEPRECATED_CONSUMABLE:
                configuration.deprecateForConsumption(
                    depSpec -> DeprecationLogger.deprecateConfiguration(configuration.getName())
                        .forConsumption()
                        .willBecomeAnErrorInGradle9()
                        .withUserManual("dependencies_should_no_longer_be_declared_using_the_compile_and_runtime_configurations")); // fall-through
            case INTENDED_CONSUMABLE:
                configuration.setCanBeConsumed(true);
                configuration.setCanBeResolved(false);
                configuration.setCanBeDeclaredAgainst(false);
                break;

            case DEPRECATED_RESOLVABLE:
                configuration.deprecateForResolution(); // fall-through
            case INTENDED_RESOLVABLE:
                configuration.setCanBeConsumed(false);
                configuration.setCanBeResolved(true);
                configuration.setCanBeDeclaredAgainst(false);
                break;

            default:
                throwUnknownRole(configuration, role);
        }

        configuration.preventRoleMutation();
        return configuration;
    }

    @SuppressWarnings({"fallthrough", "deprecation"})
    public static ConfigurationInternal assertInRole(ConfigurationInternal configuration, ConfigurationRoles role) {
        switch (role) {
            case LEGACY:
                if (!configuration.isCanBeConsumed() || !configuration.isCanBeResolved() || !configuration.isCanBeDeclaredAgainst()) {
                    throwConfigurationNotInRole(configuration, role);
                }
                break;

            case INTENDED_BUCKET:
                if (configuration.isCanBeConsumed() || configuration.isCanBeResolved() || !configuration.isCanBeDeclaredAgainst()) {
                    throwConfigurationNotInRole(configuration, role);
                }
                break;

            case DEPRECATED_CONSUMABLE:
                if (!configuration.isDeprecatedForConsumption()) {
                    throwConfigurationNotInRole(configuration, role);
                } // fall-through
            case INTENDED_CONSUMABLE:
                if (!configuration.isCanBeConsumed() || configuration.isCanBeResolved() || configuration.isCanBeDeclaredAgainst()) {
                    throwConfigurationNotInRole(configuration, role);
                }
                break;

            case DEPRECATED_RESOLVABLE:
                if (!configuration.isDeprecatedForResolution()) {
                    throwConfigurationNotInRole(configuration, role);
                } // fall-through
            case INTENDED_RESOLVABLE:
                if (configuration.isCanBeConsumed() || !configuration.isCanBeResolved() || configuration.isCanBeDeclaredAgainst()) {
                    throwConfigurationNotInRole(configuration, role);
                }
                break;

            default:
                throwUnknownRole(configuration, role);
        }

        return configuration;
    }

    private static void throwUnknownRole(ConfigurationInternal configuration, ConfigurationRoles role) throws GradleException {
        throw new GradleException("Configuration '" + configuration.getName() + "' is being assigned to or queried for an unknown role: " + role);
    }

    private static void throwConfigurationNotInRole(ConfigurationInternal configuration, ConfigurationRoles role) throws GradleException {
        throw new GradleException("Configuration '" + configuration.getName() + "' is not in the " + role + " role.  This configuration is:\n" + ConfigurationRoles.describeRole(configuration));
    }
}
