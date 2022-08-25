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
package org.gradle.api.tasks.compile;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceTask;
import org.gradle.work.DisableCachingByDefault;

/**
 * The base class for all JVM-based language compilation tasks.
 */
@DisableCachingByDefault(because = "Abstract super-class, not to be instantiated directly")
public abstract class AbstractCompile extends SourceTask {
    private final DirectoryProperty destinationDirectory;
    private FileCollection classpath;
    private String sourceCompatibility;
    private String targetCompatibility;

    public AbstractCompile() {
        this.destinationDirectory = getProject().getObjects().directoryProperty();
    }

    /**
     * Returns the classpath to use to compile the source files.
     *
     * @return The classpath.
     */
    @Classpath
    public FileCollection getClasspath() {
        return classpath;
    }

    /**
     * Sets the classpath to use to compile the source files.
     *
     * @param configuration The classpath. Must not be null, but may be empty.
     */
    public void setClasspath(FileCollection configuration) {
        this.classpath = configuration;
    }

    /**
     * Returns the directory property that represents the directory to generate the {@code .class} files into.
     *
     * @return The destination directory property.
     * @since 6.1
     */
    @OutputDirectory
    public DirectoryProperty getDestinationDirectory() {
        return destinationDirectory;
    }

    /**
     * Returns the Java language level to use to compile the source files.
     *
     * @return The source language level.
     */
    @Input
    public String getSourceCompatibility() {
        return sourceCompatibility;
    }

    /**
     * Sets the Java language level to use to compile the source files.
     *
     * @param sourceCompatibility The source language level. Must not be null.
     */
    public void setSourceCompatibility(String sourceCompatibility) {
        this.sourceCompatibility = sourceCompatibility;
    }

    /**
     * Returns the target JVM to generate the {@code .class} files for.
     *
     * @return The target JVM.
     */
    @Input
    public String getTargetCompatibility() {
        return targetCompatibility;
    }

    /**
     * Sets the target JVM to generate the {@code .class} files for.
     *
     * @param targetCompatibility The target JVM. Must not be null.
     */
    public void setTargetCompatibility(String targetCompatibility) {
        this.targetCompatibility = targetCompatibility;
    }
}
