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

import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceTask;

import java.io.File;

/**
 * The base class for all JVM-based language compilation tasks.
 */
public abstract class AbstractCompile extends SourceTask {
    private final Property<File> destinationDir;
    private FileCollection classpath;
    private String sourceCompatibility;
    private String targetCompatibility;

    public AbstractCompile() {
        this.destinationDir = getProject().getObjects().property(File.class);
    }

    protected abstract void compile();

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
     * Returns the directory to generate the {@code .class} files into.
     *
     * @return The destination directory.
     */
    @OutputDirectory
    public File getDestinationDir() {
        return destinationDir.getOrNull();
    }

    /**
     * Sets the directory to generate the {@code .class} files into.
     *
     * @param destinationDir The destination directory. Must not be null.
     */
    public void setDestinationDir(File destinationDir) {
        this.destinationDir.set(destinationDir);
    }

    /**
     * Sets the directory to generate the {@code .class} files into.
     *
     * @param destinationDir The destination directory. Must not be null.
     *
     * @since 4.0
     */
    public void setDestinationDir(Provider<File> destinationDir) {
        this.destinationDir.set(destinationDir);
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
