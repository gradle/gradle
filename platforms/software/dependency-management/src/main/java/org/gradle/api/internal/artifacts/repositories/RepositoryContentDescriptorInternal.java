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
import org.jspecify.annotations.Nullable;

import java.util.List;
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

    /**
     * Returns user-facing descriptions of include rules declared on this descriptor,
     * for use by diagnostic reporting.
     *
     * <p>Each entry is a human-readable form such as
     * {@code includeGroup("com.example")} or
     * {@code includeVersion("g", "m", "1.0")}. Entries preserve declaration order.
     *
     * <p>Note: {@code includeModule(group, module)} and {@code includeModuleByRegex(groupRegex, moduleRegex)}
     * store identically internally and are both rendered as {@code includeModuleByRegex(...)}.
     *
     * @return immutable list of rule descriptions, empty when none
     */
    List<String> describeIncludeRules();

    /**
     * Returns user-facing descriptions of exclude rules declared on this descriptor,
     * for use by diagnostic reporting.
     *
     * @return immutable list of rule descriptions, empty when none
     */
    List<String> describeExcludeRules();
}
