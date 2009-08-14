/*
 * Copyright 2009 the original author or authors.
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
import org.gradle.api.file.SourceDirectorySet;

import java.io.File;

/**
 * <p>A {@code SourceSet} represents a logical group of Java source.</p>
 */
public interface SourceSet {
    /**
     * Returns the classpath used to compile this source.
     */
    FileCollection getCompileClasspath();

    /**
     * Sets the classpath used to compile this source.
     */
    void setCompileClasspath(FileCollection classpath);

    /**
     * Returns the classpath used to execute this source.
     */
    FileCollection getRuntimeClasspath();

    /**
     * Sets the classpath used to execute this source.
     */
    void setRuntimeClasspath(FileCollection classpath);

    /**
     * Returns the directory to assemble the compiled classes into.
     */
    File getClassesDir();

    /**
     * Sets the directory to assemble the compiled classes into.
     */
    void setClassesDir(File file);

    /**
     * Returns the non-Java resources which are to be copied into the compiled class output directory.
     */
    SourceDirectorySet getResources();

    /**
     * Returns the Java source which is to be compiled into the compiled class output directory.
     */
    SourceDirectorySet getJavaSource();
}
