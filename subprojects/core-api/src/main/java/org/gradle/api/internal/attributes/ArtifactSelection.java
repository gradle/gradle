/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.attributes;

import org.gradle.api.attributes.Attribute;

/**
 * This attribute is used as a compatibility layer with Maven classifiers.
 * Whenever a build file declares a dependency with a classifier, when we
 * publish with Gradle metadata, the dependency will be attached with this
 * attribute.
 *
 * It <b>mustn't</b> be used directly by users, typically on dependency
 * declarations.
 */
public interface ArtifactSelection {
    Attribute<String> ARTIFACT_SELECTOR_ATTRIBUTE = Attribute.of("org.gradle.compat.artifactselector", String.class);
}
