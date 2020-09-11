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

package org.gradle.jvm.toolchain;

import org.gradle.api.Incubating;
import org.gradle.api.file.RegularFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;

/**
 * Generates HTML API documentation for Java classes.
 *
 * @since 6.7
 */
@Incubating
public interface JavadocTool {


    /**
     * Returns metadata information about this tool
     *
     * @return the tool metadata
     */
    @Nested
    JavaInstallationMetadata getMetadata();

    /**
     * Returns the path to the executable for this tool
     *
     * @return the path to the executable
     */
    @Internal
    RegularFile getExecutablePath();
}
