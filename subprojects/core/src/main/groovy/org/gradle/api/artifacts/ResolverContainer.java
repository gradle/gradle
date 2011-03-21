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
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectCollection;
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer;

import java.io.File;
import java.util.List;

/**
 * <p>A {@code ResolverContainer} is responsible for managing a set of {@link org.apache.ivy.plugins.resolver.DependencyResolver}
 * instances. Resolvers are arranged in a sequence.</p>
 *
 * <p>You can obtain a {@code ResolverContainer} instance by calling {@link org.gradle.api.Project#getRepositories()} or
 * using the {@code repositories} property in your build script.</p>
 *
 * <p>The resolvers in a container are accessable as read-only properties of the container, using the name of the
 * resolver as the property name. For example:</p>
 *
 * <pre>
 * resolvers.add('myResolver')
 * resolvers.myResolver.addArtifactPattern(somePattern)
 * </pre>
 *
 * <p>A dynamic method is added for each resolver which takes a configuration closure. This is equivalent to calling
 * {@link #getByName(String, groovy.lang.Closure)}. For example:</p>
 *
 * <pre>
 * resolvers.add('myResolver')
 * resolvers.myResolver {
 *     addArtifactPattern(somePattern)
 * }
 * </pre>
 *
 * @author Hans Dockter
 */
public interface ResolverContainer extends NamedDomainObjectContainer<DependencyResolver>, NamedDomainObjectCollection<DependencyResolver> {
    String DEFAULT_MAVEN_CENTRAL_REPO_NAME = "MavenRepo";
    String DEFAULT_MAVEN_LOCAL_REPO_NAME = "MavenLocal";
    String MAVEN_CENTRAL_URL = "http://repo1.maven.org/maven2/";
    String MAVEN_REPO_PATTERN = "[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier]).[ext]";
    String DEFAULT_CACHE_ARTIFACT_PATTERN
            = "[organisation]/[module](/[branch])/[type]s/[artifact]-[revision](-[classifier])(.[ext])";
    String DEFAULT_CACHE_IVY_PATTERN = "[organisation]/[module](/[branch])/ivy-[revision].xml";
    String INTERNAL_REPOSITORY_NAME = "internal-repository";
    String DEFAULT_CACHE_DIR_NAME = "cache";
    String RESOLVER_NAME = "name";
    String RESOLVER_URL = "url";

    /**
     * Adds a resolver to this container, at the end of the resolver sequence. The given {@code userDescription} can be
     * one of:
     *
     * <ul>
     *
     * <li>A String. This is treated as a URL, and used to create a maven resolver.</li>
     *
     * <li>A map. This is used to create a maven resolver. The map must contain an {@value #RESOLVER_NAME} entry and a
     * {@value #RESOLVER_URL} entry.</li>
     *
     * <li>A {@link org.apache.ivy.plugins.resolver.DependencyResolver}.</li>
     *
     * </ul>
     *
     * @param userDescription The resolver definition.
     * @return The added resolver.
     * @throws InvalidUserDataException when a resolver with the given name already exists in this container.
     */
    DependencyResolver add(Object userDescription) throws InvalidUserDataException;

    /**
     * Adds a resolver to this container, at the end of the resolver sequence. The resolver is configured using the
     * given configure closure.
     *
     * @param userDescription The resolver definition. See {@link #add(Object)} for details of this parameter.
     * @param configureClosure The closure to use to configure the resolver.
     * @return The added resolver.
     * @throws InvalidUserDataException when a resolver with the given name already exists in this container.
     */
    DependencyResolver add(Object userDescription, Closure configureClosure) throws InvalidUserDataException;

    /**
     * Adds a resolver to this container, before the given resolver.
     *
     * @param userDescription The resolver definition. See {@link #add(Object)} for details of this parameter.
     * @param nextResolver The existing resolver to add the new resolver before.
     * @return The added resolver.
     * @throws InvalidUserDataException when a resolver with the given name already exists in this container.
     * @throws InvalidUserDataException when the given next resolver does not exist in this container.
     */
    DependencyResolver addBefore(Object userDescription, String nextResolver) throws InvalidUserDataException;

    /**
     * Adds a resolver to this container, before the given resolver. The resolver is configured using the given
     * configure closure.
     *
     * @param userDescription The resolver definition. See {@link #add(Object)} for details of this parameter.
     * @param nextResolver The existing resolver to add the new resolver before.
     * @param configureClosure The closure to use to configure the resolver.
     * @return The added resolver.
     * @throws InvalidUserDataException when a resolver with the given name already exists in this container.
     * @throws InvalidUserDataException when the given next resolver does not exist in this container.
     */
    DependencyResolver addBefore(Object userDescription, String nextResolver, Closure configureClosure)
            throws InvalidUserDataException;

    /**
     * Adds a resolver to this container, after the given resolver.
     *
     * @param userDescription The resolver definition. See {@link #add(Object)} for details of this parameter.
     * @param previousResolver The existing resolver to add the new resolver after.
     * @return The added resolver.
     * @throws InvalidUserDataException when a resolver with the given name already exists in this container.
     * @throws InvalidUserDataException when the given previous resolver does not exist in this container.
     */
    DependencyResolver addAfter(Object userDescription, String previousResolver) throws InvalidUserDataException;

    /**
     * Adds a resolver to this container, after the given resolver. The resolver is configured using the given configure
     * closure.
     *
     * @param userDescription The resolver definition. See {@link #add(Object)} for details of this parameter.
     * @param previousResolver The existing resolver to add the new resolver after.
     * @param configureClosure The closure to use to configure the resolver.
     * @return The added resolver.
     * @throws InvalidUserDataException when a resolver with the given name already exists in this container.
     * @throws InvalidUserDataException when the given previous resolver does not exist in this container.
     */
    DependencyResolver addAfter(Object userDescription, String previousResolver, Closure configureClosure)
            throws InvalidUserDataException;

    /**
     * Adds a resolver to this container, at the start of the resolver sequence.
     *
     * @param userDescription The resolver definition. See {@link #add(Object)} for details of this parameter.
     * @return The added resolver.
     * @throws InvalidUserDataException when a resolver with the given name already exists in this container.
     */
    DependencyResolver addFirst(Object userDescription) throws InvalidUserDataException;

    /**
     * Adds a resolver to this container, at the start of the resolver sequence. The resolver is configured using the
     * given configure closure.
     *
     * @param userDescription The resolver definition. See {@link #add(Object)} for details of this parameter.
     * @param configureClosure The closure to use to configure the resolver.
     * @return The added resolver.
     * @throws InvalidUserDataException when a resolver with the given name already exists in this container.
     */
    DependencyResolver addFirst(Object userDescription, Closure configureClosure) throws InvalidUserDataException;

    /**
     * {@inheritDoc}
     */
    DependencyResolver getByName(String name) throws UnknownRepositoryException;

    /**
     * {@inheritDoc}
     */
    DependencyResolver getByName(String name, Closure configureClosure) throws UnknownRepositoryException;

    /**
     * {@inheritDoc}
     */
    DependencyResolver getAt(String name) throws UnknownRepositoryException;

    /**
     * Returns the resolvers in this container, in sequence.
     *
     * @return The resolvers in sequence. Returns an empty list if this container is empty.
     */
    List<DependencyResolver> getResolvers();

    void setMavenPomDir(File mavenPomDir);

    Conf2ScopeMappingContainer getMavenScopeMappings();

    File getMavenPomDir();
}
