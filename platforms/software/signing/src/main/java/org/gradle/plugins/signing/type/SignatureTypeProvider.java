/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.signing.type;

import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;

/**
 * Provider of {@link SignatureType}.
 * @since 1.0
 */
public interface SignatureTypeProvider {

    /**
     * Returns the default type.
     *
     * @since 1.0
     */
    @ToBeReplacedByLazyProperty
    SignatureType getDefaultType();

    /**
     * Sets the default type.
     *
     * @since 1.0
     */
    void setDefaultType(String extension);

    /**
     * Returns the type for extension.
     *
     * @since 1.0
     */
    @ToBeReplacedByLazyProperty
    SignatureType getTypeForExtension(String extension);

    /**
     * Returns whether type for extension.
     *
     * @since 1.0
     */
    boolean hasTypeForExtension(String extension);
}
