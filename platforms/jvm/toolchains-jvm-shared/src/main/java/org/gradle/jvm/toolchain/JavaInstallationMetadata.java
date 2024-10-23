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
public interface JavaInstallationMetadata {
    /**
     * Returns the language version of the JVM to which this tool belongs
     *
     * @return the {@code JavaLanguageVersion}
     */
    @Input
    JavaLanguageVersion getLanguageVersion();

    /**
     * Returns the full Java version (including the build number) of the JVM, as specified in its {@code java.runtime.version} property.
     *
     * @return the full Java version of the JVM
     * @since 7.1
     */
    @Internal
    String getJavaRuntimeVersion();

    /**
     * Returns the version of the JVM, as specified in its {@code java.vm.version} property.
     *
     * @return the version of the JVM
     * @since 7.1
     */
    @Internal
    String getJvmVersion();

    /**
     * Returns a human-readable string for the vendor of the JVM.
     *
     * @return the vendor
     * @since 6.8
     */
    @Internal
    String getVendor();

    /**
     * The path to installation this tool belongs to.
     * <p>
     * This value matches what would be the content of {@code JAVA_HOME} for the given installation.
     *
     * @return the installation path
     */
    @Internal
    Directory getInstallationPath();

    /**
     * Returns true if this installation corresponds to the build JVM.
     *
     * @since 8.0
     */
    @Internal
    @Incubating
    boolean isCurrentJvm();
}
