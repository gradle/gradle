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
 * Holds a reference to a relative path {@link String} when visiting a {@link CompleteFileSystemLocationSnapshot}.
 *
 * If you need to keep track of individual path segments use {@link RelativePathSegmentsTracker} instead.
 */
public class RelativePathStringTracker {
    private final Deque<String> relativePathStrings = Lists.newLinkedList();
    private boolean root = true;

    public void enter(CompleteFileSystemLocationSnapshot snapshot) {
        enter(snapshot.getName());
    }

    public void enter(String name) {
        if (!root) {
            String previous = relativePathStrings.peekLast();
            if (previous == null) {
                relativePathStrings.addLast(name);
            } else {
                relativePathStrings.addLast(previous + '/' + name);
            }
        }
        root = false;
    }

    public void leave() {
        if (!relativePathStrings.isEmpty()) {
            relativePathStrings.removeLast();
        }
    }

    public boolean isRoot() {
        return root;
    }

    public String getRelativePathString() {
        return relativePathStrings.getLast();
    }
}
