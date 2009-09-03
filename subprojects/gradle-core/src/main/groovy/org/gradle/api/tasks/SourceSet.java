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
import org.gradle.api.file.FileTree;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.tasks.util.PatternFilterable;

import java.io.File;

/**
 * <p>A {@code SourceSet} represents a logical group of Java source and resources.</p>
 */
public interface SourceSet {
    /**
     * The name of the main source set.
     */
    String MAIN_SOURCE_SET_NAME = "main";

    /**
     * The name of the test source set.
     */
    String TEST_SOURCE_SET_NAME = "test";

    /**
     * Returns the name of this source set.
     *
     * @return The name. Never returns null.
     */
    String getName();

    /**
     * Returns the classpath used to compile this source.
     *
     * @return The classpath. Never returns null.
     */
    FileCollection getCompileClasspath();

    /**
     * Sets the classpath used to compile this source.
     *
     * @param classpath The classpath. Should not be null.
     */
    void setCompileClasspath(FileCollection classpath);

    /**
     * Returns the classpath used to execute this source.
     *
     * @return The classpath. Never returns null.
     */
    FileCollection getRuntimeClasspath();

    /**
     * Sets the classpath used to execute this source.
     *
     * @param classpath The classpath. Should not be null.
     */
    void setRuntimeClasspath(FileCollection classpath);

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
    void setClassesDir(File classesDir);

    /**
     * Returns the non-Java resources which are to be copied into the class output directory.
     *
     * @return the resources. Never returns null.
     */
    SourceDirectorySet getResources();

    /**
     * Returns the Java source which is to be compiled by the Java compiler into the class output directory.
     *
     * @return the Java source. Never returns null.
     */
    SourceDirectorySet getJava();

    /**
     * All Java source for this source set. This includes, for example, source which is directly compiled, and source
     * which is indirectly compiled through joint compilation.
     *
     * @return the Java source. Never returns null.
     */
    FileTree getAllJava();

    /**
     * Returns the patterns used to select Java source. This pattern set is applied to the files returned by {@link
     * #getAllJava()}.
     *
     * @return the patterns. Never returns null.
     */
    PatternFilterable getJavaSourcePatterns();

    /**
     * Returns the name of the Java compile task for this source set.
     *
     * @return The task name. Never returns null.
     */
    String getCompileTaskName();

    /**
     * Returns the name of the resource process task for this source set.
     *
     * @return The task name. Never returns null.
     */
    String getProcessResourcesTaskName();
}
