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

package org.gradle.runtime.jvm;

import org.gradle.api.Incubating;
import org.gradle.runtime.base.ProjectBinary;

import java.io.File;

/**
 * A JVM binary that is built by Gradle.
 */
@Incubating
public interface ProjectJvmBinary extends ProjectBinary {
    /**
     * The set of tasks associated with this binary.
     */
    JvmBinaryTasks getTasks();

    /**
     * The jar file output for this binary.
     */
    File getJarFile();

    /**
     * Sets the jar file output for this binary.
     */
    void setJarFile(File jarFile);

    /**
     * The classes directory for this binary.
     */
    File getClassesDir();

    /**
     * Sets the classes directory for this binary.
     */
    void setClassesDir(File classesDir);
}
