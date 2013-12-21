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

public class CompositeNotationParser<N, T> implements NotationParser<N, T> {
    private final Collection<? extends NotationParser<? super N, ? extends T>> delegates;

    public CompositeNotationParser(Collection<? extends NotationParser<? super N, ? extends T>> delegates) {
        assert delegates != null : "delegates cannot be null!";
        this.delegates = delegates;
    }

    public void describe(Collection<String> candidateFormats) {
        for (NotationParser<?, ?> delegate : delegates) {
            delegate.describe(candidateFormats);
        }
    }

    public T parseNotation(N notation) {
        for (NotationParser<? super N, ? extends T> delegate : delegates) {
            try {
                return delegate.parseNotation(notation);
            } catch (UnsupportedNotationException e) {
                // Ignore
            }
        }

        throw new UnsupportedNotationException(notation);
    }
}
