/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.tasks;

import org.gradle.api.file.FileCollection;

import java.io.File;

/**
 * A collection of all output directories (compiled classes, processed resources, etc.) - notice that {@link SourceSetOutput} extends {@link FileCollection}.
 * <p>
 * Provides output information of the source set. Allows configuring the default output dirs and specify additional output dirs.
 *
 * <pre autoTested=''>
 * apply plugin: 'java'
 *
 * sourceSets {
 *   main {
 *     //if you truly want to override the defaults:
 *     output.resourcesDir = 'out/res'
 *     output.classesDir   = 'out/bin'
 *   }
 * }
 * </pre>
 */
public interface SourceSetOutput extends FileCollection {

    /**
     * Returns the directory to assemble the compiled classes into.
     * <p>
     * See example at {@link SourceSetOutput}
     *
     * @return The classes dir. Never returns null.
     */
    File getClassesDir();

    /**
     * Sets the directory to assemble the compiled classes into.
     * <p>
     * See example at {@link SourceSetOutput}
     *
     * @param classesDir the classes dir. Should not be null.
     */
    void setClassesDir(Object classesDir);

    /**
     * Returns the output directory for resources
     * <p>
     * See example at {@link SourceSetOutput}
     *
     * @return The dir resources are copied to.
     */
    File getResourcesDir();

    /**
     * Sets the output directory for resources
     * <p>
     * See example at {@link SourceSetOutput}
     *
     * @param resourcesDir the classes dir. Should not be null.
     */
    void setResourcesDir(Object resourcesDir);
}
