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

package org.gradle.play;

import org.gradle.api.BuildableComponentSpec;
import org.gradle.api.Incubating;

import java.io.File;
import java.util.Set;

/**
 * A set of classes and resources that operate together.
 */
@Incubating
public interface JvmClasses extends BuildableComponentSpec {
    /**
     * The classes directory for this binary.
     */
    File getClassesDir();

    /**
     * Sets the classes directory for this binary.
     */
    void setClassesDir(File classesDir);

    /**
     * A set of resource directories for this binary.
     */
    Set<File> getResourceDirs();

    /**
     * Add a resource directory to this binary.
     */
    void addResourceDir(File resourceDir);
}
