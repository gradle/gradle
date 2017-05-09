/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.component.model;

import java.util.Set;

/**
 * Represents the variants associated with each configuration of a particular component. Implementations must be immutable.
 */
public interface ComponentArtifacts {
    /**
     * Returns the variants for the given configuration. The values that are returned are retained for the life of the current build, so should reference as little state as possible. Should also be immutable.
     */
    Set<? extends VariantMetadata> getVariantsFor(ConfigurationMetadata configuration);
}
