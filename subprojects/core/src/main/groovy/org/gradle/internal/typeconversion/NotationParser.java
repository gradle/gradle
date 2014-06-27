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

package org.gradle.internal.typeconversion;

import java.util.Collection;

/**
 * A parser from notations of type {@link N} to a result of type {@link T}. This interface is used by clients to perform the parsing. To implement a parser, you should use {@link NotationConverter}
 * instead.
 */
public interface NotationParser<N, T> {
    /**
     * @throws UnsupportedNotationException When the supplied notation is not handled by this parser.
     * @throws TypeConversionException When the supplied notation is recognised by this parser but is badly formed and cannot be converted to the target type.
     */
    T parseNotation(N notation) throws UnsupportedNotationException, TypeConversionException;

    /**
     * Describes the formats that the parser accepts.
     */
    void describe(Collection<String> candidateFormats);
}
