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
 * Kind data for {@link NestedKind#SHARED_REF}: a top-level shared type declared via
 * {@code TestScenarioBuilder.sharedType(...)}, or a use-site reference to one.
 *
 * <p>{@link #sharedShape} controls the top-level file's shape (interface vs abstract
 * class) when {@link SharedTypeBuilder} renders the declaration. Ignored at the
 * use-site copy.</p>
 */
class SharedRefKindData {
    TypeShape sharedShape = TypeShape.INTERFACE
}
