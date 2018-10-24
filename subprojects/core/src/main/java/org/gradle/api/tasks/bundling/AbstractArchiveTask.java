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
package org.gradle.api.tasks.bundling;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.file.copy.CopyActionExecuter;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.AbstractCopyTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.internal.nativeplatform.filesystem.FileSystem;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.GUtil;

import javax.annotation.Nullable;
import java.io.File;
import java.util.concurrent.Callable;

/**
 * {@code AbstractArchiveTask} is the base class for all archive tasks.
 */
public abstract class AbstractArchiveTask extends AbstractCopyTask {
    // All of these field names are really long to prevent collisions with the groovy setters.
    // Groovy will try to set the private fields if given the opportunity.
    // This makes it much more difficult for this to happen accidentally.
    private final DirectoryProperty archiveDestinationDirectory = getProject().getObjects().directoryProperty();
    private final RegularFileProperty archiveFile = getProject().getObjects().fileProperty();
    private final Property<String> archiveCustomName = createStringProperty();
    private final Property<String> archiveBaseName = createStringProperty();
    private final Property<String> archiveAppendix = createStringProperty();
    private final Property<String> archiveVersion = createStringProperty();
    private final Property<String> archiveExtension = createStringProperty();
    private final Property<String> archiveClassifier = createProperty(String.class, "");
    private final Property<Boolean> archivePreserveFileTimestamps = createProperty(Boolean.class, true);
    private final Property<Boolean> archiveReproducibleFileOrder = createProperty(Boolean.class, false);

    protected AbstractArchiveTask() {
        archiveFile.set(archiveDestinationDirectory.file(getProject().provider(new Callable<CharSequence>() {
            @Override
            public CharSequence call() {
                return getArchiveName();
            }
        })));
    }

    private Property<String> createStringProperty() {
        return getProject().getObjects().property(String.class);
    }

    private <T> Property<T> createProperty(final Class<T> clazz, final T defaultValue) {
        final Property<T> prop = getProject().getObjects().property(clazz);
        prop.set(defaultValue);
        return prop;
    }

    /**
     * Returns the archive name. If the name has not been explicitly set, the pattern for the name is:
     * <code>[baseName]-[appendix]-[version]-[classifier].[extension]</code>
     *
     * @return the archive name.
     */
    @Internal("Represented as part of archiveFile")
    public String getArchiveName() {
        if (archiveCustomName.isPresent()) {
            return archiveCustomName.get();
        }
        String name = GUtil.elvis(getBaseName(), "") + maybe(getBaseName(), getAppendix());
        name += maybe(name, getVersion());
        name += maybe(name, getClassifier());
        name += GUtil.isTrue(getExtension()) ? "." + getExtension() : "";
        return name;
    }

    /**
     * Sets the archive name.
     *
     * @param name the archive name.
     */
    public void setArchiveName(String name) {
        archiveCustomName.set(name);
    }

    private static String maybe(@Nullable String prefix, @Nullable String value) {
        if (GUtil.isTrue(value)) {
            if (GUtil.isTrue(prefix)) {
                return "-".concat(value);
            } else {
                return value;
            }
        }
        return "";
    }

    /**
     * The path where the archive is constructed. The path is simply the {@code destinationDir} plus the {@code archiveName}.
     *
     * @return a File object with the path to the archive
     */
    @Internal("Represented as a part of the archiveFile")
    public File getArchivePath() {
        return getArchiveFile().get().getAsFile();
    }

    /**
     * The {@link RegularFile} where the archive is constructed.
     * The path is simply the {@code destinationDir} plus the {@code archiveName}.
     *
     * @return a {@link RegularFile} object with the path to the archive
     */
    @OutputFile
    @SuppressWarnings("DanglingJavadoc")
    public Provider<RegularFile> getArchiveFile() {
        // TODO: Turn this into an `@implSpec` annotation on the comment above:
        // https://github.com/gradle/gradle/issues/7486
        /**
         * This returns a provider of {@link RegularFile} instead of {@link RegularFileProperty} in order to
         * prevent users calling {@link org.gradle.api.provider.Property#set} and causing a plugin or users using
         * {@link AbstractArchiveTask#getArchivePath()} to break or have strange behaviour.
         * An example can be found
         * <a href="https://github.com/gradle/gradle-native/issues/893#issuecomment-430776251">here</a>.
         */
        return archiveFile;
    }

    /**
     * Returns the directory where the archive is generated into.
     *
     * @return the directory
     */
    @Internal("Represented as part of archiveFile")
    public File getDestinationDir() {
        return archiveDestinationDirectory.getAsFile().get();
    }

    public void setDestinationDir(File destinationDir) {
        archiveDestinationDirectory.set(destinationDir);
    }

    /**
     * The directory where the archive will be placed.
     */
    @Internal("Represented by the archiveFile")
    public DirectoryProperty getDestinationDirectory() {
        return archiveDestinationDirectory;
    }

    /**
     * Returns the base name of the archive.
     *
     * @return the base name. May be null.
     */
    @Nullable
    @Internal("Represented as part of archiveFile")
    public String getBaseName() {
        return archiveBaseName.getOrNull();
    }

    public void setBaseName(@Nullable String baseName) {
        this.archiveBaseName.set(baseName);
    }

    /**
     * Returns the base name of the archive.
     *
     * @return the base name. Internal property may be null.
     */
    @Internal("Represented as part of archiveFile")
    public Property<String> getArtifactBaseName() {
        return archiveBaseName;
    }

    /**
     * Returns the appendix part of the archive name, if any.
     *
     * @return the appendix. May be null
     */
    @Nullable
    @Internal("Represented as part of archiveFile")
    public String getAppendix() {
        return archiveAppendix.getOrNull();
    }

