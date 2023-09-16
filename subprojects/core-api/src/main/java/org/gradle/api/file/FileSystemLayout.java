/*
 * Copyright 2023 the original author or authors.
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

import java.io.File;

/**
 * Provides provider-based access to (absolute) file system locations.
 * <p>
 * File system locations (or providers thereof) based on relative paths will be resolved against this
 * layout's reference location.
 *
 * @since 8.5
 */
@Incubating
public interface FileSystemLayout {
    /**
     * Creates a {@link RegularFile} provider whose location is calculated from the given {@link Provider}.
     */
    Provider<RegularFile> file(Provider<File> file);

    /**
     * Creates a {@link Directory} provider whose location is calculated from the given {@link Provider}.
     */
    Provider<Directory> dir(Provider<File> file);
}
