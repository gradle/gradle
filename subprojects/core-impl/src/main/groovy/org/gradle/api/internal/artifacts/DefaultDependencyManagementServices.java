/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts;

import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.gradle.StartParameter;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.maven.MavenFactory;
import org.gradle.api.internal.*;
import org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler;
import org.gradle.api.internal.artifacts.dsl.dependencies.*;
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.*;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.*;
import org.gradle.api.internal.artifacts.publish.maven.DefaultLocalMavenCacheLocator;
import org.gradle.api.internal.artifacts.publish.maven.DefaultMavenFactory;
import org.gradle.api.internal.artifacts.repositories.DefaultInternalRepository;
import org.gradle.api.internal.artifacts.repositories.DefaultResolverFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.IdentityFileResolver;
import org.gradle.api.internal.project.DefaultServiceRegistry;
import org.gradle.api.internal.project.ServiceRegistry;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.util.WrapUtil;

import java.util.HashMap;
import java.util.Map;

public class DefaultDependencyManagementServices extends DefaultServiceRegistry implements DependencyManagementServices {
    private final Map<String, ModuleDescriptor> clientModuleRegistry = new HashMap<String, ModuleDescriptor>();

    public DefaultDependencyManagementServices(ServiceRegistry parent) {
        super(parent);
    }

    public DependencyResolutionServices create(FileResolver resolver, DependencyMetaDataProvider dependencyMetaDataProvider, ProjectFinder projectFinder, DomainObjectContext domainObjectContext) {
        return new DefaultDependencyResolutionServices(this, resolver, dependencyMetaDataProvider, projectFinder, domainObjectContext);
    }

    protected ResolverFactory createResolverFactory() {
        return new DefaultResolverFactory(getFactory(LoggingManagerInternal.class), get(MavenFactory.class), new DefaultLocalMavenCacheLocator());
    }

    protected MavenFactory createMavenFactory() {
        return new DefaultMavenFactory();
    }

    protected PublishModuleDescriptorConverter createPublishModuleDescriptorConverter() {
        return new PublishModuleDescriptorConverter(
                createResolveModuleDescriptorConverter(ProjectDependencyDescriptorFactory.RESOLVE_DESCRIPTOR_STRATEGY),
                new DefaultArtifactsToModuleDescriptorConverter(DefaultArtifactsToModuleDescriptorConverter.RESOLVE_STRATEGY));
    }

    protected IvyServiceFactory createIvyServiceFactory() {
        DependencyDescriptorFactory dependencyDescriptorFactoryDelegate = createDependencyDescriptorFactory(ProjectDependencyDescriptorFactory.RESOLVE_DESCRIPTOR_STRATEGY);
        PublishModuleDescriptorConverter fileModuleDescriptorConverter = new PublishModuleDescriptorConverter(
                createResolveModuleDescriptorConverter(ProjectDependencyDescriptorFactory.IVY_FILE_DESCRIPTOR_STRATEGY),
                new DefaultArtifactsToModuleDescriptorConverter(DefaultArtifactsToModuleDescriptorConverter.IVY_FILE_STRATEGY));

        return new DefaultIvyServiceFactory(clientModuleRegistry,
                new DefaultSettingsConverter(
                        get(ProgressLoggerFactory.class)
                ),
                get(PublishModuleDescriptorConverter.class),
                get(PublishModuleDescriptorConverter.class),
                fileModuleDescriptorConverter,
                new DefaultIvyFactory(),
                new SelfResolvingDependencyResolver(
                        new DefaultIvyDependencyResolver(
                                new DefaultIvyReportConverter(dependencyDescriptorFactoryDelegate))),
                new DefaultIvyDependencyPublisher(new DefaultPublishOptionsFactory()));
    }

    protected ModuleDescriptorFactory createModuleDescriptorFactory() {
        return new DefaultModuleDescriptorFactory();
    }

    protected ExcludeRuleConverter createExcludeRuleConverter() {
        return new DefaultExcludeRuleConverter();
    }

    protected ExternalModuleDependencyDescriptorFactory createExternalModuleDependencyDescriptorFactory() {
        return new ExternalModuleDependencyDescriptorFactory(get(ExcludeRuleConverter.class));
    }

    protected ConfigurationsToModuleDescriptorConverter createConfigurationsToModuleDescriptorConverter() {
        return new DefaultConfigurationsToModuleDescriptorConverter();
    }

    private ResolveModuleDescriptorConverter createResolveModuleDescriptorConverter(ProjectDependencyDescriptorStrategy projectDependencyStrategy) {
        DependencyDescriptorFactory dependencyDescriptorFactoryDelegate = createDependencyDescriptorFactory(projectDependencyStrategy);
        return new ResolveModuleDescriptorConverter(
                get(ModuleDescriptorFactory.class),
                get(ConfigurationsToModuleDescriptorConverter.class),
                new DefaultDependenciesToModuleDescriptorConverter(
                        dependencyDescriptorFactoryDelegate,
                        get(ExcludeRuleConverter.class)));
    }

    private DependencyDescriptorFactory createDependencyDescriptorFactory(ProjectDependencyDescriptorStrategy projectDependencyStrategy) {
        DefaultModuleDescriptorFactoryForClientModule clientModuleDescriptorFactory = new DefaultModuleDescriptorFactoryForClientModule();
        DependencyDescriptorFactory dependencyDescriptorFactoryDelegate = new DependencyDescriptorFactoryDelegate(
                new ClientModuleDependencyDescriptorFactory(
                        get(ExcludeRuleConverter.class), clientModuleDescriptorFactory, clientModuleRegistry),
                new ProjectDependencyDescriptorFactory(
                        get(ExcludeRuleConverter.class),
                        projectDependencyStrategy),
                get(ExternalModuleDependencyDescriptorFactory.class));
        clientModuleDescriptorFactory.setDependencyDescriptorFactory(dependencyDescriptorFactoryDelegate);
        return dependencyDescriptorFactoryDelegate;
    }

