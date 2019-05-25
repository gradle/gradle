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

package org.gradle.api;

import org.gradle.api.reflect.TypeOf;

/**
 * Schema of named domain object collections.
 *
 * @since 4.10
 * @see NamedDomainObjectCollection
 */
public interface NamedDomainObjectCollectionSchema {
    /**
     * Returns an iterable of the schemas for each element in the collection.
     */
    Iterable<? extends NamedDomainObjectSchema> getElements();

    /**
     * Schema of a named domain object.
     *
     * @since 4.10
     */
    interface NamedDomainObjectSchema {
        /**
         * The name of the domain object.
         */
        String getName();

        /**
         * The public type of the domain object.
         */
        TypeOf<?> getPublicType();
    }
}
