/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.api.artifacts.result;

import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;

import java.io.File;
import java.util.Collection;

/**
 * The result of successfully resolving an artifact.
 *
 * @since 2.0
 */
public interface ResolvedArtifactResult extends ArtifactResult {

    /**
     * The file for the artifact.
     */
    File getFile();

    /**
     * The attributes that describe this artifact.
     *
     * @since 9.7.0
     */
    AttributeContainer getAttributes();

    /**
     * The capabilities that describe this artifact.
     *
     * @since 9.7.0
     */
    Collection<? extends Capability> getCapabilities();

    /**
     * The variant that included this artifact.
     * <p>
     * Prefer {@link #getAttributes()} and {@link #getCapabilities()} instead.
     * This method will be deprecated for removal in a future release.
     */
    ResolvedVariantResult getVariant();

}
