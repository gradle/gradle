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

import org.gradle.api.JavaVersion;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.compile.HasCompileOptions;
import org.gradle.api.model.ReplacedBy;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceTask;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.util.internal.GUtil;
import org.gradle.work.DisableCachingByDefault;

import java.io.File;

/**
 * The base class for all JVM-based language compilation tasks.
 */
@DisableCachingByDefault(because = "Abstract super-class, not to be instantiated directly")
public abstract class AbstractCompile extends SourceTask {
    private final DirectoryProperty destinationDirectory;
    private FileCollection classpath;

    private String sourceCompatibility;
    private String targetCompatibility;
    private CompatibilityFormat sourceCompatibilityFormat;
    private CompatibilityFormat targetCompatibilityFormat;

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
    @Deprecated
    public String getSourceCompatibility() {
        DeprecationLogger.deprecateMethod(AbstractCompile.class, "getSourceCompatibility")
            .replaceWith("options.sourceCompatibility")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "abstract_compile_java_compatibility")
            .nagUser();

        if (this instanceof HasCompileOptions) {
            Integer version = ((HasCompileOptions) this).getOptions().getSourceCompatibility().getOrNull();
            String value = formatVersion(sourceCompatibilityFormat, version);
            return GUtil.elvis(value, sourceCompatibility); // Configuration cache hack
        } else {
            return sourceCompatibility;
        }
    }

    /**
     * Sets the Java language level to use to compile the source files.
     *
     * @param sourceCompatibility The source language level. Must not be null.
     */
    @Deprecated
    public void setSourceCompatibility(String sourceCompatibility) {
        DeprecationLogger.deprecateMethod(AbstractCompile.class, "setSourceCompatibility(String)")
            .replaceWith("options.sourceCompatibility")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(7, "abstract_compile_java_compatibility")
            .nagUser();

        sourceCompatibilityFormat = sourceCompatibility.contains(".") ?
            CompatibilityFormat.VERSION_WITH_DOT : CompatibilityFormat.MAJOR_VERSION;

        if (this instanceof HasCompileOptions) {
            int version = Integer.parseInt(JavaVersion.toVersion(sourceCompatibility).getMajorVersion());
            ((HasCompileOptions) this).getOptions().getSourceCompatibility().set(version);
        } else {
            this.sourceCompatibility = sourceCompatibility;
        }
    }

    /**
     * Returns the target JVM to generate the {@code .class} files for.
     *
     * @return The target JVM.
     */
    @Input
    @Deprecated
    public String getTargetCompatibility() {
        DeprecationLogger.deprecateMethod(AbstractCompile.class, "getTargetCompatibility()")
            .replaceWith("options.targetCompatibility")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "abstract_compile_java_compatibility")
            .nagUser();

        if (this instanceof HasCompileOptions) {
            Integer version = ((HasCompileOptions) this).getOptions().getTargetCompatibility().getOrNull();
            String value = formatVersion(targetCompatibilityFormat, version);
            return GUtil.elvis(value, targetCompatibility); // Configuration cache hack
        }
        return targetCompatibility;
    }

    /**
     * Sets the target JVM to generate the {@code .class} files for.
     *
     * @param targetCompatibility The target JVM. Must not be null.
     */
    @Deprecated
    public void setTargetCompatibility(String targetCompatibility) {
        DeprecationLogger.deprecateMethod(AbstractCompile.class, "setTargetCompatibility(String)")
            .replaceWith("options.targetCompatibility")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "abstract_compile_java_compatibility")
            .nagUser();

        targetCompatibilityFormat = targetCompatibility.contains(".") ?
            CompatibilityFormat.VERSION_WITH_DOT : CompatibilityFormat.MAJOR_VERSION;

        if (this instanceof HasCompileOptions) {
            int version = Integer.parseInt(JavaVersion.toVersion(targetCompatibility).getMajorVersion());
            ((HasCompileOptions) this).getOptions().getTargetCompatibility().set(version);
        } else {
            this.targetCompatibility = targetCompatibility;
        }
    }

    /**
     * This is a hack to ensure we return the source/target compatibility "format" which the
     * user specified if they manually set the source/target compatibility. When migrating
     * to property-based source/target compatibility, we only store the major version for the
     * source and target compatibility. To ensure we don't break users which expect the dot
     * notation to be returned when they manually set the source/target compatibility, we
     * store the format the user specified when they set the source/target compatibility.
     */
    private static String formatVersion(CompatibilityFormat format, Integer version) {
        if (version == null) {
            return null;
        }

        if (format == null || format == CompatibilityFormat.MAJOR_VERSION) {
            return Integer.toString(version);
        } else {
            return "1." + version;
        }
    }

    private enum CompatibilityFormat {
        MAJOR_VERSION, VERSION_WITH_DOT
    }
}
