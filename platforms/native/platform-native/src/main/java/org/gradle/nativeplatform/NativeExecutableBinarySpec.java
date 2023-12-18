/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.nativeplatform;

import org.gradle.api.Incubating;
import org.gradle.api.Task;
import org.gradle.platform.base.ApplicationBinarySpec;
import org.gradle.platform.base.BinaryTasksCollection;

/**
 * An binary built by Gradle for a native application.
 */
@Incubating
public interface NativeExecutableBinarySpec extends NativeBinarySpec, ApplicationBinarySpec {

    /**
     * Provides access to key tasks used for building the binary.
     */
    interface TasksCollection extends BinaryTasksCollection {
        /**
         * The link task.
         */
        Task getLink();

        /**
         * The install task.
         */
        Task getInstall();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    NativeExecutableSpec getApplication();

    /**
     * {@inheritDoc}
     */
    @Override
    NativeExecutableSpec getComponent();

    /**
     * Native Installation location for a native executable.
     */
    NativeInstallationSpec getInstallation();

    /**
     * Native Executable File.
     */
    NativeExecutableFileSpec getExecutable();

    /**
     * {@inheritDoc}
     */
    @Override
    TasksCollection getTasks();
}
