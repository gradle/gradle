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

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;

import javax.annotation.Nullable;
import java.io.File;

/**
 * Represents some configurable directory location, whose value is mutable.
 *
 * <p>
 * You can create a {@link DirectoryProperty} using {@link ObjectFactory#directoryProperty()}.
 * </p>
 *
 * <p><b>Note:</b> This interface is not intended for implementation by build script or plugin authors.</p>
 *
 * @since 4.3
 */
public interface DirectoryProperty extends FileSystemLocationProperty<Directory> {
    /**
     * Returns a {@link FileTree} that allows the files and directories contained in this directory to be queried.
     */
    FileTree getAsFileTree();

    /**
     * {@inheritDoc}
     */
    @Override
    DirectoryProperty value(@Nullable Directory value);

    /**
     * {@inheritDoc}
     */
    @Override
    DirectoryProperty value(Provider<? extends Directory> provider);

    /**
     * {@inheritDoc}
     */
    @Override
    DirectoryProperty fileValue(@Nullable File file);

    /**
     * {@inheritDoc}
     */
    @Override
    DirectoryProperty fileProvider(Provider<File> provider);

    /**
     * {@inheritDoc}
     */
    @Override
    DirectoryProperty convention(@Nullable Directory value);

    /**
     * {@inheritDoc}
     */
    @Override
    DirectoryProperty convention(Provider<? extends Directory> provider);

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

    /**
     * Returns a {@link FileCollection} containing the given files,
     * whose locations are the given paths resolved relative to this directory,
     * as defined by {@link org.gradle.api.Project#files(Object...)}.
     *
     * This method can also be used to create an empty collection, but the collection may not be mutated later.
     *
     * @param paths The paths to the files. May be empty.
     * @return The file collection.
     * @since 6.0
     */
    FileCollection files(Object... paths);
}
