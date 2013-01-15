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
import org.gradle.api.NamedDomainObjectSet;

/**
 * A {@code PublicationContainer} is responsible for declaring and managing publications.
 *
 * Publications cannot be added to a publication container by users at this time. Publication plugins
 * are responsible for creating {@link Publication} instances in the container.
 *
 * See the documentation for the Ivy Publishing plugin for more information.
 *
 * @since 1.3
 * @see Publication
 */
@Incubating
public interface PublicationContainer extends NamedDomainObjectSet<Publication> {

    /**
     * {@inheritDoc}
     */
    Publication getByName(String name) throws UnknownPublicationException;

    /**
     * {@inheritDoc}
     */
    Publication getAt(String name) throws UnknownPublicationException;

    /**
     * Adds a publication of the specified type.
     * @param name The publication name.
     * @param type The publication type.
     * @return The added publication
     */
    <T extends Publication> T add(String name, Class<T> type);

    /**
     * Adds a publication of the specified type.
     * @param name The publication name.
     * @param type The publication type.
     * @param configuration The action to configure the publication.
     * @return The added publication
     */
    <T extends Publication> T add(String name, Class<T> type, Action<T> configuration);
}
