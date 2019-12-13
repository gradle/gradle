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

package org.gradle.api.file;

import org.gradle.api.Incubating;
import org.gradle.api.provider.Provider;

/**
 * Provides lazy access to the contents of a given file.
 *
 * @since 6.1
 */
@Incubating
public interface FileContents {

    /**
     * Gets a provider to the entire file contents as a single String.
     *
     * @return provider to the entire file contents as a single String.
     */
    Provider<String> getAsText();

    /**
     * Gets a provider to the entire file contents as a single byte array.
     *
     * @return provider to the entire file contents as a single byte array.
     */
    Provider<byte[]> getAsBytes();
}
