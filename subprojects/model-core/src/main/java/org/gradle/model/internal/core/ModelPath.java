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
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import org.gradle.api.GradleException;
import org.gradle.api.Nullable;
import org.gradle.internal.exceptions.Contextual;

import java.util.List;

public class ModelPath {

    public static final String SEPARATOR = ".";
    public static final Splitter PATH_SPLITTER = Splitter.on('.');
    public static final Joiner PATH_JOINER = Joiner.on('.');

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

    public static ModelPath path(Iterable<String> names) {
        return path(PATH_JOINER.join(names));
    }

    public static String pathString(Iterable<String> names) {
        return PATH_JOINER.join(names);
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

    public static class InvalidNameException extends GradleException {
        public InvalidNameException(String message) {
            super(message);
        }
    }

    @Contextual
    public static class InvalidPathException extends GradleException {
        public InvalidPathException(String message, InvalidNameException e) {
            super(message, e);
        }
    }

    private static final CharMatcher VALID_FIRST_CHAR_MATCHER = CharMatcher.inRange('a', 'z').or(CharMatcher.inRange('A', 'Z')).or(CharMatcher.is('_'));
    private final static CharMatcher INVALID_FIRST_CHAR_MATCHER = VALID_FIRST_CHAR_MATCHER.negate().precomputed();
    private final static CharMatcher INVALID_CHAR_MATCHER = CharMatcher.inRange('0', '9').or(VALID_FIRST_CHAR_MATCHER).negate().precomputed();

    public static void validateName(String name) {
        if (name.isEmpty()) {
            throw new InvalidNameException("Cannot use an empty string as a model element name.");
        }

        char firstChar = name.charAt(0);

        if (INVALID_FIRST_CHAR_MATCHER.matches(firstChar)) {
            throw new InvalidNameException(String.format("Model element name '%s' has illegal first character '%s' (names must start with an ASCII letter or underscore).", name, firstChar));
        }

        for (int i = 1; i < name.length(); ++i) {
            char character = name.charAt(i);
            if (INVALID_CHAR_MATCHER.matches(character)) {
                throw new InvalidNameException(String.format("Model element name '%s' contains illegal character '%s' (only ASCII letters, numbers and the underscore are allowed).", name, character));
            }
        }
    }

    @Nullable
    public static ModelPath validatedPath(@Nullable String path) {
        if (path == null) {
            return null;
        } else {
            validatePath(path);
            return path(path);
        }
    }

    public static ModelPath nonNullValidatedPath(String path) {
        if (path == null) {
            throw new IllegalArgumentException("path cannot be null");
        } else {
            return validatedPath(path);
        }
    }

    public static void validatePath(String path) throws InvalidPathException {
        if (path.isEmpty()) {
            throw new InvalidPathException("Cannot use an empty string as a model path.", null);
        }

        if (path.startsWith(SEPARATOR)) {
            throw new InvalidPathException(String.format("Model path '%s' cannot start with name separator '%s'.", path, SEPARATOR), null);
        }

        if (path.endsWith(SEPARATOR)) {
            throw new InvalidPathException(String.format("Model path '%s' cannot end with name separator '%s'.", path, SEPARATOR), null);
        }

        List<String> names = PATH_SPLITTER.splitToList(path);
        if (names.size() == 1) {
            validateName(names.get(0));
        } else {
            for (String name : names) {
                try {
                    validateName(name);
                } catch (InvalidNameException e) {
                    throw new InvalidPathException(String.format("Model path '%s' is invalid due to invalid name component.", path), e);
                }
            }
        }
    }
}
