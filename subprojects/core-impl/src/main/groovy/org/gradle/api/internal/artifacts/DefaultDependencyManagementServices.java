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

import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.maven.MavenFactory;
import org.gradle.api.internal.ClassGenerator;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.Factory;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler;
import org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyHandler;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.artifacts.ivyservice.ResolverFactory;
import org.gradle.api.internal.artifacts.publish.maven.DefaultLocalMavenCacheLocator;
import org.gradle.api.internal.artifacts.publish.maven.DefaultMavenFactory;
import org.gradle.api.internal.artifacts.repositories.DefaultResolverFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.project.DefaultServiceRegistry;
import org.gradle.api.internal.project.ServiceRegistry;
import org.gradle.logging.LoggingManagerInternal;

public class DefaultDependencyManagementServices extends DefaultServiceRegistry implements DependencyManagementServices {
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
                configurationContainer = parent.get(ConfigurationContainerFactory.class).createConfigurationContainer(getResolveRepositoryHandler(), dependencyMetaDataProvider, domainObjectContext);
            }
            return configurationContainer;
        }

        public DependencyHandler getDependencyHandler() {
            if (dependencyHandler == null) {
                dependencyHandler = new DefaultDependencyHandler(getConfigurationContainer(), parent.get(DependencyFactory.class), projectFinder);
            }
            return dependencyHandler;
        }

        RepositoryHandler createRepositoryHandlerWithSharedConventionMapping() {
            IConventionAware prototype = (IConventionAware) getResolveRepositoryHandler();
            RepositoryHandler handler = initialiseRepositoryHandler(createRepositoryHandler());
            ((IConventionAware)handler).setConventionMapping(prototype.getConventionMapping());
            return handler;
        }

        public Factory<ArtifactPublicationServices> getPublishServicesFactory() {
            return new Factory<ArtifactPublicationServices>() {
                public ArtifactPublicationServices create() {
                    return new DefaultArtifactPublicationServices(parent, DefaultDependencyResolutionServices.this);
                }
            };
        }
    }

    private static class DefaultArtifactPublicationServices implements ArtifactPublicationServices {
        private final DefaultDependencyResolutionServices dependencyResolutionServices;
        private final ServiceRegistry parent;
        private RepositoryHandler repositoryHandler;

        public DefaultArtifactPublicationServices(ServiceRegistry parent, DefaultDependencyResolutionServices dependencyResolutionServices) {
            this.parent = parent;
            this.dependencyResolutionServices = dependencyResolutionServices;
        }

        public IvyService getIvyService() {
            return parent.get(IvyService.class);
        }

        public RepositoryHandler getRepositoryHandler() {
            if (repositoryHandler == null) {
                repositoryHandler = dependencyResolutionServices.createRepositoryHandlerWithSharedConventionMapping();
            }
            return repositoryHandler;
        }
    }
}
