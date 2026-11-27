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

package org.gradle.features.internal.builders

/**
 * Discriminator for the four "kinds" a {@link PropertyTypeDeclaration} can represent:
 * a plain {@code @Nested} type, a {@code NamedDomainObjectContainer} element, an
 * undiscoverable inner object, or a use-site reference to a shared top-level type.
 *
 * <p>The chosen kind determines both the rendered shape of the nested type body and
 * which {@code kindData} holder is attached to the declaration.</p>
 */
enum NestedKind {
    /** A plain {@code @Nested} type. Carries {@link PlainKindData}. */
    PLAIN,
    /** A {@code NamedDomainObjectContainer<T>} element type. Carries {@link NdocKindData}. */
    NDOC,
    /** A private-field-owned nested object with no public getter. Carries {@link UndiscoverableKindData}. */
    UNDISCOVERABLE,
    /**
     * A use-site reference to a {@code TestScenarioBuilder.sharedType(...)} declaration.
     * Also used for the top-level shared-type declaration itself, which is rendered
     * separately by {@link SharedTypeBuilder}. Carries {@link SharedRefKindData}.
     */
    SHARED_REF
}
