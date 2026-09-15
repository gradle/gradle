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

import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.ProblemId;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Shared behavior of all {@code ProblemGroup} implementations: name validation, structural equality, and the path notation.
 */
public final class ProblemGroupSupport {

    public static final int MAX_GROUP_NAME_LENGTH = 50;
    public static final int MAX_PROBLEM_NAME_LENGTH = 2000;
    public static final String UNDEFINED_NAME = "Undefined";
    /**
     * Separates group names when a group chain is rendered for humans, for example {@code Compilation > Java}.
     */
    public static final String SEPARATOR = " > ";

    private ProblemGroupSupport() {
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

    private static boolean isControlOrFormat(int codePoint) {
        if (Character.isISOControl(codePoint)) {
            return true;
        }
        int type = Character.getType(codePoint);
        // line and paragraph separators are neither ISO controls nor format characters, but they break a name across lines
        return type == Character.FORMAT || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR;
    }

    private static boolean isWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    /**
     * Structural equality shared by all implementations: two groups are equal if their names and parents are equal.
     */
    public static boolean equals(ProblemGroup self, @Nullable Object o) {
        if (self == o) {
            return true;
        }
        if (!(o instanceof ProblemGroup)) {
            return false;
        }
        ProblemGroup that = (ProblemGroup) o;
        return self.getName().equals(that.getName()) && Objects.equals(self.getParent(), that.getParent());
    }

    /**
     * Hash code consistent with {@link #equals(ProblemGroup, Object)}.
     */
    public static int hashCode(ProblemGroup self) {
        return Objects.hash(self.getName(), self.getParent());
    }

    /**
     * Renders the chain of group names from the root for humans, for example {@code Compilation > Java}.
     * <p>
     * This is a rendering, not an identity: identity is structural (see {@link #equals(ProblemGroup, Object)}) and the
     * serialized form is the list of names. A group name that contains the separator or a quote is quoted so the rendering
     * cannot be misread, for example {@code Compilation > "Java > Kotlin"}.
     */
    public static String render(ProblemGroup group) {
        ProblemGroup parent = group.getParent();
        String name = quoteIfNeeded(group.getName());
        return parent == null ? name : render(parent) + SEPARATOR + name;
    }

    /**
     * Renders a problem id for humans as the problem name followed by its group chain, for example
     * {@code Unused import (in Compilation > Java)}.
     */
    public static String render(ProblemId id) {
        return renderProblemName(id.getName()) + " (in " + render(id.getGroup()) + ")";
    }

    /**
     * Renders a problem name for humans. A problem name is a sentence rather than a path segment, and sentences legitimately
     * contain quotes, for example {@code Class "Foo" is not serializable}, so quotes alone do not trigger quoting. A name that
     * contains the separator is quoted, so that it cannot be misread as part of the group chain.
     */
    public static String renderProblemName(String name) {
        return name.contains(SEPARATOR.trim()) ? quote(name) : name;
    }

    /**
     * Quotes a group name that contains the separator or a quote, escaping quotes and backslashes inside it, so that a
     * rendered chain cannot be misread. Names that need no quoting are returned unchanged.
     */
    public static String quoteIfNeeded(String name) {
        if (!name.contains(SEPARATOR.trim()) && name.indexOf('"') < 0) {
            return name;
        }
        return quote(name);
    }

    private static String quote(String name) {
        return '"' + name.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
