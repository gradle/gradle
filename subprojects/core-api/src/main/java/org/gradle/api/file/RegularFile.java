/*
 * Copyright 2017 the original author or authors.
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
 * Represents a regular file at a fixed location on the file system. A regular file is a file that is not a directory and is not some special kind of file such as a device.
 * <p>
 * <b>Note:</b> This interface is not intended for implementation by build script or plugin authors. An instance of this class can be created
 * from a {@link Directory} instance using the {@link Directory#file(String)} method or via various methods on {@link ProjectLayout} such as {@link ProjectLayout#getProjectDirectory()}.
 *
 * @since 4.1
 */
public interface RegularFile extends FileSystemLocation {
    /**
     * Returns the location of this file, as an absolute {@link File}.
     */
    @Override
    File getAsFile();
}
