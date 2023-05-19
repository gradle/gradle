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
package org.gradle.api.capabilities;

import javax.annotation.Nullable;

/**
 * A capability describes a feature of a component.
 *
 * <p>Conceptually, a capability represents the content or API exposed by a set of artifacts.
 * Capabilities are versioned since that API changes as the artifacts are updated.<p>
 *
 * <p>Each feature of a component, such as the production library or its test fixtures, exposes
 * similar variants, such as the runtime, api, or sources variants. Each corresponding variant in
 * a given feature of a component will have the same attributes, and therefore are distinguished
 * by their capabilities.<p>
 *
 * <p>For example, the runtime/api variants of a library will share a capability with the sources variant,
 * since they have the same content. However, their attributes differ since their artifacts
 * differ in form. Conversely, the runtime/api variants of a library and its test fixtures will have the same
 * attributes, but will have different capabilities since they expose different APIs.</p>
 *
 * <p>Multiple variants with the same capability may not exist in a dependency graph simultaneously.</p>
 *
 * @since 4.7
 */
public interface Capability {
    String getGroup();
    String getName();
    @Nullable
    String getVersion();
}
