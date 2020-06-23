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
import org.gradle.api.internal.GeneratedSubclasses;
import org.gradle.api.model.ReplacedBy;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceTask;

import java.io.File;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;

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
        this.destinationDirectory.convention(getProject().getProviders().provider(new BackwardCompatibilityOutputDirectoryConvention()));
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
     *
     * TODO - move this into the class decoration
     */
    private class BackwardCompatibilityOutputDirectoryConvention implements Callable<Directory> {
        private boolean recursiveCall;

        @Override
        public Directory call() throws Exception {
            Method getter = GeneratedSubclasses.unpackType(AbstractCompile.this).getMethod("getDestinationDir");
            if (getter.getDeclaringClass() == AbstractCompile.class) {
                // Subclass has not overridden the getter, so ignore
                return null;
            }

            // Subclass has overridden the getter, so call it

            if (recursiveCall) {
                // Already querying AbstractCompile.getDestinationDirectory()
                // In that case, this convention should not be used.
                return null;
            }
            recursiveCall = true;
            File legacyValue;
            try {
                // This will call a subclass implementation of getDestinationDir(), which possibly will not call the overridden getter
                // In the Kotlin plugin, the subclass manages its own field which will be used here.
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
