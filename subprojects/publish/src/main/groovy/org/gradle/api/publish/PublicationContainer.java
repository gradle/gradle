/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.publish;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectSet;

/**
 * A {@code PublicationContainer} is responsible for creating and managing {@link Publication} instances.
 *
 * The set of available publication types is dependent on the application of particular plugins:
 * <ul>
 *     <li>The {@link org.gradle.api.publish.maven.plugins.MavenPublishPlugin} makes it possible to create {@link org.gradle.api.publish.maven.MavenPublication} instances.</li>
 *     <li>The {@link org.gradle.api.publish.ivy.plugins.IvyPublishPlugin} makes it possible to create {@link org.gradle.api.publish.ivy.IvyPublication} instances.</li>
 * </ul>
 *
 * See the documentation for {@link PublishingExtension#publications(org.gradle.api.Action)} for more examples of how to create and configure publications.
 *
 * @since 1.3
 * @see Publication
 * @see PublishingExtension
 */
@Incubating
public interface PublicationContainer extends NamedDomainObjectSet<Publication> {

    /**
     * Creates a publication with the specified name and type, adding it to the container.
     *
     * <pre autoTested="true">
     * apply plugin: 'maven-publish'
     *
     * publishing.publications.add('publication-name', MavenPublication)
     * </pre>
     *
     * @param name The publication name.
     * @param type The publication type.
     * @return The added publication
     * @throws InvalidUserDataException If type is not a valid publication type, or if a publication named "name" already exists.
     */
    <T extends Publication> T add(String name, Class<T> type) throws InvalidUserDataException;

    /**
     * Creates a publication with the specified name and type, adding it to the container and configuring it with the supplied action.
     * A {@link groovy.lang.Closure} can be supplied in place of an action, through type coercion.
     *
     * <pre autoTested="true">
     * apply plugin: 'ivy-publish'
     *
     * publishing.publications.add('publication-name', IvyPublication) {
     *     // Configure the ivy publication here
     * }
     * </pre>
     *
     * @param name The publication name.
     * @param type The publication type.
     * @param configuration The action or closure to configure the publication with.
     * @return The added publication
     * @throws InvalidUserDataException If type is not a valid publication type, or if a publication named "name" already exists.
     */
    <T extends Publication> T add(String name, Class<T> type, Action<? super T> configuration) throws InvalidUserDataException;
}
