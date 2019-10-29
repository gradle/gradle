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

import com.google.common.collect.Lists;

import java.util.Deque;

/**
 * Tracks the relative path as an {@link Iterable}{@code <String>} when visiting a {@link CompleteFileSystemLocationSnapshot}.
 *
 * If you need to keep track of the concatenated relative path use {@link RelativePathStringTracker} instead.
 */
public class RelativePathSegmentsTracker {
    private final Deque<String> relativePath = Lists.newLinkedList();
    private String rootName;


    public void enter(CompleteFileSystemLocationSnapshot snapshot) {
        enter(snapshot.getName());
    }

    public void enter(String name) {
        if (rootName == null) {
            rootName = name;
        } else {
            relativePath.addLast(name);
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

    public Iterable<String> getRelativePath() {
        return relativePath;
    }

    public boolean isRoot() {
        return rootName == null;
    }
}
