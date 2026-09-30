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

import java.text.Normalizer;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * The rules for names of groups and problems created through the predefined-group API.
 */
final class ProblemNames {

    static final int MAX_GROUP_NAME_LENGTH = 50;
    static final int MAX_PROBLEM_NAME_LENGTH = 2000;
    static final String UNDEFINED_NAME = "Undefined";
    /**
     * The punctuation allowed in group names, as found in category names such as {@code C++}, {@code C#}, {@code CI/CD} and in
     * the dashed or dotted ids plugins use today.
     */
    static final String ALLOWED_GROUP_NAME_PUNCTUATION = "-_.+/&'#";

    private ProblemNames() {
    }

    /**
     * Validates a group name created through the predefined-group API. A group name is a category: besides the rules shared
     * with problem names, it
     * <ul>
     *     <li>only contains letters and decimal digits of any script, the combining marks that scripts such as Arabic,
     *     Devanagari or Thai need for spelling, the zero width joiner and non-joiner, single spaces, and the characters
     *     {@value #ALLOWED_GROUP_NAME_PUNCTUATION};</li>
     *     <li>starts with a letter or a digit;</li>
     *     <li>is in Unicode normalization form NFC, so that two names that render the same are the same name.</li>
     * </ul>
     * Symbols, including {@code >} and emoji, quotes and other punctuation are rejected: group names are rendered as a chain
     * separated by {@code >}, and none of these has a place in a category name.
     *
     * @return the validated name
     * @throws IllegalArgumentException if the name violates the rules
     */
    static String validateGroupName(@Nullable String name) {
        String validated = validateName("Problem group name", name, MAX_GROUP_NAME_LENGTH);
        checkArgument(Normalizer.isNormalized(validated, Normalizer.Form.NFC), "Problem group name must be in Unicode normalization form NFC: '%s'", validated);
        int first = validated.codePointAt(0);
        checkArgument(Character.isLetter(first) || Character.isDigit(first), "Problem group name must start with a letter or digit: '%s'", validated);
        int previous = first;
        int i = Character.charCount(first);
        while (i < validated.length()) {
            int codePoint = validated.codePointAt(i);
            if (!isAllowedInGroupName(codePoint)) {
                throw new IllegalArgumentException(String.format(
                    "Problem group name must only contain letters, digits, spaces and the characters %s, but contains '%s' (U+%04X): '%s'",
                    ALLOWED_GROUP_NAME_PUNCTUATION, new String(Character.toChars(codePoint)), codePoint, validated
                ));
            }
            checkArgument(!(codePoint == ' ' && previous == ' '), "Problem group name must not contain consecutive spaces: '%s'", validated);
            previous = codePoint;
            i += Character.charCount(codePoint);
        }
        return validated;
    }

    private static boolean isAllowedInGroupName(int codePoint) {
        if (Character.isLetter(codePoint) || Character.isDigit(codePoint)) {
            return true;
        }
        int type = Character.getType(codePoint);
        if (type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK) {
            // combining marks are part of the spelling in many scripts: Arabic vowel signs, Devanagari vowel signs and virama, Thai tone marks
            return true;
        }
        return codePoint == ' '
            || codePoint == ZERO_WIDTH_NON_JOINER
            || codePoint == ZERO_WIDTH_JOINER
            || ALLOWED_GROUP_NAME_PUNCTUATION.indexOf(codePoint) >= 0;
    }

    /**
     * Validates a problem name created through the predefined-group API.
     *
     * @return the validated name
     * @throws IllegalArgumentException if the name violates the rules
     */
    static String validateProblemName(@Nullable String name) {
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
            // codePointAt() combines a valid pair into one code point, so a surrogate seen here has no partner: it is not
            // a character and turns into a replacement character when the name is encoded, which changes the identity
            checkArgument(Character.getType(codePoint) != Character.SURROGATE, "%s must not contain unpaired surrogates: '%s'", what, name);
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
