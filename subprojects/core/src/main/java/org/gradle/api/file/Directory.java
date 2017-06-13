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
 * Represents a directory. The location of the directory is not mutable through this interface, but the underlying value may be mutable.
 *
 * @since 4.1
 */
@Incubating
public interface Directory extends Provider<File> {
    /**
     * Returns a {@link Directory} whose value is the given path resolved relative to the value of this directory.
     *
     * @param path The path. Can be absolute.
     * @return The directory.
     */
    Directory dir(String path);

    /**
     * Returns a {@link Directory} whose value is the given path resolved relative to the value of this directory.
     *
     * @param path The path. Can be absolute.
     * @return The directory.
     */
    Directory dir(Provider<? extends CharSequence> path);

    /**
     * Returns a {@link Provider} whose value is the given path resolved relative to the value of this directory.
     *
     * @param path The path. Can be absolute.
     * @return The directory.
     */
    Provider<File> file(String path);

    /**
     * Returns a {@link Provider} whose value is the given path resolved relative to the value of this directory.
     *
     * @param path The path. Can be absolute.
     * @return The directory.
     */
    Provider<File> file(Provider<? extends CharSequence> path);
}
