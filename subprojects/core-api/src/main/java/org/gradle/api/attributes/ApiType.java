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

package org.gradle.api.attributes;

import org.gradle.api.Incubating;
import org.gradle.api.Named;

/**
 * Differentiates between the different APIs that a library exposes during compilation.
 *
 * <p>This attribute is only applicable when paired with the {@link Usage#JAVA_API} attribute.</p>
 *
 * @since 8.8
 */
@Incubating
public interface ApiType extends Named {

    /**
     * The attribute that tracks a variant's compilation API.
     *
     * @since 8.8
     */
    Attribute<ApiType> TYPE_ATTRIBUTE = Attribute.of("org.gradle.api-type", ApiType.class);

    /**
     * The public API of a library. This is a subset of the private API and is intended to expose
     * dependencies accessible to consumers under normal circumstances.
     *
     * @since 8.8
     */
    String PUBLIC = "public";

    /**
     * The private API of a library. It contains all dependencies required to compile the library itself
     * and exposes dependencies that are not exposed as part of the library's public API.
     *
     * @since 8.8
     */
    String PRIVATE = "private";
}
