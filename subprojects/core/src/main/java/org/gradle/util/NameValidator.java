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

import java.util.Arrays;

public final class NameValidator {

    private static final char[] FORBIDDEN_CHARACTERS = new char[] {' ', '/', '\\', ':', '<', '>', '"', '?', '*', '|'};
    private static final char FORBIDDEN_LEADING_AND_TRAILING_CHARACTER = '.';
    private static final char REPLACEMENT_CHARACTER = '_';

    private NameValidator() { }

    /**
     * Validates that a given name string does not contain any forbidden characters.
     */
    public static void validate(String name, String nameDescription, String fixSuggestion) {
        if (StringUtils.isEmpty(name)) {
            DeprecationLogger.nagUserOfDeprecatedThing("The " + nameDescription + " is empty", fixSuggestion);
        } else if (StringUtils.containsAny(name, FORBIDDEN_CHARACTERS)) {
            DeprecationLogger.nagUserOfDeprecatedThing("The " + nameDescription + " '" + name + "' contains at least one of the following characters: " + Arrays.toString(FORBIDDEN_CHARACTERS), fixSuggestion);
        } else if (name.charAt(0) == FORBIDDEN_LEADING_AND_TRAILING_CHARACTER || name.charAt(name.length() - 1) == FORBIDDEN_LEADING_AND_TRAILING_CHARACTER) {
            DeprecationLogger.nagUserOfDeprecatedThing("The " + nameDescription + " '" + name + "' starts or ends with a '" + FORBIDDEN_LEADING_AND_TRAILING_CHARACTER + "'", fixSuggestion);
        }
    }

    /**
     * Turns the given name into a valid name by replacing each forbidden character occurrence with "_".
     */
    public static String asValidName(String name) {
        //TODO activate this once the names which are now deprecated are forbidden
        //for (char forbiddenCharacter : FORBIDDEN_CHARACTERS) {
        //    name = name.replace(forbiddenCharacter, REPLACEMENT_CHARACTER);
        //}
        //if (name.charAt(0) == FORBIDDEN_LEADING_AND_TRAILING_CHARACTER) {
        //    name = REPLACEMENT_CHARACTER + name.substring(1);
        //}
        //if (name.charAt(0) == FORBIDDEN_LEADING_AND_TRAILING_CHARACTER || name.charAt(name.length() - 1) == FORBIDDEN_LEADING_AND_TRAILING_CHARACTER) {
        //    name = name.substring(0, name.length() - 1) + REPLACEMENT_CHARACTER;
        //}
        return name;
    }
}
