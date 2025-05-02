/*
 * Copyright 2017 the original author or authors.
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

/**
 * Describes the dependencies of a variant declared in a resolved component's metadata, which typically originate from
 * a component descriptor (Gradle metadata file, Ivy file, Maven POM). This interface can be used to adjust the dependencies
 * of a published component via metadata rules (see {@link org.gradle.api.artifacts.dsl.ComponentMetadataHandler}.
 *
 * @since 4.5
 */
public interface DirectDependenciesMetadata extends DependenciesMetadata<DirectDependencyMetadata> {

}
