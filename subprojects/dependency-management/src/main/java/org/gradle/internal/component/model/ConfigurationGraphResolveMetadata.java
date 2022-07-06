/*
 * Copyright 2022 the original author or authors.
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

import com.google.common.collect.ImmutableSet;
import org.gradle.internal.deprecation.DeprecationMessageBuilder;

import javax.annotation.Nullable;

/**
 * Immutable metadata for a configuration of a component instance, which is used to perform dependency graph resolution.
 */
public interface ConfigurationGraphResolveMetadata extends VariantGraphResolveMetadata {
    ImmutableSet<String> getHierarchy();

    boolean isCanBeConsumed();

    boolean isVisible();

    @Nullable
    DeprecationMessageBuilder.WithDocumentation getConsumptionDeprecation();
}
