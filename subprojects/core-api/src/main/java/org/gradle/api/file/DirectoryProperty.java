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
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

import java.io.File;

/**
 * Represents some configurable directory location, whose value is mutable and is not necessarily currently known until later.
 * <p>
 * <b>Note:</b> This interface is not intended for implementation by build script or plugin authors. An instance of this class can be created using the {@link ProjectLayout#directoryProperty()} method.
 *
 * @since 4.3
 */
@Incubating
public interface DirectoryProperty extends Provider<Directory>, Property<Directory> {
    /**
     * Views the location of this directory as a {@link File}.
     */
    Provider<File> getAsFile();

    /**
     * Returns a {@link FileTree} that allows the files and directories contained in this directory to be queried.
     */
    FileTree getAsFileTree();

    /**
     * Sets the location of this directory.
     */
    void set(File dir);

    /**
     * Returns a {@link Directory} whose value is the given path resolved relative to the value of this directory.
     *
     * @param path The path. Can be absolute.
     * @return The directory.
     */
    Provider<Directory> dir(String path);

    /**
     * Returns a {@link Directory} whose value is the given path resolved relative to the value of this directory.
     *
     * @param path The path. Can have a value that is an absolute path.
     * @return The directory.
     */
    Provider<Directory> dir(Provider<? extends CharSequence> path);

    /**
     * Returns a {@link RegularFile} whose value is the given path resolved relative to the value of this directory.
     *
     * @param path The path. Can be absolute.
     * @return The file.
     */
    Provider<RegularFile> file(String path);

    /**
     * Returns a {@link RegularFile} whose value is the given path resolved relative to the value of this directory.
     *
     * @param path The path. Can have a value that is an absolute path.
     * @return The file.
     */
    Provider<RegularFile> file(Provider<? extends CharSequence> path);
}
