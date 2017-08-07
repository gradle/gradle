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

package org.gradle.language.swift.model;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;

/**
 * Configuration for a Swift library or executable, defining the source files that make up the component.
 *
 * @since 4.2
 */
@Incubating
public interface SwiftComponent {
    /**
     * Defines the source files or directories of this component. You can add files or directories to this collection. When a directory is added, all source files are included for compilation.
     *
     * <p>When this collection is empty, the directory {@code src/main/swift} is used by default.</p>
     */
    ConfigurableFileCollection getSource();

    /**
     * Configures the source files or directories for this component.
     */
    void source(Action<? super ConfigurableFileCollection> action);

    /**
     * Returns the Swift source files of this component, as defined in {@link #getSource()}.
     */
    FileCollection getSwiftSource();
}
