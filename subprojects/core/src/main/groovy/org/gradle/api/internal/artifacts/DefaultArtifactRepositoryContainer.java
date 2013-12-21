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
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Namer;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.artifacts.ArtifactRepositoryContainer;
import org.gradle.api.artifacts.UnknownRepositoryException;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.internal.Actions;
import org.gradle.api.internal.DefaultNamedDomainObjectList;
import org.gradle.api.internal.artifacts.repositories.ArtifactRepositoryInternal;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.DeprecationLogger;
import org.gradle.util.GUtil;

import java.util.ArrayList;
import java.util.List;

import static org.gradle.internal.Cast.cast;

public class DefaultArtifactRepositoryContainer extends DefaultNamedDomainObjectList<ArtifactRepository>
        implements ArtifactRepositoryContainer {

    private final BaseRepositoryFactory baseRepositoryFactory;
    private final Action<ArtifactRepository> addLastAction = new Action<ArtifactRepository>() {
        public void execute(ArtifactRepository repository) {
            DefaultArtifactRepositoryContainer.super.add(repository);
        }
    };
    private final Action<ArtifactRepository> addFirstAction = new Action<ArtifactRepository>() {
        public void execute(ArtifactRepository repository) {
            DefaultArtifactRepositoryContainer.super.add(0, repository);
        }
    };

    public DefaultArtifactRepositoryContainer(BaseRepositoryFactory baseRepositoryFactory, Instantiator instantiator) {
        super(ArtifactRepository.class, instantiator, new RepositoryNamer());
        this.baseRepositoryFactory = baseRepositoryFactory;
    }

    private static class RepositoryNamer implements Namer<ArtifactRepository> {
        public String determineName(ArtifactRepository r) {
            return r.getName();
        }
    }

    @Override
    public String getTypeDisplayName() {
        return "repository";
    }

    public DefaultArtifactRepositoryContainer configure(Closure closure) {
        return ConfigureUtil.configure(closure, this, false);
    }

    public void addFirst(ArtifactRepository repository) {
        add(0, repository);
    }

    public void addLast(ArtifactRepository repository) {
        add(repository);
    }

    public boolean add(DependencyResolver resolver, Closure configureClosure) {
        DeprecationLogger.nagUserOfDiscontinuedMethod("ArtifactRepositoryContainer.add(DependencyResolver, Closure)");
        addCustomDependencyResolver(resolver, configureClosure, addLastAction);
        return true;
    }

    public boolean add(DependencyResolver resolver) {
        DeprecationLogger.nagUserOfDiscontinuedMethod("ArtifactRepositoryContainer.add(DependencyResolver)");
        addCustomDependencyResolver(resolver, null, addLastAction);
        return true;
    }

    public DependencyResolver addFirst(Object userDescription) {
        return addFirst(userDescription, null);
    }

    public DependencyResolver addFirst(Object userDescription, Closure configureClosure) {
        DeprecationLogger.nagUserOfReplacedMethod("ArtifactRepositoryContainer.addFirst(Object)", "addFirst(ArtifactRepository");
        return addCustomDependencyResolver(userDescription, configureClosure, addFirstAction);
    }

    @Deprecated
    public DependencyResolver addLast(Object userDescription) {
        DeprecationLogger.nagUserOfReplacedMethod("ArtifactRepositoryContainer.addLast()", "maven() or add()");
        return addCustomDependencyResolver(userDescription, null, addLastAction);
    }

    @Deprecated
    public DependencyResolver addLast(Object userDescription, Closure configureClosure) {
        DeprecationLogger.nagUserOfReplacedMethod("ArtifactRepositoryContainer.addLast()", "maven() or add()");
        return addCustomDependencyResolver(userDescription, configureClosure, addLastAction);
    }

    public DependencyResolver addBefore(Object userDescription, String afterResolverName) {
        return addBefore(userDescription, afterResolverName, null);
    }

    public DependencyResolver addBefore(Object userDescription, final String afterResolverName, Closure configureClosure) {
        if (!GUtil.isTrue(afterResolverName)) {
            throw new InvalidUserDataException("You must specify afterResolverName");
        }
        DeprecationLogger.nagUserOfDiscontinuedMethod("ArtifactRepositoryContainer.addBefore()");
        final ArtifactRepository after = getByName(afterResolverName);
        return addCustomDependencyResolver(userDescription, configureClosure, new Action<ArtifactRepository>() {
            public void execute(ArtifactRepository repository) {
                DefaultArtifactRepositoryContainer.super.add(indexOf(after), repository);
            }
        });
    }

    public DependencyResolver addAfter(Object userDescription, final String beforeResolverName) {
        return addAfter(userDescription, beforeResolverName, null);
    }

    public DependencyResolver addAfter(Object userDescription, final String beforeResolverName, Closure configureClosure) {
        if (!GUtil.isTrue(beforeResolverName)) {
            throw new InvalidUserDataException("You must specify beforeResolverName");
        }
        DeprecationLogger.nagUserOfDiscontinuedMethod("ArtifactRepositoryContainer.addAfter()");
        final ArtifactRepository before = getByName(beforeResolverName);

        return addCustomDependencyResolver(userDescription, configureClosure, new Action<ArtifactRepository>() {
            public void execute(ArtifactRepository repository) {
                int insertPos = indexOf(before) + 1;
                if (insertPos == size()) {
                    DefaultArtifactRepositoryContainer.super.add(repository);
                } else {
                    DefaultArtifactRepositoryContainer.this.add(insertPos, repository);
                }
            }
        });
    }

    private DependencyResolver addCustomDependencyResolver(Object userDescription, Closure configureClosure, Action<ArtifactRepository> orderAction) {
        ArtifactRepository repository = baseRepositoryFactory.createRepository(userDescription);
        DependencyResolver resolver = baseRepositoryFactory.toResolver(repository);
        ConfigureUtil.configure(configureClosure, resolver);
        ArtifactRepository resolverRepository = baseRepositoryFactory.createResolverBackedRepository(resolver);
        addWithUniqueName(resolverRepository, "repository", orderAction);
        return resolver;
    }

    @Override
    protected UnknownDomainObjectException createNotFoundException(String name) {
        return new UnknownRepositoryException(String.format("Repository with name '%s' not found.", name));
    }

    public List<DependencyResolver> getResolvers() {
        DeprecationLogger.nagUserOfDiscontinuedMethod("ArtifactRepositoryContainer.getResolvers()");
        List<DependencyResolver> returnedResolvers = new ArrayList<DependencyResolver>();
        for (ArtifactRepository repository : this) {
            returnedResolvers.add(baseRepositoryFactory.toResolver(repository));
        }
        return returnedResolvers;
    }

    public <T extends ArtifactRepository> T addRepository(T repository, String defaultName) {
        return addRepository(repository, defaultName, Actions.doNothing());
    }

    public <T extends ArtifactRepository> T addRepository(T repository, String defaultName, Action<? super T> configureAction) {
        configureAction.execute(repository);
        return addWithUniqueName(repository, defaultName, addLastAction);
    }

    private <T extends ArtifactRepository> T addWithUniqueName(T repository, String defaultName, Action<? super T> insertion) {
        String repositoryName = repository.getName();
        if (!GUtil.isTrue(repositoryName)) {
            repository.setName(uniquifyName(defaultName));
        } else {
            repository.setName(uniquifyName(repositoryName));
        }

        assertCanAdd(repository.getName());
        insertion.execute(repository);
        cast(ArtifactRepositoryInternal.class, repository).onAddToContainer(this);
        return repository;
    }

    private String uniquifyName(String proposedName) {
        if (findByName(proposedName) == null) {
            return proposedName;
        }
        for (int index = 2; true; index++) {
            String candidate = String.format("%s%d", proposedName, index);
            if (findByName(candidate) == null) {
                return candidate;
            }
        }
    }

}
