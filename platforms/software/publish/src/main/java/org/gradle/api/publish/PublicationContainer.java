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

import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer;

/**
 * A {@code PublicationContainer} is responsible for creating and managing {@link Publication} instances.
 *
 * The set of available publication types is dependent on the application of particular plugins:
 * <ul>
 *     <li>The {@link org.gradle.api.publish.maven.plugins.MavenPublishPlugin} makes it possible to create {@link org.gradle.api.publish.maven.MavenPublication} instances.</li>
 *     <li>The {@link org.gradle.api.publish.ivy.plugins.IvyPublishPlugin} makes it possible to create {@link org.gradle.api.publish.ivy.IvyPublication} instances.</li>
 * </ul>
 *
 *
 * <pre class='autoTested'>
 * plugins {
 *     id 'ivy-publish'
 * }
 *
 * publishing.publications.create('publication-name', IvyPublication) {
 *     // Configure the ivy publication here
 * }
 * </pre>
 *
 * The usual way to add publications is via a configuration block.
 * See the documentation for {@link PublishingExtension#publications(org.gradle.api.Action)} for examples of how to create and configure publications.
 *
 * @since 1.3
 * @see Publication
 * @see PublishingExtension
 */
@SuppressWarnings("JavadocReference")
public interface PublicationContainer extends ExtensiblePolymorphicDomainObjectContainer<Publication> {
}
