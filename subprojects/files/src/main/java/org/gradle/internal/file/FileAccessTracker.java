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

package org.gradle.internal.file;

import java.io.File;
import java.util.Collection;

/**
 * Tracks access to files.
 */
public interface FileAccessTracker {
    /**
     * Marks the supplied file as accessed.
     *
     * @see #markAccessed(Collection)
     */
    void markAccessed(File file);

    /**
     * Marks the supplied files as accessed.
     *
     * If the supplied files are unknown to this tracker, implementations must
     * simply ignore them instead of throwing an exception. However, depending
     * on their use case, implementations may throw an exception when marking a
     * known file fails.
     */
    void markAccessed(Collection<File> files);
}
