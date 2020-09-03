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
import org.gradle.api.file.Directory;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;

/**
 * Metadata about a Java tool obtained from a toolchain.
 *
 * @see JavaLauncher
 * @see JavaCompiler
 * @see JavadocTool
 *
 * @since 6.7
 */
@Incubating
public interface JavaInstallationMetadata {
    /**
     * Returns the language version of the JVM to which this tool belongs
     *
     * @return the {@code JavaLanguageVersion}
     */
    @Input
    JavaLanguageVersion getLanguageVersion();

    /**
     * The path to installation this tool belongs to.
     * <p>
     * This value matches what would be the content of {@code JAVA_HOME} for the given installation.
     *
     * @return the installation path
     */
    @Internal
    Directory getInstallationPath();
}