    protected DependencyFactory createDependencyFactory() {
        ClassGenerator classGenerator = get(ClassGenerator.class);
        DefaultProjectDependencyFactory projectDependencyFactory = new DefaultProjectDependencyFactory(
                get(StartParameter.class).getProjectDependenciesBuildInstruction(),
                classGenerator);
        return new DefaultDependencyFactory(
                WrapUtil.<IDependencyImplementationFactory>toSet(
                        new ModuleDependencyFactory(
                                classGenerator),
                        new SelfResolvingDependencyFactory(
                                classGenerator),
                        new ClassPathDependencyFactory(
                                classGenerator,
                                get(ClassPathRegistry.class),
                                new IdentityFileResolver()),
                        projectDependencyFactory),
                new DefaultClientModuleFactory(
                        classGenerator),
                projectDependencyFactory);
    }

    private static class DefaultDependencyResolutionServices implements DependencyResolutionServices {
        private final ServiceRegistry parent;
        private final FileResolver fileResolver;
        private final DependencyMetaDataProvider dependencyMetaDataProvider;
        private final ProjectFinder projectFinder;
        private final DomainObjectContext domainObjectContext;
        private DefaultRepositoryHandler repositoryHandler;
        private ConfigurationContainer configurationContainer;
        private DependencyHandler dependencyHandler;

        private DefaultDependencyResolutionServices(ServiceRegistry parent, FileResolver fileResolver, DependencyMetaDataProvider dependencyMetaDataProvider, ProjectFinder projectFinder, DomainObjectContext domainObjectContext) {
            this.parent = parent;
            this.fileResolver = fileResolver;
            this.dependencyMetaDataProvider = dependencyMetaDataProvider;
            this.projectFinder = projectFinder;
            this.domainObjectContext = domainObjectContext;
        }

        public RepositoryHandler getResolveRepositoryHandler() {
            if (repositoryHandler == null) {
                repositoryHandler = createRepositoryHandler();
                initialiseRepositoryHandler(repositoryHandler);
            }
            return repositoryHandler;
        }

        private DefaultRepositoryHandler initialiseRepositoryHandler(DefaultRepositoryHandler repositoryHandler) {
            repositoryHandler.setConfigurationContainer(getConfigurationContainer());
            return repositoryHandler;
        }

        private DefaultRepositoryHandler createRepositoryHandler() {
            ClassGenerator classGenerator = parent.get(ClassGenerator.class);
            ResolverFactory resolverFactory = parent.get(ResolverFactory.class);
            return classGenerator.newInstance(DefaultRepositoryHandler.class, resolverFactory, fileResolver, classGenerator);
        }

        public ConfigurationContainer getConfigurationContainer() {
            if (configurationContainer == null) {
                ClassGenerator classGenerator = parent.get(ClassGenerator.class);
                IvyService ivyService = createIvyService(getResolveRepositoryHandler());
                configurationContainer = classGenerator.newInstance(DefaultConfigurationContainer.class, ivyService, classGenerator, domainObjectContext);
            }
            return configurationContainer;
        }

        public DependencyHandler getDependencyHandler() {
            if (dependencyHandler == null) {
                dependencyHandler = new DefaultDependencyHandler(getConfigurationContainer(), parent.get(DependencyFactory.class), projectFinder);
            }
            return dependencyHandler;
        }

        public Factory<ArtifactPublicationServices> getPublishServicesFactory() {
            return new Factory<ArtifactPublicationServices>() {
                public ArtifactPublicationServices create() {
                    return new DefaultArtifactPublicationServices(DefaultDependencyResolutionServices.this);
                }
            };
        }

        IvyService createIvyService(RepositoryHandler resolverProvider) {
            IvyServiceFactory ivyServiceFactory = parent.get(IvyServiceFactory.class);
            return ivyServiceFactory.newIvyService(resolverProvider, dependencyMetaDataProvider, new DefaultInternalRepository(projectFinder, parent.get(ModuleDescriptorConverter.class)));
        }

        RepositoryHandler createRepositoryHandlerWithSharedConventionMapping() {
            IConventionAware prototype = (IConventionAware) getResolveRepositoryHandler();
            RepositoryHandler handler = initialiseRepositoryHandler(createRepositoryHandler());
            ((IConventionAware)handler).setConventionMapping(prototype.getConventionMapping());
            return handler;
        }
    }

    private static class DefaultArtifactPublicationServices implements ArtifactPublicationServices {
        private final DefaultDependencyResolutionServices dependencyResolutionServices;
        private RepositoryHandler repositoryHandler;
        private IvyService ivyService;

        public DefaultArtifactPublicationServices(DefaultDependencyResolutionServices dependencyResolutionServices) {
            this.dependencyResolutionServices = dependencyResolutionServices;
        }

        public IvyService getIvyService() {
            if (ivyService == null) {
                ivyService = dependencyResolutionServices.createIvyService(getRepositoryHandler());
            }
            return ivyService;
        }

        public RepositoryHandler getRepositoryHandler() {
            if (repositoryHandler == null) {
                repositoryHandler = dependencyResolutionServices.createRepositoryHandlerWithSharedConventionMapping();
            }
            return repositoryHandler;
        }
    }
}
