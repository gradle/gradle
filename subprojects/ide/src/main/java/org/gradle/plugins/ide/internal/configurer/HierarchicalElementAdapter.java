/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugins.ide.internal.configurer;

/**
 * Adapts any type of element to the generic {@link HierarchicalElementDeduplicator}.
 *
 * @param <T> the type of element to de-duplicate
 */
public interface HierarchicalElementAdapter<T> {

    /**
     * Returns the original name of the given element.
     *
     * @param element the element, cannot be null
     * @return the name of the element, never null
     */
    String getName(T element);

    /**
     * Returns the parent in this element's hierarchy.
     *
     * @param element the child element, cannot be null
     * @return the parent element, may be null
     */
    T getParent(T element);
}
