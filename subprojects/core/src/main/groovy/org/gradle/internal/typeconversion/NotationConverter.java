/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.internal.typeconversion;

import java.util.Collection;

/**
 * A converter from notations of type {@link N} to results of type {@link T}.
 *
 * <p>This interface represents an SPI used to implement notation parsers, not the API to use to perform the conversions. Use {@link NotationParser} instead for this.
 */
public interface NotationConverter<N, T> {
    /**
     * Attempt to convert the given notation.
     *
     * @throws TypeConversionException when the notation is recognized but cannot be converted for some reason.
     */
    void convert(N notation, NotationConvertResult<? super T> result) throws TypeConversionException;

    /**
     * Describes the formats that this converter accepts.
     */
    void describe(Collection<String> candidateFormats);
}
