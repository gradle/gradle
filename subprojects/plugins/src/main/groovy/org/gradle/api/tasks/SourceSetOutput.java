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
 * Provides output information of the source set
 *
 * <pre autoTested=''>
 * apply plugin: 'java'
 *
 * sourceSets {
 *   main {
 *     classes {
 *       //if you truly want to override the defaults:
 *       resourcesDir = 'out/res'
 *       classesDir   = 'out/bin'
 *     }
 *   }
 * }
 * </pre>
 *
 * @author: Szczepan Faber, created at: 5/4/11
 */
public interface SourceSetOutput extends FileCollection {

    /**
     * Returns the directory to assemble the compiled classes into.
     *
     * @return The classes dir. Never returns null.
     */
    File getClassesDir();

    /**
     * Sets the directory to assemble the compiled classes into.
     *
     * @param classesDir the classes dir. Should not be null.
     */
    void setClassesDir(Object classesDir);

    /**
     * Returns the output directory for resources
     *
     * @return The dir resources are copied to.
     */
    File getResourcesDir();

    /**
     * Sets the output directory for resources
     *
     * @param resourcesDir the classes dir. Should not be null.
     */
    void setResourcesDir(Object resourcesDir);
}
