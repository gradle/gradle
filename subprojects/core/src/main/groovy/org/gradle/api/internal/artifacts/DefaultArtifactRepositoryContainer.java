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
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.ArtifactRepositoryContainer;
import org.gradle.api.artifacts.UnknownRepositoryException;
import org.gradle.api.internal.DefaultNamedDomainObjectList;
import org.gradle.api.internal.Instantiator;
import org.gradle.api.internal.artifacts.repositories.ArtifactRepositoryInternal;
import org.gradle.api.internal.artifacts.repositories.FixedResolverArtifactRepository;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.DeprecationLogger;
import org.gradle.util.GUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Hans Dockter
 */
public class DefaultArtifactRepositoryContainer extends DefaultNamedDomainObjectList<ArtifactRepository>
        implements ArtifactRepositoryContainer {
    private final ResolverFactory resolverFactory;

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

    public DefaultArtifactRepositoryContainer(ResolverFactory resolverFactory, Instantiator instantiator) {
        super(ArtifactRepository.class, instantiator, new RepositoryNamer());
        this.resolverFactory = resolverFactory;
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

    public boolean add(DependencyResolver resolver, Closure configureClosure) {
        addInternal(resolver, configureClosure, addLastAction);
        return true;
    }

    public boolean add(DependencyResolver resolver) {
        addInternal(resolver, null, addLastAction);
        return true;
    }

    public void addFirst(ArtifactRepository repository) {
        add(0, repository);
    }

    public void addLast(ArtifactRepository repository) {
        add(repository);
    }

    @Deprecated
    public DependencyResolver addLast(Object userDescription) {
        DeprecationLogger.nagUserOfReplacedMethod("ArtifactRepositoryContainer.addLast()", "maven() or add()");
        return addInternal(userDescription, null, addLastAction);
    }

    @Deprecated
    public DependencyResolver addLast(Object userDescription, Closure configureClosure) {
        DeprecationLogger.nagUserOfReplacedMethod("ArtifactRepositoryContainer.addLast()", "maven() or add()");
        return addInternal(userDescription, configureClosure, addLastAction);
    }

    public DependencyResolver addBefore(Object userDescription, String afterResolverName) {
        return addBefore(userDescription, afterResolverName, null);
    }

    public DependencyResolver addBefore(Object userDescription, final String afterResolverName, Closure configureClosure) {
        if (!GUtil.isTrue(afterResolverName)) {
            throw new InvalidUserDataException("You must specify afterResolverName");
        }
        final ArtifactRepository after = getByName(afterResolverName);
        return addInternal(userDescription, configureClosure, new Action<ArtifactRepository>() {
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
        final ArtifactRepository before = getByName(beforeResolverName);

        return addInternal(userDescription, configureClosure, new Action<ArtifactRepository>() {
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

    public DependencyResolver addFirst(Object userDescription) {
        return addFirst(userDescription, null);
    }

    public DependencyResolver addFirst(Object userDescription, Closure configureClosure) {
        return addInternal(userDescription, configureClosure, addFirstAction);
    }

    private DependencyResolver addInternal(Object userDescription, Closure configureClosure, Action<ArtifactRepository> orderAction) {
        ArtifactRepository repository;
        if (userDescription instanceof ArtifactRepository) {
            repository = (ArtifactRepository) userDescription;
        } else {
            repository = resolverFactory.createRepository(userDescription);
        }
        DependencyResolver resolver = toResolver(DependencyResolver.class, repository);
        ConfigureUtil.configure(configureClosure, resolver);
        addRepository(new FixedResolverArtifactRepository(resolver), "repository", orderAction);
        return resolver;
    }

    @Override
    protected UnknownDomainObjectException createNotFoundException(String name) {
        return new UnknownRepositoryException(String.format("Repository with name '%s' not found.", name));
    }

    public List<DependencyResolver> getResolvers() {
        List<DependencyResolver> returnedResolvers = new ArrayList<DependencyResolver>();
        for (ArtifactRepository repository : this) {
            ((ArtifactRepositoryInternal) repository).createResolvers(returnedResolvers);
        }
        return returnedResolvers;
    }

    public ResolverFactory getResolverFactory() {
        return resolverFactory;
    }

    protected <T extends ArtifactRepository> T addRepository(T repository, Action<? super T> action, String defaultName) {
        action.execute(repository);
        return addRepository(repository, defaultName);
    }

    public <T extends ArtifactRepository> T addRepository(T repository, Closure closure, String defaultName) {
        return addRepository(repository, closure, defaultName, addLastAction);
    }

    public <T extends ArtifactRepository> T addRepository(T repository, Map<String, ?> args, Closure closure, String defaultName) {
        ConfigureUtil.configureByMap(args, repository);
        return addRepository(repository, closure, defaultName);
    }

    protected <T extends ArtifactRepository> T addRepository(T repository, Closure closure, String defaultName, Action<ArtifactRepository> action) {
        ConfigureUtil.configure(closure, repository);
        return addRepository(repository, defaultName, action);
    }

    public <T extends ArtifactRepository> T addRepository(T repository, Map<String, ?> args, String defaultName) {
        ConfigureUtil.configureByMap(args, repository);
        addRepository(repository, defaultName);
        return repository;
    }

    public <T extends ArtifactRepository> T addRepository(T repository, String defaultName) {
        return addRepository(repository, defaultName, addLastAction);
    }

    protected <T extends ArtifactRepository> T addRepository(T repository, String defaultName, Action<ArtifactRepository> action) {
        String repositoryName = repository.getName();
        if (!GUtil.isTrue(repositoryName)) {
            repositoryName = findName(defaultName);
            repository.setName(repositoryName);
        }
        assertCanAdd(repositoryName);
        action.execute(repository);

        return repository;
    }

    protected String findName(String defaultName) {
        if (findByName(defaultName) == null) {
            return defaultName;
        }
        for (int index = 2; true; index++) {
            String candidate = String.format("%s%d", defaultName, index);
            if (findByName(candidate) == null) {
                return candidate;
            }
        }
    }

    protected <T extends DependencyResolver> T toResolver(Class<T> type, ArtifactRepository repository) {
        List<DependencyResolver> resolvers = new ArrayList<DependencyResolver>();
        ((ArtifactRepositoryInternal) repository).createResolvers(resolvers);
        assert resolvers.size() == 1;
        return type.cast(resolvers.get(0));
    }
}
