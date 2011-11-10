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
public class AlwaysThrowingParser implements NotationParser {
    private final String invalidNotationMessage;

    public AlwaysThrowingParser(String invalidNotationMessage) {
        this.invalidNotationMessage = invalidNotationMessage;
    }

    public boolean canParse(Object notation) {
        return true;
    }

    public Object parseNotation(Object notation) {
        String message = "Provided notation is invalid: " + notation + ".\n"
                + "Specifically, the type of the notation is invalid: " + notation.getClass().getName() + ".\n"
                + invalidNotationMessage;
        throw new InvalidNotationType(message);
    }
}
