/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.tasks.compile;

import org.gradle.api.Incubating;

/**
 * When should the --module-path be used for compiling Java sources?
 *
 * @since 6.3
 */
@Incubating
public enum ModulePathHandling {

    /**
     * Always use the --classpath (traditional behavior).
     */
    NONE,

    /**
     * If the compiled sources represent a Java module, automatically detect modules on the
     * {@link AbstractCompile#getClasspath()} and put them on the --module-path.
     */
    AUTO,

    /**
     * Put everything from the {@link AbstractCompile#getClasspath()} on the --module-path.
     */
    ALL
}
