/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.tooling.events.problems;

import org.gradle.api.Incubating;
import org.jspecify.annotations.Nullable;

/**
 * A position in compiled code, such as a line in a plugin's class.
 * <p>
 * The file name and line come from the class's debug information. The file isn't in the build;
 * with the class and the module coordinates, an IDE can find the line in the module's sources.
 *
 * @since 9.10.0
 */
@Incubating
public interface CompiledCodePosition extends Position {

    /**
     * Returns the name of the source file the class was compiled from, such as {@code AcmePlugin.java}.
     *
     * @return the file name
     * @since 9.10.0
     */
    String getFileName();

    /**
     * Returns the fully qualified name of the class.
     *
     * @return the class name
     * @since 9.10.0
     */
    String getClassName();

    /**
     * Returns the coordinates of the module the class came from, such as {@code com.acme:acme-plugin:1.0}.
     *
     * @return the module, or {@code null} if not known
     * @since 9.10.0
     */
    @Nullable
    String getModule();
}
