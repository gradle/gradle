/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.problems.internal;

import org.jspecify.annotations.Nullable;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * The rules for names of groups and problems created through the predefined-group API.
 */
public final class ProblemNames {

    public static final int MAX_GROUP_NAME_LENGTH = 50;
    public static final int MAX_PROBLEM_NAME_LENGTH = 2000;
    public static final String UNDEFINED_NAME = "Undefined";

    private ProblemNames() {
    }

    /**
     * Validates a group name created through the predefined-group API.
     *
     * @return the validated name
     * @throws IllegalArgumentException if the name violates the rules
     */
    public static String validateGroupName(@Nullable String name) {
        return validateName("Problem group name", name, MAX_GROUP_NAME_LENGTH);
    }

    /**
     * Validates a problem name created through the predefined-group API.
     *
     * @return the validated name
     * @throws IllegalArgumentException if the name violates the rules
     */
    public static String validateProblemName(@Nullable String name) {
        return validateName("Problem name", name, MAX_PROBLEM_NAME_LENGTH);
    }

    private static String validateName(String what, @Nullable String name, int maxLength) {
        if (name == null) {
            // not checkNotNull: the contract of the public entry points is IllegalArgumentException, not NullPointerException
            throw new IllegalArgumentException(what + " must not be null");
        }
        checkArgument(!name.isEmpty(), "%s must not be empty", what);
        boolean blank = isBlank(what, name);
        checkArgument(!blank, "%s must not be blank", what);
        boolean startsWithWhitespace = isWhitespace(name.codePointAt(0));
        boolean endsWithWhitespace = isWhitespace(name.codePointBefore(name.length()));
        checkArgument(!(startsWithWhitespace && endsWithWhitespace), "%s must not start or end with whitespace: '%s'", what, name);
        checkArgument(!startsWithWhitespace, "%s must not start with whitespace: '%s'", what, name);
        checkArgument(!endsWithWhitespace, "%s must not end with whitespace: '%s'", what, name);
        int length = name.codePointCount(0, name.length());
        checkArgument(length <= maxLength, "%s must not be longer than %s characters, but was %s characters long", what, maxLength, length);
        return name;
    }

    private static boolean isBlank(String what, String name) {
        boolean blank = true;
        int i = 0;
        while (i < name.length()) {
            int codePoint = name.codePointAt(i);
            checkArgument(!isControlOrFormat(codePoint), "%s must not contain control or format characters: '%s'", what, name);
            blank &= isWhitespace(codePoint);
            i += Character.charCount(codePoint);
        }
        return blank;
    }

    private static final int ZERO_WIDTH_NON_JOINER = 0x200C;
    private static final int ZERO_WIDTH_JOINER = 0x200D;

    private static boolean isControlOrFormat(int codePoint) {
        if (Character.isISOControl(codePoint)) {
            return true;
        }
        if (codePoint == ZERO_WIDTH_NON_JOINER || codePoint == ZERO_WIDTH_JOINER) {
            // format characters, but part of correct spelling in Persian, Arabic and Indic scripts, and of emoji sequences;
            // PRECIS FreeformClass (RFC 8264) allows them for the same reason
            return false;
        }
        int type = Character.getType(codePoint);
        // line and paragraph separators are neither ISO controls nor format characters, but they break a name across lines
        return type == Character.FORMAT || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR;
    }

    private static boolean isWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }
}
