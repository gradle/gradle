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
package org.gradle.api.ecosystem;

import org.gradle.api.Incubating;
import org.gradle.api.Named;

import javax.annotation.Nullable;

/**
 * An ecosystem is used for consumers to discover that they need
 * a specific plugin to be applied in order for Gradle (and potentially
 * other build tools) to understand metadata of components.
 *
 * For example, the Java ecosystem configures the attribute schema in
 * such a way that we can disambiguate usages. Similarly, a plugin that
 * enhances the Gradle metadata format with custom attributes needs to
 * declare its ecosystem, so that when dependency resolution happens,
 * we can warn if no plugin providing the ecosystem support has been
 * applied.
 *
 * @since 5.3
 */
@Incubating
public interface Ecosystem extends Named {

    /**
     * A description for this ecosystem. The description will
     * appear in published metadata, and may appear in error
     * messages when dependency resolution fails.
     *
     * @return a human readable description of the ecosystem
     */
    @Nullable
    String getDescription();
}
