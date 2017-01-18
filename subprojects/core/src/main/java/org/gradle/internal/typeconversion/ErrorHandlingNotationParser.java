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
import org.gradle.internal.exceptions.FormattingDiagnosticsVisitor;

class ErrorHandlingNotationParser<N, T> implements NotationParser<N, T> {
    private final Object targetTypeDisplayName;
    private final String invalidNotationMessage;
    private final boolean allowNullInput;
    private final NotationParser<N, T> delegate;

    public ErrorHandlingNotationParser(Object targetTypeDisplayName, String invalidNotationMessage, boolean allowNullInput, NotationParser<N, T> delegate) {
        this.targetTypeDisplayName = targetTypeDisplayName;
        this.invalidNotationMessage = invalidNotationMessage;
        this.allowNullInput = allowNullInput;
        this.delegate = delegate;
    }

    @Override
    public void describe(DiagnosticsVisitor visitor) {
        delegate.describe(visitor);
    }

    public T parseNotation(N notation) {
        String failure;
        //TODO SF add quotes to both formats (there will be *lots* of tests failing so I'm not sure if it is worth it).
        if (notation == null && !allowNullInput) {
            failure = String.format("Cannot convert a null value to %s.", targetTypeDisplayName);
        } else {
            try {
                return delegate.parseNotation(notation);
            } catch (UnsupportedNotationException e) {
                failure = String.format("Cannot convert the provided notation to %s: %s.", targetTypeDisplayName, e.getNotation());
            }
        }

        FormattingDiagnosticsVisitor visitor = new FormattingDiagnosticsVisitor();
        describe(visitor);

        throw new UnsupportedNotationException(notation, failure, invalidNotationMessage, visitor.getCandidates());
    }
}
