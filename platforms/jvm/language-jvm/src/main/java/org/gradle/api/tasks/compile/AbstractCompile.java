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
import org.gradle.api.model.ReplacedBy;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceTask;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor;
import org.gradle.internal.instrumentation.api.annotations.ReplacedDeprecation;
import org.gradle.internal.instrumentation.api.annotations.ReplacedDeprecation.RemovedIn;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;
import org.gradle.work.DisableCachingByDefault;

import java.io.File;

import static org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor.AccessorType.GETTER;
import static org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor.AccessorType.SETTER;
import static org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty.BinaryCompatibility.ACCESSORS_KEPT;

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
    @ToBeReplacedByLazyProperty
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
    @ReplacesEagerProperty(
        replacedAccessors = {
            @ReplacedAccessor(value = GETTER, name = "getDestinationDir"),
            @ReplacedAccessor(value = SETTER, name = "setDestinationDir")
        },
        binaryCompatibility = ACCESSORS_KEPT,
        deprecation = @ReplacedDeprecation(removedIn = RemovedIn.GRADLE9, withUpgradeGuideMajorVersion = 7, withUpgradeGuideSection = "compile_task_wiring")
    )
    public DirectoryProperty getDestinationDirectory() {
        return destinationDirectory;
    }

    /**
     * Returns the directory to generate the {@code .class} files into.
     *
     * @return The destination directory.
     *
     * @deprecated Use {@link #getDestinationDirectory()} instead. This method will be removed in Gradle 9.0.
     */
    @ReplacedBy("destinationDirectory")
    @Deprecated
    public File getDestinationDir() {
        // Used in Kotlin plugin - needs updating there and bumping the version first. Followup with https://github.com/gradle/gradle/issues/16783
        DeprecationLogger.deprecateProperty(AbstractCompile.class, "destinationDir")
            .replaceWith("destinationDirectory")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(7, "compile_task_wiring")
            .nagUser();

        return destinationDirectory.getAsFile().getOrNull();
    }

    /**
     * Sets the directory to generate the {@code .class} files into.
     *
     * @param destinationDir The destination directory. Must not be null.
     *
     * @deprecated Use {@link #getDestinationDirectory()}.set() instead. This method will be removed in Gradle 9.0.
     */
    @Deprecated
    public void setDestinationDir(File destinationDir) {
        DeprecationLogger.deprecateProperty(AbstractCompile.class, "destinationDir")
            .replaceWith("destinationDirectory")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(7, "compile_task_wiring")
            .nagUser();

        this.destinationDirectory.set(destinationDir);
    }

    /**
     * Sets the directory to generate the {@code .class} files into.
     *
     * @param destinationDir The destination directory. Must not be null.
     * @since 4.0
     *
     * @deprecated Use {@link #getDestinationDirectory()}.set() instead. This method will be removed in Gradle 9.0.
     */
    @Deprecated
    public void setDestinationDir(Provider<File> destinationDir) {
        // Used by Android plugin. Followup with https://github.com/gradle/gradle/issues/16782
        DeprecationLogger.deprecateProperty(AbstractCompile.class, "destinationDir")
            .replaceWith("destinationDirectory")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(7, "compile_task_wiring")
            .nagUser();

        this.destinationDirectory.set(getProject().getLayout().dir(destinationDir));
    }

    /**
     * Returns the Java language level to use to compile the source files.
     *
     * @return The source language level.
     */
    @Input
    @ToBeReplacedByLazyProperty
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
    @ToBeReplacedByLazyProperty
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
