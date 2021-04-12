/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.util;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.InvalidUserDataException;

import java.util.Arrays;

/**
 * This class is only here to maintain binary compatibility with existing plugins.
 *
 * @deprecated Will be removed in Gradle 8.0.
 */
@Deprecated
public final class NameValidator {

    private static final char[] FORBIDDEN_CHARACTERS = new char[] {'/', '\\', ':', '<', '>', '"', '?', '*', '|'};
    private static final char FORBIDDEN_LEADING_AND_TRAILING_CHARACTER = '.';

    private NameValidator() { }

    /**
     * Validates that a given name string does not contain any forbidden characters.
     */
    public static void validate(String name, String nameDescription, String fixSuggestion) throws InvalidUserDataException {
        if (StringUtils.isEmpty(name)) {
            throw newInvalidUserDataException("The " + nameDescription + " must not be empty.", fixSuggestion);
        } else if (StringUtils.containsAny(name, FORBIDDEN_CHARACTERS)) {
            throw newInvalidUserDataException("The " + nameDescription + " '" + name + "' must not contain any of the following characters: " + Arrays.toString(FORBIDDEN_CHARACTERS) + ".", fixSuggestion);
        } else if (name.charAt(0) == FORBIDDEN_LEADING_AND_TRAILING_CHARACTER || name.charAt(name.length() - 1) == FORBIDDEN_LEADING_AND_TRAILING_CHARACTER) {
            throw newInvalidUserDataException("The " + nameDescription + " '" + name + "' must not start or end with a '" + FORBIDDEN_LEADING_AND_TRAILING_CHARACTER + "'.", fixSuggestion);
        }
    }

    private static InvalidUserDataException newInvalidUserDataException(String message, String fixSuggestion) {
        return new InvalidUserDataException(message + (StringUtils.isBlank(fixSuggestion) ? "" : (" " + fixSuggestion)));
    }
}
