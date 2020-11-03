/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.snapshot;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BinaryOperator;

/**
 * Tracks the relative path. Useful when visiting {@link CompleteFileSystemLocationSnapshot}s.
 */
public abstract class RelativePathTracker {
    private final BinaryOperator<String> combiner;
    protected final Deque<String> relativePath = new ArrayDeque<>();
    private String rootName;

    private RelativePathTracker(BinaryOperator<String> combiner) {
        this.combiner = combiner;
    }

    /**
     * Tracks the relative path as an {@link Iterable}{@code <String>} when visiting a {@link CompleteFileSystemLocationSnapshot}.
     */
    public static AsIterable asIterable() {
        return new AsIterable();
    }

    /**
     * Tracks the relative path as a {@link String} when visiting a {@link CompleteFileSystemLocationSnapshot}.
     */
    public static AsString asString() {
        return new AsString();
    }

    public void enter(CompleteFileSystemLocationSnapshot snapshot) {
        enter(snapshot.getName());
    }

    public void enter(String name) {
        String previous = relativePath.peekLast();
        String value = previous == null
            ? name
            : combiner.apply(previous, name);
        if (rootName == null) {
            rootName = value;
        } else {
            relativePath.addLast(value);
        }
    }

    public String leave() {
        if (relativePath.isEmpty()) {
            String currentRootName = rootName;
            rootName = null;
            return currentRootName;
        } else {
            return relativePath.removeLast();
        }
    }

    public boolean isRoot() {
        return rootName == null;
    }

    public static class AsIterable extends RelativePathTracker {
        public AsIterable() {
            super((__, name) -> name);
        }

        public Iterable<String> getRelativePath() {
            return relativePath;
        }
    }

    public static class AsString extends RelativePathTracker {
        public AsString() {
            super((previous, name) -> previous + "/" + name);
        }

        public String getRelativePath() {
            return relativePath.getLast();
        }
    }
}
