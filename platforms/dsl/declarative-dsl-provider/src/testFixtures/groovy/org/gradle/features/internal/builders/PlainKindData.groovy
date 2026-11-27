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
 * Kind data for {@link NestedKind#PLAIN}: a regular {@code @Nested} type.
 *
 * <p>{@link #shape} overrides the body shape (interface vs abstract class) inherited
 * from the enclosing definition. {@code null} means "inherit".</p>
 */
class PlainKindData {
    TypeShape shape = null
}
