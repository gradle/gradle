/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.api.Incubating;

import javax.annotation.Nullable;

/**
 * Details about an artifact selection in the context of a dependency substitution.
 *
 * Artifact selections are handy as a migration path from the Maven or Ivy ecosystem,
 * where different "variants" are actually represented as different artifacts, with
 * specific (type, extension, classifier) sub-coordinates, in addition to the GAV
 * (group, artifact, version) coordinates.
 *
 * It is preferable to use component metadata rules to properly describe the variants
 * of a module, so this variant selector should only be used when defining such rules
 * is not possible or too complex for the use case.
 *
 * @since 6.6
 */
@Incubating
public interface DependencyArtifactSelector {
    /**
     * Returns the type of the artifact to select
     */
    String getType();

    /**
     * Returns the extension of the artifact to select. If it returns null, it will fallback to jar.
     */
    @Nullable
    String getExtension();

    /**
     * Returns the classifier of the artifact to select.
     */
    @Nullable
    String getClassifier();
}
