/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.plugins;

import org.gradle.api.NamedDomainObjectCollectionSchema;
import org.gradle.api.reflect.TypeOf;

/**
 * Schema of extensions.
 *
 * @see ExtensionContainer
 * @since 4.5
 */
public interface ExtensionsSchema extends NamedDomainObjectCollectionSchema, Iterable<ExtensionsSchema.ExtensionSchema> {

    /**
     * {@inheritDoc}
     */
    @Override
    Iterable<ExtensionSchema> getElements();

    /**
     * Schema of an extension.
     *
     * @since 4.5
     */
    interface ExtensionSchema extends NamedDomainObjectCollectionSchema.NamedDomainObjectSchema {

        /**
         * The name of the extension.
         *
         * @return the name of the extension
         */
        @Override
        String getName();

        /**
         * The public type of the extension.
         *
         * @return the public type of the extension
         */
        @Override
        TypeOf<?> getPublicType();
    }
}
