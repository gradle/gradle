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
 * Marks a variant as a "fallback": one that is selected only when no other variant is compatible
 * with the consumer's request.
 * <p>
 * Gradle applies this attribute automatically to the variants of a {@code Configuration} that
 * defines secondary variants but whose primary variant has no artifacts, including inherited ones,
 * when its variants are computed. The primary is tagged {@link #TRUE} and each secondary
 * {@link #FALSE}, unless the variant already sets this attribute.
 * <p>
 * During artifact selection, a consumer that does not request this attribute is treated as
 * requesting {@link #FALSE}, which excludes the primary. Consumers can select the fallback by
 * explicitly requesting {@link #TRUE}.
 *
 * @since 9.10.0
 */
@Incubating
public interface FallbackVariant extends Named {
    /**
     * Identifies this attribute.
     *
     * @since 9.10.0
     */
    Attribute<FallbackVariant> FALLBACK_VARIANT_ATTRIBUTE = Attribute.of("org.gradle.fallback-variant", FallbackVariant.class);

    /**
     * Value applied to a primary variant that has no artifacts but whose configuration
     * defines secondary variants.
     *
     * @since 9.10.0
     */
    String TRUE = "true";

    /**
     * Value applied to the secondary variants of a configuration whose primary variant
     * is tagged {@link #TRUE}.
     *
     * @since 9.10.0
     */
    String FALSE = "false";
}
