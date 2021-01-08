/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.publish.ivy;

import org.gradle.api.NamedDomainObjectContainer;

/**
 * The set of {@link IvyConfiguration}s that will be included in the {@link IvyPublication}.
 *
 * Being a {@link org.gradle.api.NamedDomainObjectContainer}, a {@code IvyConfigurationContainer} provides
 * convenient methods for adding, querying, filtering, and applying actions to the set of {@link IvyConfiguration}s.
 *
 * <pre class='autoTested'>
 * plugins {
 *     id 'ivy-publish'
 * }
 *
 * def publication = publishing.publications.create("my-pub", IvyPublication)
 * def configurations = publication.configurations
 *
 * configurations.create("extended", { extend "default"})
 * configurations.all {
 *     extend "base"
 * }
 * </pre>
 */
public interface IvyConfigurationContainer extends NamedDomainObjectContainer<IvyConfiguration> {
}
