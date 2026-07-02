/*
 * Copyright 2026 the original author or authors.
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
 * Marks a variant as a "fallback" — one that should be considered during variant
 * selection only when no non-fallback alternative is available.
 * <p>
 * Gradle applies this attribute automatically to the primary variant of any
 * {@code Configuration} that declares secondary variants but has no statically
 * declared artifacts on its primary. In that case, the primary is tagged with
 * value {@link #TRUE} and each secondary is tagged with {@link #FALSE}. A default
 * disambiguation rule then prefers {@link #FALSE} candidates over {@link #TRUE}
 * candidates so the secondaries win whenever a secondary is compatible with the
 * consumer's request.
 * <p>
 * Consumers can opt into the fallback by explicitly requesting the
 * {@link #FALLBACK_VARIANT_ATTRIBUTE} with value {@link #TRUE}.
 *
 * @since 9.7.0
 */
@Incubating
public interface FallbackVariant extends Named {
    /**
     * Identifies this attribute.
     *
     * @since 9.7.0
     */
    Attribute<FallbackVariant> FALLBACK_VARIANT_ATTRIBUTE = Attribute.of("org.gradle.fallback-variant", FallbackVariant.class);

    /**
     * Value applied to a primary variant that has no statically declared artifacts
     * but whose configuration defines secondary variants.
     *
     * @since 9.7.0
     */
    String TRUE = "true";

    /**
     * Value applied to secondary variants whose primary has been tagged as
     * {@link #TRUE}.
     *
     * @since 9.7.0
     */
    String FALSE = "false";
}
