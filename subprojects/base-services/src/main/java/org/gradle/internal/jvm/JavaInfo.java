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

package org.gradle.internal.jvm;

import org.gradle.api.Nullable;

import java.io.File;

public interface JavaInfo {
    /**
     * @return the executable
     * @throws JavaHomeException when executable cannot be found
     */
    File getJavaExecutable() throws JavaHomeException;

    /**
     * @return the executable
     * @throws JavaHomeException when executable cannot be found
     */
    File getJavacExecutable() throws JavaHomeException;

    /**
     * @return the executable
     * @throws JavaHomeException when executable cannot be found
     */
    File getJavadocExecutable() throws JavaHomeException;

    /**
     * @return the executable
     * @throws JavaHomeException when executable cannot be found
     */
    File getExecutable(String name) throws JavaHomeException;

    /**
     * The location of java.
     *
     * @return the java home location
     */
    File getJavaHome();

    /**
     * Returns the tools jar. May return null, for example when Jvm was created via
     * with custom jre location or if jdk is not installed.
     */
    @Nullable
    File getToolsJar();
}
