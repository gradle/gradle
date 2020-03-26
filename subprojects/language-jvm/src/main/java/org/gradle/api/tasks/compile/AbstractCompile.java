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

import org.gradle.api.Incubating;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ReplacedBy;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceTask;

import java.io.File;
import java.util.concurrent.Callable;

/**
 * The base class for all JVM-based language compilation tasks.
 */
public abstract class AbstractCompile extends SourceTask {
    private final DirectoryProperty destinationDirectory;
    private FileCollection classpath;
    private String sourceCompatibility;
    private String targetCompatibility;
    private Property<Integer> release;

    public AbstractCompile() {
        this.destinationDirectory = getProject().getObjects().directoryProperty();
        this.destinationDirectory.convention(getProject().getProviders().provider(new BackwardCompatibilityOutputDirectoryConvention()));
        this.release = getProject().getObjects().property(Integer.class);
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
    @Incubating
    @OutputDirectory
    public DirectoryProperty getDestinationDirectory() {
        return destinationDirectory;
    }

    /**
     * Returns the directory to generate the {@code .class} files into.
     *
     * @return The destination directory.
     */
    @ReplacedBy("destinationDirectory")
    public File getDestinationDir() {
        return destinationDirectory.getAsFile().getOrNull();
    }

    /**
     * Sets the directory to generate the {@code .class} files into.
     *
     * @param destinationDir The destination directory. Must not be null.
     */
    public void setDestinationDir(File destinationDir) {
        this.destinationDirectory.set(destinationDir);
    }

    /**
     * Sets the directory to generate the {@code .class} files into.
     *
     * @param destinationDir The destination directory. Must not be null.
     * @since 4.0
     */
    public void setDestinationDir(Provider<File> destinationDir) {
        this.destinationDirectory.set(getProject().getLayout().dir(destinationDir));
    }

    /**
     * Configure the minimal Java release version for this compile task (--release compiler flag)
     *
     * If set, it will take precedences over the {@link #getSourceCompatibility()} and {@link #getTargetCompatibility()} settings,
     * which will have no effect in that case.
     *
     * @since 6.4
     */
    @Incubating
    @Input
    @Optional
    public Property<Integer> getRelease() {
        return release;
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

    /**
     * Convention to fall back to the 'destinationDir' output for backwards compatibility with plugins that extend AbstractCompile and override the deprecated methods.
     */
    private class BackwardCompatibilityOutputDirectoryConvention implements Callable<Directory> {
        private boolean recursiveCall;

        @Override
        public Directory call() throws Exception {
            if (recursiveCall) {
                // Already quering AbstractCompile.getDestinationDirectory() and not by a subclass implementation of that method.
                // In that case, this convention should not be used.
                return null;
            }
            recursiveCall = true;
            File legacyValue;
            try {
                // If we are not in an error case, this will most likely call a subclass implementation of getDestinationDir().
                // In the Kotlin plugin, the subclass manages it's own field which will be used here.
                legacyValue = getDestinationDir();
            } finally {
                recursiveCall = false;
            }
            if (legacyValue == null) {
                return null;
            } else {
                return getProject().getLayout().getProjectDirectory().dir(legacyValue.getAbsolutePath());
            }
        }
    }
}
