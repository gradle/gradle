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

import org.gradle.internal.RelativePathSupplier;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;

/**
 * Tracks the relative path. Useful when visiting {@link FileSystemLocationSnapshot}s.
 */
public class RelativePathTracker implements RelativePathSupplier {
    private final Deque<String> segments = new ArrayDeque<>();
    private String rootName;

    public void enter(FileSystemLocationSnapshot snapshot) {
        enter(snapshot.getName());
    }

    public void enter(String name) {
        if (rootName == null) {
            rootName = name;
        } else {
            segments.addLast(name);
        }
    }

    public String leave() {
        String name = segments.pollLast();
        if (name == null) {
            name = rootName;
            rootName = null;
        }
        return name;
    }

    @Override
    public boolean isRoot() {
        return segments.isEmpty();
    }

    @Override
    public Collection<String> getSegments() {
        return segments;
    }

    /**
     * Returns the relative path using '{@literal /}' as the separator.
     */
    @Override
    public String toRelativePath() {
        switch (segments.size()) {
            case 0:
                return "";
            case 1:
                return segments.getLast();
            default:
                int length = segments.size() - 1;
                for (String segment : segments) {
                    length += segment.length();
                }
                StringBuilder buffer = new StringBuilder(length);
                Iterator<String> iterator = segments.iterator();
                while (true) {
                    buffer.append(iterator.next());
                    if (!iterator.hasNext()) {
                        break;
                    }
                    buffer.append('/');
                }
                return buffer.toString();
        }
    }
}
