/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.file;

import java.io.File;

/**
 * Contains details about the file or directory being visited.
 */
public interface FileVisitDetails {
    /**
     * Returns the file being visited.
     *
     * @return The file. Never returns null.
     */
    File getFile();

    /**
     * Returns the path of the file being visited.
     *
     * @return The path. Never returns null.
     */
    RelativePath getRelativePath();

    /**
     * Requests that file visiting terminate after the current file.
     */
    void stopVisiting();
}
