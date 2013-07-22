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
package org.gradle.api.internal.notations.parsers;

import org.gradle.api.internal.notations.api.NotationParser;
import org.gradle.api.internal.notations.api.UnsupportedNotationException;

import java.util.Collection;

public class JustReturningParser<T> implements NotationParser<T> {

    private final Class<T> passThroughType;

    public JustReturningParser(Class<T> passThroughType) {
        this.passThroughType = passThroughType;
    }

    public void describe(Collection<String> candidateFormats) {
        candidateFormats.add(String.format("Instances of %s.", passThroughType.getSimpleName()));
    }

    public T parseNotation(Object notation) {
        if (!passThroughType.isInstance(notation)) {
            throw new UnsupportedNotationException(notation);
        }
        return passThroughType.cast(notation);
    }
}