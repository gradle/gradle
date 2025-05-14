/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.FileSystemLocationProperty;
import org.gradle.api.provider.Provider;
import org.jspecify.annotations.NullMarked;

/**
 * This class was added just to support FileSystemLocation properties in the convention mapping.
 *
 * Should be removed once ConventionMapping is removed.
 */
@NullMarked
public interface FileSystemLocationPropertyInternal<T extends FileSystemLocation> extends FileSystemLocationProperty<T> {

    /**
     * Same as {@link FileSystemLocationProperty#convention(Provider)} but it also works with a Provider of a File.
     */
    void conventionFromAnyFile(Provider<Object> provider);
}
