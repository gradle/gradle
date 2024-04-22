/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.properties.bean;

/**
 * Walks the properties of a pair of objects, visiting each pair of properties with the provided {@link PropertyPairVisitor}.
 */
public interface PropertyPairWalker {
    /**
     * Visits the properties of the given pair of objects.
     */
    <T, L extends T, R extends T> void visitPropertyPairs(Class<T> commonType, L left, R right, PropertyPairVisitor visitor);
}
