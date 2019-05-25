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
import org.gradle.api.Action;
import org.gradle.api.Namer;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.artifacts.ArtifactRepositoryContainer;
import org.gradle.api.artifacts.UnknownRepositoryException;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.DefaultNamedDomainObjectList;
import org.gradle.api.internal.InternalAction;
import org.gradle.api.internal.artifacts.repositories.ArtifactRepositoryInternal;
import org.gradle.internal.Actions;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.GUtil;

public class DefaultArtifactRepositoryContainer extends DefaultNamedDomainObjectList<ArtifactRepository>
        implements ArtifactRepositoryContainer {

    private final Action<ArtifactRepository> addLastAction = new Action<ArtifactRepository>() {
        @Override
        public void execute(ArtifactRepository repository) {
            DefaultArtifactRepositoryContainer.super.add(repository);
        }
    };

    public DefaultArtifactRepositoryContainer(Instantiator instantiator, CollectionCallbackActionDecorator callbackActionDecorator) {
        super(ArtifactRepository.class, instantiator, new RepositoryNamer(), callbackActionDecorator);
        whenObjectAdded(new InternalAction<ArtifactRepository>() {
            @Override
            public void execute(ArtifactRepository artifactRepository) {
                if (artifactRepository instanceof ArtifactRepositoryInternal) {
                    ArtifactRepositoryInternal repository = (ArtifactRepositoryInternal) artifactRepository;
                    repository.onAddToContainer(DefaultArtifactRepositoryContainer.this);
                }
            }
        });
    }

    private static class RepositoryNamer implements Namer<ArtifactRepository> {
        @Override
        public String determineName(ArtifactRepository r) {
            return r.getName();
        }
    }

    @Override
    public String getTypeDisplayName() {
        return "repository";
    }

    @Override
    public DefaultArtifactRepositoryContainer configure(Closure closure) {
        return ConfigureUtil.configureSelf(closure, this);
    }

    @Override
    public void addFirst(ArtifactRepository repository) {
        add(0, repository);
    }

    @Override
    public void addLast(ArtifactRepository repository) {
        add(repository);
    }

    @Override
    protected UnknownDomainObjectException createNotFoundException(String name) {
        return new UnknownRepositoryException(String.format("Repository with name '%s' not found.", name));
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
        return repository;
    }

    private String uniquifyName(String proposedName) {
        if (findByName(proposedName) == null) {
            return proposedName;
        }
        for (int index = 2; true; index++) {
            String candidate = proposedName + index;
            if (findByName(candidate) == null) {
                return candidate;
            }
        }
    }

}
