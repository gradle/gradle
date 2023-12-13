/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories;

import org.gradle.api.Action;
import org.gradle.api.artifacts.repositories.RepositoryContentDescriptor;
import org.gradle.api.attributes.Attribute;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;

public interface RepositoryContentDescriptorInternal extends RepositoryContentDescriptor {
    Action<? super ArtifactResolutionDetails> toContentFilter();
    RepositoryContentDescriptorInternal asMutableCopy();

    /**
     * Returns values added by {@link #onlyForConfigurations}.
     */
    @Nullable
    Set<String> getIncludedConfigurations();

    /**
     * Returns values added by {@link #notForConfigurations}
     */
    @Nullable
    Set<String> getExcludedConfigurations();

    /**
     * Returns value added by {@link #onlyForAttribute}
     */
    @Nullable
    Map<Attribute<Object>, Set<Object>> getRequiredAttributes();
}
