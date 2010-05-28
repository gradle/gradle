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

package org.gradle.api.internal.artifacts;

import groovy.lang.Closure;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ResolverContainer;
import org.gradle.api.artifacts.UnknownRepositoryException;
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer;
import org.gradle.api.artifacts.maven.GroovyMavenDeployer;
import org.gradle.api.artifacts.maven.MavenResolver;
import org.gradle.api.internal.ClassGenerator;
import org.gradle.api.internal.DefaultNamedDomainObjectContainer;
import org.gradle.api.internal.artifacts.ivyservice.ResolverFactory;
import org.gradle.api.internal.artifacts.publish.maven.MavenPomMetaInfoProvider;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Hans Dockter
 */
public class DefaultResolverContainer extends DefaultNamedDomainObjectContainer<DependencyResolver>
        implements ResolverContainer, MavenPomMetaInfoProvider {
    private ResolverFactory resolverFactory;

    private List<String> resolverNames = new ArrayList<String>();

    private File mavenPomDir;

    private FileResolver fileResolver;

    private Conf2ScopeMappingContainer mavenScopeMappings;

    private ConfigurationContainer configurationContainer;

    public DefaultResolverContainer(ResolverFactory resolverFactory, ClassGenerator classGenerator) {
        super(DependencyResolver.class, classGenerator);
        this.resolverFactory = resolverFactory;
    }

    @Override
    public String getTypeDisplayName() {
        return "resolver";
    }

    public DependencyResolver add(Object userDescription) {
        return add(userDescription, null);
    }

    public DependencyResolver add(Object userDescription, Closure configureClosure) {
        return addInternal(userDescription, configureClosure, new OrderAction() {
            public void apply(String resolverName) {
                resolverNames.add(resolverName);
            }
        });
    }

    public DependencyResolver addBefore(Object userDescription, String afterResolverName) {
        return addBefore(userDescription, afterResolverName, null);
    }

    public DependencyResolver addBefore(Object userDescription, final String afterResolverName, Closure configureClosure) {
        if (!GUtil.isTrue(afterResolverName)) {
            throw new InvalidUserDataException("You must specify userDescription and afterResolverName");
        }
        if (findByName(afterResolverName) == null) {
            throw new InvalidUserDataException("Resolver $afterResolverName does not exists!");
        }
        return addInternal(userDescription, configureClosure, new OrderAction() {
            public void apply(String resolverName) {
                resolverNames.add(resolverNames.indexOf(afterResolverName), resolverName);
            }
        });
    }

    public DependencyResolver addAfter(Object userDescription, final String beforeResolverName) {
        return addAfter(userDescription, beforeResolverName, null);
    }

    public DependencyResolver addAfter(Object userDescription, final String beforeResolverName, Closure configureClosure) {
        if (!GUtil.isTrue(beforeResolverName)) {
            throw new InvalidUserDataException("You must specify userDescription and beforeResolverName");
        }
        if (findByName(beforeResolverName) == null) {
            throw new InvalidUserDataException("Resolver $beforeResolverName does not exists!");
        }
        return addInternal(userDescription, configureClosure, new OrderAction() {
            public void apply(String resolverName) {
                int insertPos = resolverNames.indexOf(beforeResolverName) + 1;
                if (insertPos == resolverNames.size()) {
                    resolverNames.add(resolverName);
                } else {
                    resolverNames.add(insertPos, resolverName);
                }
            }
        });
    }

    public DependencyResolver addFirst(Object userDescription) {
        return addFirst(userDescription, null);
    }

    public DependencyResolver addFirst(Object userDescription, Closure configureClosure) {
        if (!GUtil.isTrue(userDescription)) {
            throw new InvalidUserDataException("You must specify userDescription");
        }
        return addInternal(userDescription, configureClosure, new OrderAction() {
            public void apply(String resolverName) {
                if (resolverNames.size() == 0) {
                    resolverNames.add(resolverName);
                } else {
                    resolverNames.add(0, resolverName);
                }
            }
        });
    }

    private DependencyResolver addInternal(Object userDescription, Closure configureClosure, OrderAction orderAction) {
        if (!GUtil.isTrue(userDescription)) {
            throw new InvalidUserDataException("You must specify userDescription");
        }
        DependencyResolver resolver = resolverFactory.createResolver(userDescription);
        ConfigureUtil.configure(configureClosure, resolver);
        if (!GUtil.isTrue(resolver.getName())) {
            throw new InvalidUserDataException("You must specify a name for the resolver. Resolver=" + userDescription);
        }
        if (findByName(resolver.getName()) != null) {
            throw new InvalidUserDataException(String.format(
                    "Cannot add a resolver with name '%s' as a resolver with that name already exists.", resolver.getName()));
        }
        addObject(resolver.getName(), resolver);
        orderAction.apply(resolver.getName());
        return resolver;
    }

    @Override
    protected UnknownDomainObjectException createNotFoundException(String name) {
        return new UnknownRepositoryException(String.format("Repository with name '%s' not found.", name));
    }

    public List<DependencyResolver> getResolvers() {
        List<DependencyResolver> returnedResolvers = new ArrayList<DependencyResolver>();
        for (String resolverName : resolverNames) {
            returnedResolvers.add(getByName(resolverName));
        }
        return returnedResolvers;
    }

    private static interface OrderAction {
        
        void apply(String resolverName);
    }

    public ResolverFactory getResolverFactory() {
        return resolverFactory;
    }

    public void setResolverFactory(ResolverFactory resolverFactory) {
        this.resolverFactory = resolverFactory;
    }

    public List<String> getResolverNames() {
        return resolverNames;
    }

    public void setResolverNames(List<String> resolverNames) {
        this.resolverNames = resolverNames;
    }

    public FileResolver getFileResolver() {
        return fileResolver;
    }

    public void setFileResolver(FileResolver fileResolver) {
        this.fileResolver = fileResolver;
    }

    public ConfigurationContainer getConfigurationContainer() {
        return configurationContainer;
    }

    public void setConfigurationContainer(ConfigurationContainer configurationContainer) {
        this.configurationContainer = configurationContainer;
    }

    public GroovyMavenDeployer createMavenDeployer(String name) {
        return resolverFactory.createMavenDeployer(name, this, getConfigurationContainer(), getMavenScopeMappings(), getFileResolver());
    }

    public MavenResolver createMavenInstaller(String name) {
        return resolverFactory.createMavenInstaller(name, this, getConfigurationContainer(), getMavenScopeMappings(), getFileResolver());
    }

    public Conf2ScopeMappingContainer getMavenScopeMappings() {
        return mavenScopeMappings;
    }

    public void setMavenScopeMappings(Conf2ScopeMappingContainer mavenScopeMappings) {
        this.mavenScopeMappings = mavenScopeMappings;
    }

    public File getMavenPomDir() {
        return mavenPomDir;
    }

    public void setMavenPomDir(File mavenPomDir) {
        this.mavenPomDir = mavenPomDir;
    }
}
