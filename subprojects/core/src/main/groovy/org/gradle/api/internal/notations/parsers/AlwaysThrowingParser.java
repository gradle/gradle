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

import org.gradle.api.internal.notations.api.InvalidNotationType;
import org.gradle.api.internal.notations.api.NotationParser;

/**
 * by Szczepan Faber, created at: 11/8/11
 */
public class AlwaysThrowingParser<T> implements NotationParser<T> {
    private final Class<T> targetType;
    private final String invalidNotationMessage;

    public AlwaysThrowingParser(Class<T> targetType, String invalidNotationMessage) {
        this.targetType = targetType;
        this.invalidNotationMessage = invalidNotationMessage;
    }

    public boolean canParse(Object notation) {
        return true;
    }

    public T parseNotation(Object notation) {
        String message;
        if (notation == null) {
            message = String.format("Cannot convert a null value to an object of type %s.%n%s", targetType.getSimpleName(), invalidNotationMessage);
        } else {
            message = String.format("Cannot convert the provided notation to an object of type %s: %s.%n%s", targetType.getSimpleName(), notation, invalidNotationMessage);
        }
        throw new InvalidNotationType(message);
    }
}
