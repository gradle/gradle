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

/**
 * by Szczepan Faber, created at: 11/10/11
 */
public class CompositeNotationParser<T> implements NotationParser<T> {

    private final Collection<NotationParser<? extends T>> delegates;

    public CompositeNotationParser(Collection<NotationParser<? extends T>> delegates) {
        assert delegates != null : "delegates cannot be null!";
        this.delegates = delegates;
    }

    public void describe(Collection<String> candidateFormats) {
        for (NotationParser<? extends T> delegate : delegates) {
            delegate.describe(candidateFormats);
        }
    }

    public T parseNotation(Object notation) {
        for (NotationParser<? extends T> delegate : delegates) {
            try {
                return delegate.parseNotation(notation);
            } catch (UnsupportedNotationException e) {
                // Ignore
            }
        }

        throw new UnsupportedNotationException(notation);
    }
}
