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

import org.gradle.api.InvalidUserDataException;
import org.gradle.util.GUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Formatter;
import java.util.List;

public class ErrorHandlingNotationParser<N, T> implements NotationParser<N, T> {
    private final String targetTypeDisplayName;
    private final String invalidNotationMessage;
    private final NotationParser<N, T> delegate;

    public ErrorHandlingNotationParser(String targetTypeDisplayName, String invalidNotationMessage, NotationParser<N, T> delegate) {
        this.targetTypeDisplayName = targetTypeDisplayName;
        this.invalidNotationMessage = invalidNotationMessage;
        this.delegate = delegate;
    }

    public void describe(Collection<String> candidateFormats) {
        delegate.describe(candidateFormats);
    }

    public T parseNotation(N notation) {
        Formatter message = new Formatter();
        if (notation == null) {
            //we don't support null input at the moment. If you need this please implement it.
            message.format("Cannot convert a null value to an object of type %s.%n", targetTypeDisplayName);
        } else {
            try {
                return delegate.parseNotation(notation);
            } catch (UnsupportedNotationException e) {
                message.format("Cannot convert the provided notation to an object of type %s: %s.%n", targetTypeDisplayName, e.getNotation());
            }
        }

        message.format("The following types/formats are supported:");
        List<String> formats = new ArrayList<String>();
        describe(formats);
        for (String format : formats) {
            message.format("%n  - %s", format);
        }
        if (GUtil.isTrue(invalidNotationMessage)) {
            message.format("%n%s", invalidNotationMessage);
        }
        throw new InvalidUserDataException(message.toString());
    }
}
