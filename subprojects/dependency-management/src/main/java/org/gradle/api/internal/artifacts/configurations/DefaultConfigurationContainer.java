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
import org.gradle.api.internal.AbstractNamedDomainObjectContainer;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.ConfigurationResolver;
import org.gradle.api.internal.artifacts.component.ComponentIdentifierFactory;
import org.gradle.api.internal.artifacts.dsl.PublishArtifactNotationParserFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionRules;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.ConfigurationComponentMetaDataBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultResolutionStrategy;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.initialization.ProjectAccessListener;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.progress.BuildOperationExecutor;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;

import java.util.Collection;
import java.util.Set;

public class DefaultConfigurationContainer extends AbstractNamedDomainObjectContainer<Configuration>
        implements ConfigurationContainerInternal, ConfigurationsProvider {
    public static final String DETACHED_CONFIGURATION_DEFAULT_NAME = "detachedConfiguration";

    private final ConfigurationResolver resolver;
    private final Instantiator instantiator;
    private final DomainObjectContext context;
    private final ListenerManager listenerManager;
    private final DependencyMetaDataProvider dependencyMetaDataProvider;
    private final ProjectAccessListener projectAccessListener;
    private final ProjectFinder projectFinder;
    private final ConfigurationComponentMetaDataBuilder configurationComponentMetaDataBuilder;
    private final FileCollectionFactory fileCollectionFactory;
    private final DependencySubstitutionRules globalDependencySubstitutionRules;
    private final ComponentIdentifierFactory componentIdentifierFactory;
    private final BuildOperationExecutor buildOperationExecutor;
    private final NotationParser<Object, ConfigurablePublishArtifact> artifactNotationParser;

    private int detachedConfigurationDefaultNameCounter = 1;

    public DefaultConfigurationContainer(ConfigurationResolver resolver,
                                         Instantiator instantiator, DomainObjectContext context, ListenerManager listenerManager,
                                         DependencyMetaDataProvider dependencyMetaDataProvider, ProjectAccessListener projectAccessListener,
                                         ProjectFinder projectFinder, ConfigurationComponentMetaDataBuilder configurationComponentMetaDataBuilder,
                                         FileCollectionFactory fileCollectionFactory, DependencySubstitutionRules globalDependencySubstitutionRules,
                                         ComponentIdentifierFactory componentIdentifierFactory, BuildOperationExecutor buildOperationExecutor) {
        super(Configuration.class, instantiator, new Configuration.Namer());
        this.resolver = resolver;
        this.instantiator = instantiator;
        this.context = context;
        this.listenerManager = listenerManager;
        this.dependencyMetaDataProvider = dependencyMetaDataProvider;
        this.projectAccessListener = projectAccessListener;
        this.projectFinder = projectFinder;
        this.configurationComponentMetaDataBuilder = configurationComponentMetaDataBuilder;
        this.fileCollectionFactory = fileCollectionFactory;
        this.globalDependencySubstitutionRules = globalDependencySubstitutionRules;
        this.componentIdentifierFactory = componentIdentifierFactory;
        this.buildOperationExecutor = buildOperationExecutor;
        this.artifactNotationParser = new PublishArtifactNotationParserFactory(instantiator, dependencyMetaDataProvider).create();
    }

    @Override
    protected Configuration doCreate(String name) {
        DefaultResolutionStrategy resolutionStrategy = instantiator.newInstance(DefaultResolutionStrategy.class, globalDependencySubstitutionRules, componentIdentifierFactory);
        return instantiator.newInstance(DefaultConfiguration.class, context.identityPath(name), context.projectPath(name), name, this, resolver,
                listenerManager, dependencyMetaDataProvider, resolutionStrategy, projectAccessListener, projectFinder,
                configurationComponentMetaDataBuilder, fileCollectionFactory, componentIdentifierFactory, buildOperationExecutor, instantiator, artifactNotationParser);
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
                context.identityPath(name), context.projectPath(name), name, detachedConfigurationsProvider, resolver,
                listenerManager, dependencyMetaDataProvider, new DefaultResolutionStrategy(globalDependencySubstitutionRules, componentIdentifierFactory), projectAccessListener, projectFinder,
                configurationComponentMetaDataBuilder, fileCollectionFactory, componentIdentifierFactory, buildOperationExecutor, instantiator, artifactNotationParser);
        DomainObjectSet<Dependency> detachedDependencies = detachedConfiguration.getDependencies();
        for (Dependency dependency : dependencies) {
            detachedDependencies.add(dependency.copy());
        }
        detachedConfigurationsProvider.setTheOnlyConfiguration(detachedConfiguration);
        return detachedConfiguration;
    }

    /**
     * Build a formatted representation of all Configurations in this ConfigurationContainer.
     * Configuration(s) being toStringed are likely derivations of DefaultConfiguration.
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
