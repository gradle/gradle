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

package org.gradle.jvm;

import org.gradle.api.Incubating;
import org.gradle.api.Task;
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier;
import org.gradle.internal.HasInternalProtocol;
import org.gradle.platform.base.BinaryTasksCollection;
import org.gradle.platform.base.LibraryBinarySpec;

import java.io.File;
import java.util.Set;

/**
 * Definition of a Jar file binary that is to be built by Gradle.
 */
@Incubating @HasInternalProtocol
@Deprecated
public interface JarBinarySpec extends LibraryBinarySpec, JvmBinarySpec {
    /**
     * {@inheritDoc}
     */
    @Override
    TasksCollection getTasks();

    /**
     * {@inheritDoc}
     */
    @Override
    JvmLibrarySpec getLibrary();

    /**
     * The unique identifier of this JarBinarySpec.
     */
    LibraryBinaryIdentifier getId();

    /**
     * The jar file output for this binary.
     */
    File getJarFile();

    /**
     * Sets the jar file output for this binary.
     */
    void setJarFile(File jarFile);

    /**
     * The API jar file output for this binary.
     */
    File getApiJarFile();

    /**
     * Sets the API jar file output for this binary.
     */
    void setApiJarFile(File jarFile);

    void setExportedPackages(Set<String> exportedPackages);

    Set<String> getExportedPackages();

    /**
     * Provides access to key tasks used for building the binary.
     */
    interface TasksCollection extends BinaryTasksCollection {
        /**
         * The jar task used to create an archive for this binary.
         */
        Task getJar();
    }
}
