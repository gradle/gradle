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

package org.gradle.api.artifacts.type;

import org.gradle.api.NamedDomainObjectContainer;

/**
 * Defines a set of known artifact types and related meta-data. This allows you to fine tune how dependency resolution handles artifacts of a specific type.
 *
 * Each entry in this container defines a particular artifact type, such as a JAR or an AAR, and some information about that artifact type.
 *
 * @since 4.0
 */
public interface ArtifactTypeContainer extends NamedDomainObjectContainer<ArtifactTypeDefinition> {
}
