/*
 * Copyright 2022 the original author or authors.
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
import org.gradle.jvm.toolchain.internal.DefaultJavaToolchainInstallation;

import java.nio.file.Path;

/**
 * The response provided by a {@link JavaToolchainResolver} to a specific
 * {@link JavaToolchainRequest} for local toolchain installation directories.
 * <p>
 * Contains the path to the JAVA_HOME directory of a Java toolchain matching the request.
 * The returned path must exist and contain a valid toolchain installation.
 *
 * @since 8.1
 */
@Incubating
public interface JavaToolchainInstallation {

    /**
     * Get the JAVA_HOME directory of this installation.
     */
    Path getJavaHome();

    /**
     * Create a {@link JavaToolchainInstallation} from the JAVA_HOME directory of a
     * local Java toolchain installation.
     *
     * @param javaHome The JAVA_HOME path. Must exist and point to a directory.
     *
     * @return A new {@link JavaToolchainInstallation}.
     */
    static JavaToolchainInstallation fromJavaHome(Path javaHome) {
        return DefaultJavaToolchainInstallation.fromJavaHome(javaHome);
    }

}
