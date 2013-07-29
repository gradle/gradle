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
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectList;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.util.Configurable;

import java.util.List;

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
    String MAVEN_CENTRAL_URL = "http://repo1.maven.org/maven2/";
    @Deprecated
    String MAVEN_REPO_PATTERN = "[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier]).[ext]";
    @Deprecated
    String DEFAULT_CACHE_ARTIFACT_PATTERN
            = "[organisation]/[module](/[branch])/[type]s/[artifact]-[revision](-[classifier])(.[ext])";
    @Deprecated
    String DEFAULT_CACHE_IVY_PATTERN = "[organisation]/[module](/[branch])/ivy-[revision].xml";
    @Deprecated
    String INTERNAL_REPOSITORY_NAME = "internal-repository";
    @Deprecated
    String RESOLVER_NAME = "name";
    @Deprecated
    String RESOLVER_URL = "url";

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
     * Adds a repository to this container, at the end of the repository sequence.
     *
     * @param resolver The repository to add, represented as an Ivy {@link DependencyResolver}.
     * @deprecated Use one of the repository methods on {@link org.gradle.api.artifacts.dsl.RepositoryHandler} or {@link #add(ArtifactRepository)} instead.
     */
    @Deprecated
    boolean add(DependencyResolver resolver);

    /**
     * Adds a repository to this container, at the end of the repository sequence.
     *
     * @param resolver The repository to add, represented as an Ivy {@link DependencyResolver}.
     * @param configureClosure The closure to use to configure the repository.
     * @deprecated Use one of the repository methods on {@link org.gradle.api.artifacts.dsl.RepositoryHandler} or {@link #add(ArtifactRepository)} instead.
     */
    @Deprecated
    boolean add(DependencyResolver resolver, Closure configureClosure);

    /**
     * Adds a repository to this container, at the end of the repository sequence. The given {@code userDescription} can be
     * one of:
     *
     * <ul>
     *
     * <li>A String. This is treated as a URL, and used to create a Maven repository.</li>
     *
     * <li>A map. This is used to create a Maven repository. The map must contain an {@value #RESOLVER_NAME} entry and a
     * {@value #RESOLVER_URL} entry.</li>
     *
     * <li>A {@link DependencyResolver}.</li>
     *
     * <li>A {@link ArtifactRepository}.</li>
     *
     * </ul>
     *
     * @param userDescription The resolver definition.
     * @return The added resolver.
     * @throws InvalidUserDataException when a resolver with the given name already exists in this container.
     * @deprecated Use {@link org.gradle.api.artifacts.dsl.RepositoryHandler#maven(groovy.lang.Closure)} or {@link #add(ArtifactRepository)} instead.
     */
    @Deprecated
    DependencyResolver addLast(Object userDescription) throws InvalidUserDataException;

    /**
     * Adds a resolver to this container, at the end of the resolver sequence. The resolver is configured using the
     * given configure closure.
     *
     * @param userDescription The resolver definition. See {@link #addLast(Object)} for details of this parameter.
     * @param configureClosure The closure to use to configure the resolver.
     * @return The added resolver.
     * @throws InvalidUserDataException when a resolver with the given name already exists in this container.
     * @deprecated Use {@link org.gradle.api.artifacts.dsl.RepositoryHandler#maven(groovy.lang.Closure)} or {@link #add(ArtifactRepository)} instead.
     */
    @Deprecated
    DependencyResolver addLast(Object userDescription, Closure configureClosure) throws InvalidUserDataException;

    /**
     * Adds a resolver to this container, before the given resolver.
     *
     * @param userDescription The resolver definition. See {@link #addLast(Object)} for details of this parameter.
     * @param nextResolver The existing resolver to add the new resolver before.
     * @return The added resolver.
     * @throws InvalidUserDataException when a resolver with the given name already exists in this container.
     * @throws UnknownRepositoryException when the given next resolver does not exist in this container.
     * @deprecated No replacement
     */
    @Deprecated
    DependencyResolver addBefore(Object userDescription, String nextResolver) throws InvalidUserDataException;

    /**
     * Adds a resolver to this container, before the given resolver. The resolver is configured using the given
     * configure closure.
     *
     * @param userDescription The resolver definition. See {@link #addLast(Object)} for details of this parameter.
     * @param nextResolver The existing resolver to add the new resolver before.
     * @param configureClosure The closure to use to configure the resolver.
     * @return The added resolver.
     * @throws InvalidUserDataException when a resolver with the given name already exists in this container.
     * @throws UnknownRepositoryException when the given next resolver does not exist in this container.
     * @deprecated No replacement
     */
    @Deprecated
    DependencyResolver addBefore(Object userDescription, String nextResolver, Closure configureClosure)
            throws InvalidUserDataException;

    /**
     * Adds a resolver to this container, after the given resolver.
     *
     * @param userDescription The resolver definition. See {@link #addLast(Object)} for details of this parameter.
     * @param previousResolver The existing resolver to add the new resolver after.
     * @return The added resolver.
     * @throws InvalidUserDataException when a resolver with the given name already exists in this container.
     * @throws UnknownRepositoryException when the given previous resolver does not exist in this container.
     * @deprecated No replacement
     */
    @Deprecated
    DependencyResolver addAfter(Object userDescription, String previousResolver) throws InvalidUserDataException;

    /**
     * Adds a resolver to this container, after the given resolver. The resolver is configured using the given configure
     * closure.
     *
     * @param userDescription The resolver definition. See {@link #addLast(Object)} for details of this parameter.
     * @param previousResolver The existing resolver to add the new resolver after.
     * @param configureClosure The closure to use to configure the resolver.
     * @return The added resolver.
     * @throws InvalidUserDataException when a resolver with the given name already exists in this container.
     * @throws UnknownRepositoryException when the given previous resolver does not exist in this container.
     * @deprecated No replacement
     */
    @Deprecated
    DependencyResolver addAfter(Object userDescription, String previousResolver, Closure configureClosure)
            throws InvalidUserDataException;

    /**
     * Adds a resolver to this container, at the start of the resolver sequence.
     *
     * @param userDescription The resolver definition. See {@link #addLast(Object)} for details of this parameter.
     * @return The added resolver.
     * @throws InvalidUserDataException when a resolver with the given name already exists in this container.
     * @deprecated Use {@link #addFirst(ArtifactRepository)} instead.
     */
    @Deprecated
    DependencyResolver addFirst(Object userDescription) throws InvalidUserDataException;

    /**
     * Adds a resolver to this container, at the start of the resolver sequence. The resolver is configured using the
     * given configure closure.
     *
     * @param userDescription The resolver definition. See {@link #addLast(Object)} for details of this parameter.
     * @param configureClosure The closure to use to configure the resolver.
     * @return The added resolver.
     * @throws InvalidUserDataException when a resolver with the given name already exists in this container.
     * @deprecated Use {@link #addFirst(ArtifactRepository)} instead.
     */
    @Deprecated
    DependencyResolver addFirst(Object userDescription, Closure configureClosure) throws InvalidUserDataException;

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
    ArtifactRepository getAt(String name) throws UnknownRepositoryException;

    /**
     * Returns the resolvers in this container, in sequence.
     *
     * @return The resolvers in sequence. Returns an empty list if this container is empty.
     * @deprecated No replacement.
     */
    @Deprecated
    List<DependencyResolver> getResolvers();
}
