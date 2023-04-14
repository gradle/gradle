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
package org.gradle.api.internal.artifacts.repositories.metadata;

import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;

/**
 * A specialized attributes factory for Maven metadata. The specialized methods take advantage
 * of the fact we know that for derived variants, we're going to see almost always the same input
 * attributes, and the same mutations to make on them, so it's more efficient to map them, than
 * recomputing each time.
 */
public interface MavenImmutableAttributesFactory extends ImmutableAttributesFactory {
    // We need to work with the 'String' version of the usage attribute, since this is expected for all providers by the `PreferJavaRuntimeVariant` schema
    Attribute<String> USAGE_ATTRIBUTE = Attribute.of(Usage.USAGE_ATTRIBUTE.getName(), String.class);
    Attribute<String> FORMAT_ATTRIBUTE = Attribute.of(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE.getName(), String.class);
    Attribute<String> CATEGORY_ATTRIBUTE = Attribute.of(Category.CATEGORY_ATTRIBUTE.getName(), String.class);

    ImmutableAttributes compileScope(ImmutableAttributes original);
    ImmutableAttributes runtimeScope(ImmutableAttributes original);
    ImmutableAttributes platformWithUsage(ImmutableAttributes original, String usage, boolean enforced);
    ImmutableAttributes sourcesVariant(ImmutableAttributes original);
    ImmutableAttributes javadocVariant(ImmutableAttributes original);
}