    public void setAppendix(@Nullable String appendix) {
        this.archiveAppendix.set(appendix);
    }

    /**
     * Returns the version part of the archive name, if any.
     *
     * @return the version. May be null.
     */
    @Nullable
    @Internal("Represented as part of archiveFile")
    public String getVersion() {
        return archiveVersion.getOrNull();
    }

    public void setVersion(@Nullable String version) {
        this.archiveVersion.set(version);
    }

    /**
     * Returns the version part of the archive name.
     *
     * @return the version. Internal property may be null.
     */
    @Internal("Represented as part of archiveFile")
    public Property<String> getArchiveVersion() {
        return this.archiveVersion;
    }

    /**
     * Returns the extension part of the archive name.
     */
    @Nullable
    @Internal("Represented as part of archiveFile")
    public String getExtension() {
        return archiveExtension.getOrNull();
    }

    public void setExtension(@Nullable String extension) {
        this.archiveExtension.set(extension);
    }


    /**
     * Returns the extension part of the archive name.
     */
    @Internal("Represented as part of archiveFile")
    public Property<String> getArchiveExtension() {
        return archiveExtension;
    }

    /**
     * Returns the classifier part of the archive name, if any.
     *
     * @return The classifier. May be null.
     */
    @Nullable
    @Internal("Represented as part of archiveFile")
    public String getClassifier() {
        return archiveClassifier.getOrNull();
    }

    public void setClassifier(@Nullable String classifier) {
        this.archiveClassifier.set(classifier);
    }


    /**
     * Returns the classifier part of the archive name, if any.
     *
     * @return The classifier. Internal property may be null.
     */
    @Internal("Represented as part of archiveFile")
    public Property<String> getArchiveClassifier() {
        return archiveClassifier;
    }

    /**
     * Specifies the destination directory *inside* the archive for the files.
     * The destination is evaluated as per {@link org.gradle.api.Project#file(Object)}.
     * Don't mix it up with {@link #getDestinationDir()} which specifies the output directory for the archive.
     *
     * @param destPath destination directory *inside* the archive for the files
     * @return this
     */
    @Override
    public AbstractArchiveTask into(Object destPath) {
        super.into(destPath);
        return this;
    }

    /**
     * Creates and configures a child {@code CopySpec} with a destination directory *inside* the archive for the files.
     * The destination is evaluated as per {@link org.gradle.api.Project#file(Object)}.
     * Don't mix it up with {@link #getDestinationDir()} which specifies the output directory for the archive.
     *
     * @param destPath destination directory *inside* the archive for the files
     * @param configureClosure The closure to use to configure the child {@code CopySpec}.
     * @return this
     */
    @Override
    public AbstractArchiveTask into(Object destPath, Closure configureClosure) {
        super.into(destPath, configureClosure);
        return this;
    }


    /**
     * Creates and configures a child {@code CopySpec} with a destination directory *inside* the archive for the files.
     * The destination is evaluated as per {@link org.gradle.api.Project#file(Object)}.
     * Don't mix it up with {@link #getDestinationDir()} which specifies the output directory for the archive.
     *
     * @param destPath destination directory *inside* the archive for the files
     * @param copySpec The closure to use to configure the child {@code CopySpec}.
     * @return this
     */
    @Override
    public CopySpec into(Object destPath, Action<? super CopySpec> copySpec) {
        super.into(destPath, copySpec);
        return this;
    }

    /**
     * Specifies whether file timestamps should be preserved in the archive.
     * <p>
     * If <tt>false</tt> this ensures that archive entries have the same time for builds between different machines, Java versions and operating systems.
     * </p>
     *
     * @since 3.4
     * @return <tt>true</tt> if file timestamps should be preserved for archive entries
     */
    @Input
    public boolean isPreserveFileTimestamps() {
        return archivePreserveFileTimestamps.get();
    }

    /**
     * Specifies whether file timestamps should be preserved in the archive.
     * <p>
     * If <tt>false</tt> this ensures that archive entries have the same time for builds between different machines, Java versions and operating systems.
     * </p>
     *
     * @since 3.4
     * @param preserveFileTimestamps <tt>true</tt> if file timestamps should be preserved for archive entries
     */
    public void setPreserveFileTimestamps(boolean preserveFileTimestamps) {
        this.archivePreserveFileTimestamps.set(preserveFileTimestamps);
    }

    /**
     * Specifies whether to enforce a reproducible file order when reading files from directories.
     * <p>
     * Gradle will then walk the directories on disk which are part of this archive in a reproducible order
     * independent of file systems and operating systems.
     * This helps Gradle reliably produce byte-for-byte reproducible archives.
     * </p>
     *
     * @since 3.4
     * @return <tt>true</tt> if the files should read from disk in a reproducible order.
     */
    @Input
    public boolean isReproducibleFileOrder() {
        return archiveReproducibleFileOrder.get();
    }
    /**
     * Specifies whether to enforce a reproducible file order when reading files from directories.
     * <p>
     * Gradle will then walk the directories on disk which are part of this archive in a reproducible order
     * independent of file systems and operating systems.
     * This helps Gradle reliably produce byte-for-byte reproducible archives.
     * </p>
     *
     * @since 3.4
     * @param reproducibleFileOrder <tt>true</tt> if the files should read from disk in a reproducible order.
     */
    public void setReproducibleFileOrder(boolean reproducibleFileOrder) {
        this.archiveReproducibleFileOrder.set(reproducibleFileOrder);
    }

    @Override
    protected CopyActionExecuter createCopyActionExecuter() {
        Instantiator instantiator = getInstantiator();
        FileSystem fileSystem = getFileSystem();

        return new CopyActionExecuter(instantiator, fileSystem, isReproducibleFileOrder());
    }
}
