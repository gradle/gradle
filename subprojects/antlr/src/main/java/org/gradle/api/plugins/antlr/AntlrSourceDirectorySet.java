/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.plugins.antlr;

import org.gradle.api.file.SourceDirectorySet;

/**
 * Contract for a Gradle extension that acts as a handler for what I call a virtual directory mapping,
 * injecting a virtual directory named 'antlr' into the project's various {@link org.gradle.api.tasks.SourceSet source
 * sets}.
 *
 * @since 7.1
 */
public interface AntlrSourceDirectorySet extends SourceDirectorySet {

    /**
     * Name of the source set extension contributed by the antlr plugin.
     *
     * @since 8.0
     */
    String NAME = "antlr";
}
