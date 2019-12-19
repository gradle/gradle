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

package org.gradle.jvm.toolchain;

import org.gradle.api.Incubating;
import org.gradle.api.provider.Provider;

import java.io.File;

/**
 * Allows information about Java installations to be queried.
 *
 * <p>An instance of this service is available for injection into tasks, plugins and other types.
 *
 * @since 6.2
 */
@Incubating
public interface JavaInstallationRegistry {
    /**
     * Returns the Java installation for the current virtual machine.
     */
    Provider<JavaInstallation> getInstallationForCurrentVirtualMachine();

    /**
     * Returns information about the Java installation at the given location.
     *
     * <p>Note that this method may return an installation whose Java home directory is not the same as the installation directory. For example, if the given directory
     * points to a JRE installation contained within a JDK installation (as was the case for Java 8 and earlier), then the {@code JavaInstallation} for outer installation will be returned.
     * </p>
     */
    Provider<JavaInstallation> installationForDirectory(File installationDirectory);
}
