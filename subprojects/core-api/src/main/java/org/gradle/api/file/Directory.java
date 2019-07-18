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

import org.gradle.api.Incubating;
import org.gradle.api.provider.Provider;

import java.io.File;

/**
 * Represents a directory at some fixed location on the file system.
 * <p>
 * <b>Note:</b> This interface is not intended for implementation by build script or plugin authors. An instance of this class can be created
 * using the {@link #dir(String)} method or using various methods on {@link ProjectLayout} such as {@link ProjectLayout#getProjectDirectory()}.
 *
 * @since 4.1
 */
@Incubating
public interface Directory extends FileSystemLocation {
    /**
     * Returns the location of this directory, as an absolute {@link File}.
     *
     * @since 4.2
     */
    @Override
    File getAsFile();

    /**
     * Returns a {@link FileTree} that allows the files and directories contained in this directory to be queried.
     */
    FileTree getAsFileTree();

    /**
     * Returns a {@link Directory} whose location is the given path, resolved relative to this directory.
     *
     * @param path The path. Can be absolute.
     * @return The directory.
     */
    Directory dir(String path);

    /**
     * Returns a {@link Provider} whose value is a {@link Directory} whose location is the given path resolved relative to this directory.
     *
     * <p>The return value is live and the provided {@code path} is queried each time the return value is queried.
     *
     * @param path The path provider. Can have value that is an absolute path.
     * @return The provider.
     */
    Provider<Directory> dir(Provider<? extends CharSequence> path);

    /**
     * Returns a {@link RegularFile} whose location is the given path, resolved relative to this directory.
     *
     * @param path The path. Can be absolute.
     * @return The file.
     */
    RegularFile file(String path);

    /**
     * Returns a {@link Provider} whose value is a {@link RegularFile} whose location is the given path resolved relative to this directory.
     *
     * <p>The return value is live and the provided {@code path} is queried each time the return value is queried.
     *
     * @param path The path provider. Can have value that is an absolute path.
     * @return The file.
     */
    Provider<RegularFile> file(Provider<? extends CharSequence> path);
}
