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

package org.gradle.api.artifacts;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Named;
import org.gradle.api.attributes.HasConfigurableAttributes;
import org.gradle.internal.HasInternalProtocol;

import java.util.Optional;

/**
 * Represents some variant of an outgoing configuration.
 *
 * @since 3.3
 */
@HasInternalProtocol
public interface ConfigurationVariant extends Named, HasConfigurableAttributes<ConfigurationVariant> {
    /**
     * Returns an optional note describing this variant.
     *
     * @since 7.5
     */
    @Incubating
    Optional<String> getDescription();

    /**
     * Returns the artifacts associated with this variant.
     */
    PublishArtifactSet getArtifacts();

    /**
     * Adds an artifact to this variant.
     *
     * <p>See {@link org.gradle.api.artifacts.dsl.ArtifactHandler} for details of the supported notations.
     */
    void artifact(Object notation);

    /**
     * Adds an artifact to this variant, configuring it using the given action.
     *
     * <p>See {@link org.gradle.api.artifacts.dsl.ArtifactHandler} for details of the supported notations.
     */
    void artifact(Object notation, Action<? super ConfigurablePublishArtifact> configureAction);
}
