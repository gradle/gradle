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

import org.gradle.internal.exceptions.DiagnosticsVisitor;

public class JustReturningParser<N, T> implements NotationParser<N, T> {
    private final Class<? extends T> passThroughType;
    private final NotationParser<N, T> delegate;

    public JustReturningParser(Class<? extends T> passThroughType, NotationParser<N, T> delegate) {
        this.passThroughType = passThroughType;
        this.delegate = delegate;
    }

    @Override
    public void describe(DiagnosticsVisitor visitor) {
        visitor.candidate(String.format("Instances of %s.", passThroughType.getSimpleName()));
        delegate.describe(visitor);
    }

    @Override
    public T parseNotation(N notation) throws TypeConversionException {
        if (passThroughType.isInstance(notation)) {
            return passThroughType.cast(notation);
        } else {
            return delegate.parseNotation(notation);
        }
    }
}
