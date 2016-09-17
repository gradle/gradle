/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.artifacts;

import groovy.lang.Closure;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectList;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.util.Configurable;

/**
 * <p>A {@code ResolverContainer} is responsible for managing a set of {@link ArtifactRepository} instances. Repositories are arranged in a sequence.</p>
 *
 * <p>You can obtain a {@code ResolverContainer} instance by calling {@link org.gradle.api.Project#getRepositories()} or
 * using the {@code repositories} property in your build script.</p>
 *
 * <p>The resolvers in a container are accessible as read-only properties of the container, using the name of the
 * resolver as the property name. For example:</p>
 *
 * <pre autoTested=''>
 * repositories.maven { name 'myResolver' }
 * repositories.myResolver.url = 'some-url'
 * </pre>
 *
 * <p>A dynamic method is added for each resolver which takes a configuration closure. This is equivalent to calling
 * {@link #getByName(String, groovy.lang.Closure)}. For example:</p>
 *
 * <pre autoTested=''>
 * repositories.maven { name 'myResolver' }
 * repositories.myResolver {
 *     url 'some-url'
 * }
 * </pre>
 */
public interface ArtifactRepositoryContainer extends NamedDomainObjectList<ArtifactRepository>, Configurable<ArtifactRepositoryContainer> {
    String DEFAULT_MAVEN_CENTRAL_REPO_NAME = "MavenRepo";
    String DEFAULT_MAVEN_LOCAL_REPO_NAME = "MavenLocal";
    String MAVEN_CENTRAL_URL = "https://repo1.maven.org/maven2/";

    /**
     * Adds a repository to this container, at the end of the repository sequence.
     *
     * @param repository The repository to add.
     */
    boolean add(ArtifactRepository repository);

    /**
     * Adds a repository to this container, at the start of the repository sequence.
     *
     * @param repository The repository to add.
     */
    void addFirst(ArtifactRepository repository);

    /**
     * Adds a repository to this container, at the end of the repository sequence.
     *
     * @param repository The repository to add.
     */
    void addLast(ArtifactRepository repository);

    /**
     * {@inheritDoc}
     */
    ArtifactRepository getByName(String name) throws UnknownRepositoryException;

    /**
     * {@inheritDoc}
     */
    ArtifactRepository getByName(String name, Closure configureClosure) throws UnknownRepositoryException;

    /**
     * {@inheritDoc}
     */
    ArtifactRepository getByName(String name, Action<? super ArtifactRepository> configureAction) throws UnknownRepositoryException;

    /**
     * {@inheritDoc}
     */
    ArtifactRepository getAt(String name) throws UnknownRepositoryException;
}
