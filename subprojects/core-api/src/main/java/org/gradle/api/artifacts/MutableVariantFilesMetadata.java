/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.artifacts;

import org.gradle.api.Action;

/**
 * Mutable information about the files that belong to a variant.
 *
 * @since 6.0
 */
public interface MutableVariantFilesMetadata {

    /**
     * Remove all files already defined for the variant.
     * Useful when files where initialized from a base variant or configuration using
     * {@link ComponentMetadataDetails#addVariant(String, String, Action)} .
     */
    void removeAllFiles();

    /**
     * Add a file, if the file location is the same as the file name.
     *
     * @param name name and path of the file.
     */
    void addFile(String name);

    /**
     * Add a file.
     *
     * @param name name of the file
     * @param url location of the file, if not located next to the metadata in the repository
     */
    void addFile(String name, String url);
}
