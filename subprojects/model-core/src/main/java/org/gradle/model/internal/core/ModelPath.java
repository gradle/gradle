/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.model.internal.core;

import com.google.common.base.CharMatcher;
import org.gradle.api.GradleException;

public class ModelPath {

    public static final String SEPARATOR = ".";

    private final String path;

    public ModelPath(String path) {
        this.path = path;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ModelPath modelPath = (ModelPath) o;

        return path.equals(modelPath.path);
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    @Override
    public String toString() {
        return path;
    }

    public static ModelPath path(String path) {
        return new ModelPath(path);
    }

    public ModelPath child(String child) {
        return path(path + SEPARATOR + child);
    }

    public ModelPath getParent() {
        int lastIndex = path.lastIndexOf(SEPARATOR);
        if (lastIndex == -1) {
            return null;
        } else {
            return path(path.substring(0, lastIndex));
        }
    }

    public String getName() {
        int lastIndex = path.lastIndexOf(SEPARATOR);
        if (lastIndex == -1) {
            return path;
        } else {
            return path.substring(lastIndex + 1);
        }
    }

    public boolean isDirectChild(ModelPath other) {
        ModelPath otherParent = other.getParent();
        return otherParent != null && otherParent.equals(this);
    }

    public static class InvalidNameException extends GradleException { // TODO: better exception?

        public InvalidNameException(String message) {
            super(message);
        }
    }

    private static final CharMatcher VALID_FIRST_CHAR_MATCHER = CharMatcher.inRange('a', 'z').or(CharMatcher.inRange('A', 'Z')).or(CharMatcher.is('_'));
    private final static CharMatcher INVALID_FIRST_CHAR_MATCHER = VALID_FIRST_CHAR_MATCHER.negate().precomputed();
    private final static CharMatcher INVALID_CHAR_MATCHER = CharMatcher.inRange('0', '9').or(VALID_FIRST_CHAR_MATCHER).negate().precomputed();

    public static void validateName(String name) {
        if (name.isEmpty()) {
            throw new InvalidNameException("Cannot use an empty string as a model element name");
        }

        char firstChar = name.charAt(0);

        if (INVALID_FIRST_CHAR_MATCHER.matches(firstChar)) {
            throw new InvalidNameException(String.format("Model element name '%s' has illegal first character '%s' (names must start with an ASCII letter or underscore)", name, firstChar));
        }

        for (int i = 1; i < name.length(); ++i) {
            char character = name.charAt(i);
            if (INVALID_CHAR_MATCHER.matches(character)) {
                throw new InvalidNameException(String.format("Model element name '%s' contains illegal character '%s' (only ASCII letters, numbers and the underscore are allowed)", name, character));
            }
        }
    }
}
