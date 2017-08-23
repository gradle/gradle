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

    private static final char[] FORBIDDEN_CHARACTERS = new char[] {' ', '/', '\\', ':'};
    private static final char REPLACEMENT_CHARACTER = '_';

    private NameValidator() { }

    /**
     * Validates that a given name string does not contain any forbidden characters.
     */
    public static void validate(String name) {
        if (StringUtils.containsNone(name, FORBIDDEN_CHARACTERS)) {
            return;
        }
        DeprecationLogger.nagUserOfDeprecated("The name '" + name + "' contains at least one of the following characters: " + Arrays.toString(FORBIDDEN_CHARACTERS) + ". This");
    }

    /**
     * Turns the given name into a valid name by replacing each forbidden character occurrence with "_".
     */
    public static String asValidName(String name) {
        for (int i = 0; i < FORBIDDEN_CHARACTERS.length; i++) {
            name = name.replace(FORBIDDEN_CHARACTERS[i], REPLACEMENT_CHARACTER);
        }
        return name;
    }
}
