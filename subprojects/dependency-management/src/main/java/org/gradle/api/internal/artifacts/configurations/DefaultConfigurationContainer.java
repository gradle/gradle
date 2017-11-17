/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.DomainObjectSet;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.artifacts.ConfigurablePublishArtifact;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.UnknownConfigurationException;
import org.gradle.api.internal.AbstractValidatingNamedDomainObjectContainer;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.ComponentSelectorConverter;
import org.gradle.api.internal.artifacts.ConfigurationResolver;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.component.ComponentIdentifierFactory;
import org.gradle.api.internal.artifacts.dsl.PublishArtifactNotationParserFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionRules;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.ConfigurationComponentMetaDataBuilder;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.DefaultRootComponentMetadataBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultResolutionStrategy;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.tasks.TaskResolver;
import org.gradle.initialization.ProjectAccessListener;
import org.gradle.internal.Factory;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.vcs.internal.VcsMappingsInternal;

import java.util.Collection;
import java.util.Set;

public class DefaultConfigurationContainer extends AbstractValidatingNamedDomainObjectContainer<Configuration>
    implements ConfigurationContainerInternal, ConfigurationsProvider {
    public static final String DETACHED_CONFIGURATION_DEFAULT_NAME = "detachedConfiguration";

    private final ConfigurationResolver resolver;
    private final Instantiator instantiator;
    private final DomainObjectContext context;
    private final ListenerManager listenerManager;
    private final DependencyMetaDataProvider dependencyMetaDataProvider;
    private final ProjectAccessListener projectAccessListener;
    private final ProjectFinder projectFinder;
    private final FileCollectionFactory fileCollectionFactory;
    private final BuildOperationExecutor buildOperationExecutor;
    private final NotationParser<Object, ConfigurablePublishArtifact> artifactNotationParser;
    private final ImmutableAttributesFactory attributesFactory;

    private int detachedConfigurationDefaultNameCounter = 1;
    private final Factory<ResolutionStrategyInternal> resolutionStrategyFactory;
    private final DefaultRootComponentMetadataBuilder rootComponentMetadataBuilder;
    private ConfigurationUseSite useSite;

    public DefaultConfigurationContainer(ConfigurationResolver resolver,
                                         final Instantiator instantiator, DomainObjectContext context, ListenerManager listenerManager,
                                         DependencyMetaDataProvider dependencyMetaDataProvider, ProjectAccessListener projectAccessListener,
                                         ProjectFinder projectFinder, ConfigurationComponentMetaDataBuilder configurationComponentMetaDataBuilder,
                                         FileCollectionFactory fileCollectionFactory,
                                         final DependencySubstitutionRules globalDependencySubstitutionRules,
                                         final VcsMappingsInternal vcsMappingsInternal,
                                         final ComponentIdentifierFactory componentIdentifierFactory,
                                         BuildOperationExecutor buildOperationExecutor,
                                         TaskResolver taskResolver,
                                         ImmutableAttributesFactory attributesFactory,
                                         final ImmutableModuleIdentifierFactory moduleIdentifierFactory,
                                         final ComponentSelectorConverter componentSelectorConverter) {
        super(Configuration.class, instantiator, new Configuration.Namer());
        this.resolver = resolver;
        this.instantiator = instantiator;
        this.context = context;
        this.listenerManager = listenerManager;
        this.dependencyMetaDataProvider = dependencyMetaDataProvider;
        this.projectAccessListener = projectAccessListener;
        this.projectFinder = projectFinder;
        this.fileCollectionFactory = fileCollectionFactory;
        this.buildOperationExecutor = buildOperationExecutor;
        this.artifactNotationParser = new PublishArtifactNotationParserFactory(instantiator, dependencyMetaDataProvider, taskResolver).create();
        this.attributesFactory = attributesFactory;
        resolutionStrategyFactory = new Factory<ResolutionStrategyInternal>() {
            @Override
            public ResolutionStrategyInternal create() {
                return instantiator.newInstance(DefaultResolutionStrategy.class, globalDependencySubstitutionRules, vcsMappingsInternal, componentIdentifierFactory, moduleIdentifierFactory, componentSelectorConverter);
            }
        };
        this.rootComponentMetadataBuilder = new DefaultRootComponentMetadataBuilder(dependencyMetaDataProvider, componentIdentifierFactory, moduleIdentifierFactory, projectFinder, configurationComponentMetaDataBuilder, this);

        if (context.isScript()) {
            this.useSite = ConfigurationUseSite.script();
        } else {
            if (context.getProjectPath() == null) {
                this.useSite = ConfigurationUseSite.unknown();
            } else {
                this.useSite = ConfigurationUseSite.project(context.getProjectPath());
            }
        }
    }

    @Override
    protected Configuration doCreate(String name) {
        DefaultConfiguration configuration = instantiator.newInstance(DefaultConfiguration.class, context, useSite, name, this, resolver,
            listenerManager, dependencyMetaDataProvider, resolutionStrategyFactory, projectAccessListener, projectFinder,
            fileCollectionFactory, buildOperationExecutor, instantiator, artifactNotationParser, attributesFactory, rootComponentMetadataBuilder);
        configuration.addMutationValidator(rootComponentMetadataBuilder.getValidator());
        return configuration;
    }

    public Set<? extends ConfigurationInternal> getAll() {
        return withType(ConfigurationInternal.class);
    }

    @Override
    public ConfigurationInternal getByName(String name) {
        return (ConfigurationInternal) super.getByName(name);
    }

    @Override
    public String getTypeDisplayName() {
        return "configuration";
    }

    @Override
    protected UnknownDomainObjectException createNotFoundException(String name) {
        return new UnknownConfigurationException(String.format("Configuration with name '%s' not found.", name));
    }

    public ConfigurationInternal detachedConfiguration(Dependency... dependencies) {
        String name = DETACHED_CONFIGURATION_DEFAULT_NAME + detachedConfigurationDefaultNameCounter++;
        DetachedConfigurationsProvider detachedConfigurationsProvider = new DetachedConfigurationsProvider();
        DefaultConfiguration detachedConfiguration = instantiator.newInstance(DefaultConfiguration.class,
            context, useSite, name, detachedConfigurationsProvider, resolver,
            listenerManager, dependencyMetaDataProvider, resolutionStrategyFactory, projectAccessListener, projectFinder,
            fileCollectionFactory, buildOperationExecutor, instantiator, artifactNotationParser, attributesFactory,
            rootComponentMetadataBuilder.withConfigurationsProvider(detachedConfigurationsProvider));
        DomainObjectSet<Dependency> detachedDependencies = detachedConfiguration.getDependencies();
        for (Dependency dependency : dependencies) {
            detachedDependencies.add(dependency.copy());
        }
        detachedConfigurationsProvider.setTheOnlyConfiguration(detachedConfiguration);
        return detachedConfiguration;
    }

    /**
     * Build a formatted representation of all Configurations in this ConfigurationContainer. Configuration(s) being toStringed are likely derivations of DefaultConfiguration.
     */
    public String dump() {
        StringBuilder reply = new StringBuilder();

        reply.append("Configuration of type: " + getTypeDisplayName());
        Collection<? extends Configuration> configs = getAll();
        for (Configuration c : configs) {
            reply.append("\n  " + c.toString());
        }

        return reply.toString();
    }

}
