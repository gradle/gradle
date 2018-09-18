/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.file;

import javax.annotation.Nullable;
import java.io.File;

public interface TemporaryFileProvider {
    /**
     * Allocates a new temporary file with the exact specified path,
     * relative to the temporary file directory. Does not create the file.
     * Provides no guarantees around whether the file already exists.
     *
     * @param path The tail path components for the file.
     * @return The file
     */
    File newTemporaryFile(String... path);

    /**
     * Allocates and creates a new temporary file with the given prefix, suffix,
     * and path, relative to the temporary file directory.
     */
    File createTemporaryFile(String prefix, @Nullable String suffix, @Nullable String... path);

    File createTemporaryDirectory(@Nullable String prefix, @Nullable String suffix, @Nullable String... path);
}
