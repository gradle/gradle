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
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

import javax.annotation.Nullable;
import java.io.File;

/**
 * Represents some element of the file system. A file system element has two parts: its location and its content. A file system element, or more accurately, its content, may be the output of a task
 * or tasks. This property object keeps track of both the location and the task or tasks that produce the content of the element.
 *
 * <p><b>Note:</b> This interface is not intended for implementation by build script or plugin authors.</p>
 *
 * @param <T> The type of location.
 * @since 5.6
 */
@Incubating
public interface FileSystemLocationProperty<T extends FileSystemLocation> extends Property<T> {
    /**
     * Views the location of this file as a {@link File}.
     */
    Provider<File> getAsFile();

    /**
     * Sets the location of this file, using a {@link File} instance.
     */
    void set(@Nullable File file);

    /**
     * Returns the location of the file system element, and discards details of the task that produces its content. This allows the location, or a value derived from it, to be used as an input to some other task without implying any dependency on the producing task. This should only be used when the task does, in fact, not use the content of this file system element.
     */
    Provider<T> getLocationOnly();
}
