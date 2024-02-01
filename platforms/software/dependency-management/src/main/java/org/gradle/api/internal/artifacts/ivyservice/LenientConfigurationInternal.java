/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice;

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.LenientConfiguration;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.SelectedArtifactSet;
import org.gradle.api.specs.Spec;

/**
 * Internal counterpart of {@link LenientConfiguration}.
 */
public interface LenientConfigurationInternal extends LenientConfiguration {

    /**
     * Selects artifacts non-leniently using the implicit selection spec, without filtering.
     */
    SelectedArtifactSet select();

    /**
     * Selects artifacts non-leniently using the implicit selection spec, with filtering by first-level dependency.
     */
    SelectedArtifactSet select(final Spec<? super Dependency> dependencySpec);
}
