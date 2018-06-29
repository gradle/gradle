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

package org.gradle.api.internal.changedetection.state.mirror.logical;

import org.gradle.api.internal.changedetection.state.FileContentSnapshot;

/**
 * Visitor of {@link LogicalSnapshot}.
 *
 * The visiting needs to happen depth first. The root {@link LogicalSnapshot} is always visited first.
 */
public interface LogicalSnapshotVisitor {
    /**
     * Called when entering a directory.
     *
     * @param path The absolute path of the directory.
     * @param name The name of the directory.
     */
    void preVisitDirectory(String path, String name);

    /**
     * Called when visiting a single file.
     *
     * <p>
     * If {@link #preVisitDirectory(String, String)} has not been called before, this is the file at the root.
     * Only root files may be missing files.
     * </p>
     *
     * @param path The absolute path of the file.
     * @param name The name of the file.
     * @param content The content hash of the file.
     */
    void visit(String path, String name, FileContentSnapshot content);

    /**
     * Called when leaving a directory.
     */
    void postVisitDirectory();
}
