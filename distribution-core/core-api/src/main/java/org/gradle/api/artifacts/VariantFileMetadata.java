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

/**
 * Part of a component variant's metadata representing a file and its location.
 *
 * @since 6.0
 */
public interface VariantFileMetadata {

    /**
     * Get the name of the file.
     *
     * @return the name of the file
     */
    String getName();

    /**
     * Get the location of the file relative to the corresponding metadata file in the repository.
     * This is the same as the file name, if the file is located next to the metadata file.
     *
     * @return relative location of the file
     */
    String getUrl();

}
