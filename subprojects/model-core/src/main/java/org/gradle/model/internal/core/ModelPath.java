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
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import net.jcip.annotations.ThreadSafe;
import org.gradle.api.GradleException;
import org.gradle.api.Nullable;
import org.gradle.internal.exceptions.Contextual;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.WeakHashMap;

@ThreadSafe
public class ModelPath implements Iterable<String>, Comparable<ModelPath> {
    private static final String SEPARATOR = ".";
    private static final Splitter PATH_SPLITTER = Splitter.on('.').omitEmptyStrings();
    private static final Joiner PATH_JOINER = Joiner.on('.');
    private static final CharMatcher VALID_FIRST_CHAR_MATCHER = CharMatcher.inRange('a', 'z').or(CharMatcher.inRange('A', 'Z')).or(CharMatcher.is('_'));
    private final static CharMatcher INVALID_FIRST_CHAR_MATCHER = VALID_FIRST_CHAR_MATCHER.negate().precomputed();
    private final static CharMatcher INVALID_CHAR_MATCHER = CharMatcher.inRange('0', '9').or(VALID_FIRST_CHAR_MATCHER).or(CharMatcher.is('-')).negate().precomputed();

    public static final ModelPath ROOT;
    private static final Cache BY_PATH;

    static {
        ROOT = new ModelPath("") {
            @Override
            public String toString() {
                return "<root>";
            }

            @Override
            public ModelPath descendant(ModelPath path) {
                return path;
            }
        };
        BY_PATH = new Cache(ROOT);
    }

    private final String path;
    private final List<String> components;
    private final ModelPath parent;

    public ModelPath(String path) {
        this.path = path;
        this.components = new ArrayList<String>(PATH_SPLITTER.splitToList(path));
        this.parent = doGetParent();
    }

    private ModelPath(String path, Iterable<String> components) {
        // one should really avoid using this constructor as it is totally inefficient
        // and reserved to spurious cases when the components have dots in names
        // (and this can happen if a task name contains dots)
        this.path = path;
        this.components = Lists.newArrayList(components);
        this.parent = doGetParent();
    }

    @Override
    public int compareTo(ModelPath other) {
        if (this == other) {
            return 0;
        }
        return path.compareTo(other.path);
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

        return components.size() == modelPath.components.size() && path.equals(modelPath.path);
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    public int getDepth() {
        return components.size();
    }

    public List<String> getComponents() {
        return components;
    }

    @Override
    public Iterator<String> iterator() {
        return components.iterator();
    }

    public String getPath() {
        return path;
    }

    @Override
    public String toString() {
        return path;
    }

    public static ModelPath path(String path) {
        return BY_PATH.get(path);
    }

    public static ModelPath path(Iterable<String> names) {
        String path = pathString(names);
        for (String name : names) {
            if (name.indexOf('.') >= 0) {
                return new ModelPath(path, names);
            }
        }
        return BY_PATH.get(path);
    }

    public static String pathString(Iterable<String> names) {
        return PATH_JOINER.join(names);
    }

    public ModelPath child(String child) {
        List<String> childComponents = new ArrayList<String>(components);
        childComponents.add(child);
        return path(childComponents);
    }

    public ModelPath getRootParent() {
        return components.size() <= 1 ? null : ModelPath.path(components.get(0));
    }

    @Nullable
    public ModelPath getParent() {
        return parent;
    }

    private ModelPath doGetParent() {
        if (components.isEmpty()) {
            return null;
        }
        if (components.size() == 1) {
            return ROOT;
        }

        return path(components.subList(0, components.size() - 1));
    }

    public String getName() {
        if (components.isEmpty()) {
            return "";
        }
        return components.get(components.size() - 1);
    }

    public boolean isDirectChild(@Nullable ModelPath other) {
        if (other == null) {
            return false;
        }
        if (other.getDepth() != getDepth() + 1) {
            return false;
        }
        ModelPath otherParent = other.getParent();
        return otherParent != null && otherParent.equals(this);
    }

    public boolean isDescendant(@Nullable ModelPath other) {
        if (other == null) {
            return false;
        }
        if (other.getDepth() <= getDepth()) {
            return false;
        }
        return getComponents().equals(other.getComponents().subList(0, getDepth()));
    }

    public ModelPath descendant(ModelPath path) {
        return path(Iterables.concat(components, path.components));
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

    private static class Cache {
        private final WeakHashMap<String, ModelPath> cache = new WeakHashMap<String, ModelPath>();

        public Cache(ModelPath root) {
            cache.put(root.path, root);
        }

        public synchronized ModelPath get(String path) {
            ModelPath result = cache.get(path);
            if (result != null) {
                return result;
            }
            result = new ModelPath(path);
            cache.put(path, result);
            return result;
        }
    }
}
