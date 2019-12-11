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

import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.provider.AbstractReadOnlyProvider;
import org.gradle.api.model.ReplacedBy;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceTask;
import org.gradle.util.DeprecationLogger;

import javax.annotation.Nullable;
import java.io.File;

/**
 * The base class for all JVM-based language compilation tasks.
 */
public abstract class AbstractCompile extends SourceTask {
    private final DirectoryProperty destinationDirectory;
    private FileCollection classpath;
    private String sourceCompatibility;
    private String targetCompatibility;

    public AbstractCompile() {
        this.destinationDirectory = getProject().getObjects().directoryProperty();
        this.destinationDirectory.convention(new BackwardCompatibilityOutputDirectoryConvention());
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
     * Returns the directory to generate the {@code .class} files into.
     *
     * @return The destination directory.
     * @deprecated use {@link #getDestinationDirectory()}
     */
    @ReplacedBy("destinationDirectory")
    @Deprecated
    public File getDestinationDir() {
        // No 'nagUserOfReplacedMethod("AbstractCompile.getDestinationDir()", "AbstractCompile.getDestinationDirectory().get()")' because this method is called by the Kotlin plugin
        return destinationDirectory.getAsFile().getOrNull();
    }

    /**
     * Sets the directory to generate the {@code .class} files into.
     *
     * @param destinationDir The destination directory. Must not be null.
     *
     * @deprecated set the value of {@link #getDestinationDirectory()} instead
     */
    @Deprecated
    public void setDestinationDir(File destinationDir) {
        DeprecationLogger.nagUserOfReplacedMethod("AbstractCompile.setDestinationDir()",
            "AbstractCompile.getDestinationDirectory().set()");
        this.destinationDirectory.set(destinationDir);
    }

    /**
     * Sets the directory to generate the {@code .class} files into.
     *
     * @param destinationDir The destination directory. Must not be null.
     *
     * @since 4.0
     * @deprecated set the value of {@link #getDestinationDirectory()} instead
     */
    @Deprecated
    public void setDestinationDir(Provider<File> destinationDir) {
        DeprecationLogger.nagUserOfReplacedMethod("AbstractCompile.setDestinationDir()",
            "AbstractCompile.getDestinationDirectory().set()");
        this.destinationDirectory.set(getProject().getLayout().dir(destinationDir));
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
    private class BackwardCompatibilityOutputDirectoryConvention extends AbstractReadOnlyProvider<Directory> {
        private boolean recursiveCall;

        @Override
        public Class<Directory> getType() {
            return Directory.class;
        }

        @Nullable
        @Override
        public Directory getOrNull() {
            if (recursiveCall) {
                // getOrNull() was called by AbstractCompile.getDestinationDirectory() and not by a subclass implementation of that method.
                // In that case, this convention should not be used.
                return null;
            }
            recursiveCall = true;
            // If we are not in an error case, this will most likely call a subclass implementation of getDestinationDir().
            // In the Kotlin plugin, the subclass manages it's own field which will be used here.
            File legacyValue = getDestinationDir();
            recursiveCall = false;
            if (legacyValue == null) {
                return null;
            } else {
                return getProject().getLayout().dir(getProject().provider(() -> legacyValue)).get();
            }
        }
    }
}
