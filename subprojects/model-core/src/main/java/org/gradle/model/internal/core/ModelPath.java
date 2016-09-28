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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import net.jcip.annotations.ThreadSafe;
import org.gradle.api.GradleException;
import org.gradle.api.Nullable;
import org.gradle.internal.exceptions.Contextual;

import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;
import java.util.WeakHashMap;

import static java.lang.System.arraycopy;

@ThreadSafe
public class ModelPath implements Iterable<String>, Comparable<ModelPath> {
    private static final String SEPARATOR = ".";
    private static final CharMatcher VALID_FIRST_CHAR_MATCHER = CharMatcher.inRange('a', 'z').or(CharMatcher.inRange('A', 'Z')).or(CharMatcher.is('_'));
    private final static CharMatcher INVALID_FIRST_CHAR_MATCHER = VALID_FIRST_CHAR_MATCHER.negate().precomputed();
    private final static CharMatcher INVALID_CHAR_MATCHER = CharMatcher.inRange('0', '9').or(VALID_FIRST_CHAR_MATCHER).or(CharMatcher.is('-')).negate().precomputed();

    public static final ModelPath ROOT;
    private static final Cache BY_PATH;

    static {
        ROOT = new ModelPath("", new String[0]) {
            @Override
            public String toString() {
                return "<root>";
            }
        };
        BY_PATH = new Cache(ROOT);
    }

    private final String path;
    private final String[] components;
    private final ModelPath parent;

    public ModelPath(String path) {
        this(path, splitPath(path));
    }

    private ModelPath(String path, String[] components) {
        // one should really avoid using this constructor as it is totally inefficient
        // and reserved to spurious cases when the components have dots in names
        // (and this can happen if a task name contains dots)
        this.path = path;
        this.components = components;
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

        return components.length == modelPath.components.length && path.equals(modelPath.path);
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    @Override
    public Iterator<String> iterator() {
        return Iterators.forArray(components);
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

    @VisibleForTesting
    static ModelPath path(Iterable<String> names) {
        String[] components = Iterables.toArray(names, String.class);
        String path = pathString(components);
        return path(path, components);
    }

    private static ModelPath path(String path, String[] names) {
        for (String name : names) {
            if (name.indexOf('.') >= 0) {
                return new ModelPath(path, names);
            }
        }
        return BY_PATH.get(path, names);
    }

    public static String pathString(String... names) {
        if (names.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0, len = names.length; i < len; i++) {
            if (i != 0) {
                builder.append(".");
            }
            builder.append(names[i]);
        }
        return builder.toString();
    }

    public ModelPath child(String child) {
        if (this.components.length == 0) {
            return path(child, new String[] {child});
        }
        String[] childComponents = new String[components.length + 1];
        arraycopy(components, 0, childComponents, 0, components.length);
        childComponents[components.length] = child;
        return path(path + "." + child, childComponents);
    }

    public ModelPath getRootParent() {
        return components.length <= 1 ? null : ModelPath.path(components[0]);
    }

    @Nullable
    public ModelPath getParent() {
        return parent;
    }

    private ModelPath doGetParent() {
        if (components.length == 0) {
            return null;
        }
        if (components.length == 1) {
            return ROOT;
        }

        String[] parentComponents = new String[components.length - 1];
        arraycopy(components, 0, parentComponents, 0, components.length - 1);

        // Same as the length of this, minus the last element, minus the dot between them
        int parentPathLength = path.length() - components[components.length - 1].length() - 1;

        return path(path.substring(0, parentPathLength), parentComponents);
    }

    public String getName() {
        if (components.length == 0) {
            return "";
        }
        return components[components.length - 1];
    }

    public boolean isDirectChild(@Nullable ModelPath other) {
        if (other == null) {
            return false;
        }
        if (other.components.length != components.length + 1) {
            return false;
        }
        ModelPath otherParent = other.getParent();
        return otherParent != null && otherParent.equals(this);
    }

    public boolean isDescendant(@Nullable ModelPath other) {
        if (other == null) {
            return false;
        }

        int length = components.length;
        if (other.components.length <= components.length) {
            return false;
        }

        for (int i = 0; i < length; i++) {
            if (!components[i].equals(other.components[i])) {
                return false;
            }
        }
        return true;
    }

    public ModelPath descendant(ModelPath path) {
        if (components.length == 0) {
            return path;
        }
        if (path.components.length == 0) {
            return this;
        }

        String[] descendantComponents = new String[components.length + path.components.length];
        arraycopy(components, 0, descendantComponents, 0, components.length);
        arraycopy(path.components, 0, descendantComponents, components.length, path.components.length);

        return path(this.path + "." + path.getPath(), descendantComponents);
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

        String[] names = splitPath(path);
        if (names.length == 1) {
            validateName(names[0]);
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

    private static String[] splitPath(String path) {
        // Let's make sure we never need to reallocate
        List<String> components = Lists.newArrayListWithCapacity(path.length());
        StringTokenizer tokenizer = new StringTokenizer(path, ".");
        while (tokenizer.hasMoreTokens()) {
            String component = tokenizer.nextToken();
            if (component.isEmpty()) {
                continue;
            }
            components.add(component);
        }
        return components.toArray(new String[components.size()]);
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

        public synchronized ModelPath get(String path, String[] names) {
            ModelPath result = cache.get(path);
            if (result != null) {
                return result;
            }
            result = new ModelPath(path, names);
            cache.put(path, result);
            return result;
        }
    }
}
