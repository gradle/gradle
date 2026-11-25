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
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.provider.EagerPropertySupport;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceTask;
import org.gradle.internal.instrumentation.api.annotations.BackedByLazyProperty;
import org.gradle.internal.instrumentation.api.annotations.LazyProperties;
import org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor;
import org.gradle.internal.instrumentation.api.annotations.ReplacedDeprecation;
import org.gradle.internal.instrumentation.api.annotations.ReplacedDeprecation.RemovedIn;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;
import org.gradle.work.DisableCachingByDefault;

import static org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor.AccessorType.GETTER;
import static org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor.AccessorType.SETTER;

/**
 * The base class for all JVM-based language compilation tasks.
 * @since 0.9
 */
@DisableCachingByDefault(because = "Abstract super-class, not to be instantiated directly")
public abstract class AbstractCompile extends SourceTask {
    private final DirectoryProperty destinationDirectory;

    /**
     * Creates a new {@code AbstractCompile}.
     *
     * @since 0.9
     */
    @SuppressWarnings("this-escape")
    public AbstractCompile() {
        this.destinationDirectory = getProject().getObjects().directoryProperty();
    }

    /**
     * {@inheritDoc}
     *
     * @since 10.0.0
     */
    @Override
    @Incubating
    @Internal
    @LazyProperties
    public abstract AbstractCompileInputs getLazyProperties();

    /**
     * Returns the classpath to use to compile the source files.
     *
     * @return The classpath.
     * @since 0.9
     */
    @Classpath
    @ToBeReplacedByLazyProperty
    @BackedByLazyProperty("lazyProperties.classpath")
    public FileCollection getClasspath() {
        return EagerPropertySupport.getEager(getLazyProperties().getClasspath());
    }

    /**
     * Sets the classpath to use to compile the source files.
     *
     * @param configuration The classpath. Must not be null, but may be empty.
     * @since 0.9
     */
    public void setClasspath(FileCollection configuration) {
        getLazyProperties().getClasspath().setFrom(configuration);
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
        deprecation = @ReplacedDeprecation(removedIn = RemovedIn.GRADLE9, withUpgradeGuideMajorVersion = 7, withUpgradeGuideSection = "compile_task_wiring")
    )
    public DirectoryProperty getDestinationDirectory() {
        return destinationDirectory;
    }

    /**
     * Returns the Java language level to use to compile the source files.
     *
     * @return The source language level.
     * @since 0.9
     */
    @Input
    @ToBeReplacedByLazyProperty
    @BackedByLazyProperty("lazyProperties.sourceCompatibility")
    public String getSourceCompatibility() {
        return getLazyProperties().getSourceCompatibility().getOrNull();
    }

    /**
     * Sets the Java language level to use to compile the source files.
     *
     * @param sourceCompatibility The source language level. Must not be null.
     * @since 0.9
     */
    public void setSourceCompatibility(String sourceCompatibility) {
        EagerPropertySupport.setEager(getLazyProperties().getSourceCompatibility(), sourceCompatibility);
    }

    /**
     * Returns the target JVM to generate the {@code .class} files for.
     *
     * @return The target JVM.
     * @since 0.9
     */
    @Input
    @ToBeReplacedByLazyProperty
    @BackedByLazyProperty("lazyProperties.targetCompatibility")
    public String getTargetCompatibility() {
        return getLazyProperties().getTargetCompatibility().getOrNull();
    }

    /**
     * Sets the target JVM to generate the {@code .class} files for.
     *
     * @param targetCompatibility The target JVM. Must not be null.
     * @since 0.9
     */
    public void setTargetCompatibility(String targetCompatibility) {
        EagerPropertySupport.setEager(getLazyProperties().getTargetCompatibility(), targetCompatibility);
    }
}
