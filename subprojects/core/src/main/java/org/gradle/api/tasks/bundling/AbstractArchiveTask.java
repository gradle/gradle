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
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.model.ReplacedBy;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.AbstractCopyTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.GUtil;

import javax.annotation.Nullable;
import java.io.File;

/**
 * {@code AbstractArchiveTask} is the base class for all archive tasks.
 */
public abstract class AbstractArchiveTask extends AbstractCopyTask {
    // All of these field names are really long to prevent collisions with the groovy setters.
    // Groovy will try to set the private fields if given the opportunity.
    // This makes it much more difficult for this to happen accidentally.
    private final DirectoryProperty archiveDestinationDirectory;
    private final RegularFileProperty archiveFile;
    private final Property<String> archiveName;
    private final Property<String> archiveBaseName;
    private final Property<String> archiveAppendix;
    private final Property<String> archiveVersion;
    private final Property<String> archiveExtension;
    private final Property<String> archiveClassifier;
    private final Property<Boolean> archivePreserveFileTimestamps;
    private final Property<Boolean> archiveReproducibleFileOrder;

    public AbstractArchiveTask() {
        ObjectFactory objectFactory = getProject().getObjects();

        archiveDestinationDirectory = objectFactory.directoryProperty();
        archiveBaseName = objectFactory.property(String.class);
        archiveAppendix = objectFactory.property(String.class);
        archiveVersion = objectFactory.property(String.class);
        archiveExtension = objectFactory.property(String.class);
        archiveClassifier = objectFactory.property(String.class).convention("");

        archiveName = objectFactory.property(String.class);
        archiveName.convention(getProject().provider(() -> {
            // [baseName]-[appendix]-[version]-[classifier].[extension]
            String name = GUtil.elvis(archiveBaseName.getOrNull(), "");
            name += maybe(name, archiveAppendix.getOrNull());
            name += maybe(name, archiveVersion.getOrNull());
            name += maybe(name, archiveClassifier.getOrNull());

            String extension = archiveExtension.getOrNull();
            name += GUtil.isTrue(extension) ? "." + extension : "";
            return name;
        }));

        archiveFile = objectFactory.fileProperty();
        archiveFile.convention(archiveDestinationDirectory.file(archiveName));

        archivePreserveFileTimestamps = objectFactory.property(Boolean.class).convention(true);
        archiveReproducibleFileOrder = objectFactory.property(Boolean.class).convention(false);
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
     * Returns the archive name. If the name has not been explicitly set, the pattern for the name is:
     * <code>[archiveBaseName]-[archiveAppendix]-[archiveVersion]-[archiveClassifier].[archiveExtension]</code>
     *
     * @return the archive name.
     * @deprecated Use {@link #getArchiveFileName()}
     */
    @Deprecated
    @ReplacedBy("archiveFileName")
    public String getArchiveName() {
        // This is used by the Kotlin plugin, we should upstream a fix to avoid this API first.
        // DeprecationLogger.deprecateProperty("archiveName").replaceWith("archiveFileName").willBeRemovedInNextMajorVersion().withDslReference(AbstractArchiveTask.class).nagUser();
        return archiveName.get();
    }

    /**
     * Sets the archive name.
     *
     * @param name the archive name.
     * @deprecated Use {@link #getArchiveFileName()}
     */
    @Deprecated
    public void setArchiveName(String name) {
        DeprecationLogger.deprecateProperty(AbstractArchiveTask.class, "archiveName").replaceWith("archiveFileName")
            .willBeRemovedInGradle7()
            .withDslReference()
            .nagUser();
        archiveName.convention(name);
        archiveName.set(name);
    }

    /**
     * Returns the archive name. If the name has not been explicitly set, the pattern for the name is:
     * <code>[archiveBaseName]-[archiveAppendix]-[archiveVersion]-[archiveClassifier].[archiveExtension]</code>
     *
     * @return the archive name.
     * @since 5.1
     */
    @Internal("Represented as part of archiveFile")
    public Property<String> getArchiveFileName() {
        return archiveName;
    }

    /**
     * The path where the archive is constructed. The path is simply the {@code destinationDirectory} plus the {@code archiveFileName}.
     *
     * @return a File object with the path to the archive
     * @deprecated Use {@link #getArchiveFile()}
     */
    @Deprecated
    @ReplacedBy("archiveFile")
    public File getArchivePath() {
        // This is used by the Kotlin plugin, we should upstream a fix to avoid this API first.
        // DeprecationLogger.nagUserWith(DeprecationMessage.replacedProperty("archivePath", "archiveFile"));
        return getArchiveFile().get().getAsFile();
    }

    /**
     * The {@link RegularFile} where the archive is constructed.
     * The path is simply the {@code destinationDirectory} plus the {@code archiveFileName}.
     *
     * @return a {@link RegularFile} object with the path to the archive
     * @since 5.1
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
     * @deprecated Use {@link #getDestinationDirectory()}
     */
    @Deprecated
    @ReplacedBy("destinationDirectory")
    public File getDestinationDir() {
        DeprecationLogger.deprecateProperty(AbstractArchiveTask.class, "destinationDir").replaceWith("destinationDirectory").willBeRemovedInGradle7().withDslReference().nagUser();
        return archiveDestinationDirectory.getAsFile().get();
    }

    /**
     * Sets the destination dir.
     *
     * @deprecated Use {@link #getDestinationDirectory()}
     */
    @Deprecated
    public void setDestinationDir(File destinationDir) {
        DeprecationLogger.deprecateProperty(AbstractArchiveTask.class, "destinationDir").replaceWith("destinationDirectory").willBeRemovedInGradle7().withDslReference().nagUser();
        archiveDestinationDirectory.set(getProject().file(destinationDir));
    }

    /**
     * The directory where the archive will be placed.
     *
     * @since 5.1
     */
    @Internal("Represented by the archiveFile")
    public DirectoryProperty getDestinationDirectory() {
        return archiveDestinationDirectory;
    }

    /**
     * Returns the base name of the archive.
     *
     * @return the base name. May be null.
     * @deprecated Use {@link #getArchiveBaseName()}
     */
    @Nullable
    @Deprecated
    @ReplacedBy("archiveBaseName")
    public String getBaseName() {
        DeprecationLogger.deprecateProperty(AbstractArchiveTask.class, "baseName").replaceWith("archiveBaseName").willBeRemovedInGradle7().withDslReference().nagUser();
        return archiveBaseName.getOrNull();
    }

    /**
     * Sets the base name.
     *
     * @deprecated Use {@link #getArchiveBaseName()}
     */
    @Deprecated
    public void setBaseName(@Nullable String baseName) {
        DeprecationLogger.deprecateProperty(AbstractArchiveTask.class, "baseName").replaceWith("archiveBaseName").willBeRemovedInGradle7().withDslReference().nagUser();
        archiveBaseName.convention(baseName);
        archiveBaseName.set(baseName);
    }

    /**
     * Returns the base name of the archive.
     *
     * @return the base name. Internal property may be null.
     * @since 5.1
     */
    @Internal("Represented as part of archiveFile")
    public Property<String> getArchiveBaseName() {
        return archiveBaseName;
    }

    /**
     * Returns the appendix part of the archive name, if any.
     *
     * @return the appendix. May be null
     * @deprecated Use {@link #getArchiveAppendix()}
     */
    @Nullable
    @Deprecated
    @ReplacedBy("archiveAppendix")
    public String getAppendix() {
        DeprecationLogger.deprecateProperty(AbstractArchiveTask.class, "appendix").replaceWith("archiveAppendix").willBeRemovedInGradle7().withDslReference().nagUser();
        return archiveAppendix.getOrNull();
    }

    /**
     * Sets the appendix.
     *
     * @deprecated Use {@link #getArchiveAppendix()}
     */
    @Deprecated
    public void setAppendix(@Nullable String appendix) {
        // This is used by the Kotlin plugin, we should upstream a fix to avoid this API first.
        // DeprecationLogger.nagUserWith(DeprecationMessage.replacedProperty("appendix", "archiveAppendix"));
        archiveAppendix.convention(appendix);
        archiveAppendix.set(appendix);
    }

    /**
     * Returns the appendix part of the archive name, if any.
     *
     * @return the appendix. May be null
     * @since 5.1
     */
    @Internal("Represented as part of archiveFile")
    public Property<String> getArchiveAppendix() {
        return archiveAppendix;
    }

    /**
     * Returns the version part of the archive name, if any.
     *
     * @return the version. May be null.
     * @deprecated Use {@link #getArchiveVersion()}
     */
    @Nullable
    @Deprecated
    @ReplacedBy("archiveVersion")
    public String getVersion() {
        DeprecationLogger.deprecateProperty(AbstractArchiveTask.class, "version").replaceWith("archiveVersion").willBeRemovedInGradle7().withDslReference().nagUser();
        return archiveVersion.getOrNull();
    }

    /**
     * Sets the version.
     *
     * @deprecated Use {@link #getArchiveVersion()}
     */
    @Deprecated
    public void setVersion(@Nullable String version) {
        DeprecationLogger.deprecateProperty(AbstractArchiveTask.class, "version").replaceWith("archiveVersion").willBeRemovedInGradle7().withDslReference().nagUser();
        archiveVersion.convention(version);
        archiveVersion.set(version);
    }

    /**
     * Returns the version part of the archive name.
     *
     * @return the version. Internal property may be null.
     * @since 5.1
     */
    @Internal("Represented as part of archiveFile")
    public Property<String> getArchiveVersion() {
        return archiveVersion;
    }

    /**
     * Returns the extension part of the archive name.
     *
     * @deprecated Use {@link #getArchiveExtension()}
     */
    @Nullable
    @Deprecated
    @ReplacedBy("archiveExtension")
    public String getExtension() {
        DeprecationLogger.deprecateProperty(AbstractArchiveTask.class, "extension").replaceWith("archiveExtension").willBeRemovedInGradle7().withDslReference().nagUser();
        return archiveExtension.getOrNull();
    }

    /**
     * Sets the extension.
     *
     * @deprecated Use {@link #getArchiveExtension()}
     */
    @Deprecated
    public void setExtension(@Nullable String extension) {
        DeprecationLogger.deprecateProperty(AbstractArchiveTask.class, "extension").replaceWith("archiveExtension").willBeRemovedInGradle7().withDslReference().nagUser();
        archiveExtension.convention(extension);
        archiveExtension.set(extension);
    }

    /**
     * Returns the extension part of the archive name.
     *
     * @since 5.1
     */
    @Internal("Represented as part of archiveFile")
    public Property<String> getArchiveExtension() {
        return archiveExtension;
    }

    /**
     * Returns the classifier part of the archive name, if any.
     *
     * @return The classifier. May be null.
     * @deprecated Use {@link #getArchiveClassifier()}
     */
    @Nullable
    @Deprecated
    @ReplacedBy("archiveClassifier")
    public String getClassifier() {
        DeprecationLogger.deprecateProperty(AbstractArchiveTask.class, "classifier").replaceWith("archiveClassifier").willBeRemovedInGradle7().withDslReference().nagUser();
        return archiveClassifier.getOrNull();
    }

    /**
     * Sets the classifier.
     *
     * @deprecated Use {@link #getArchiveClassifier()}
     */
    @Deprecated
    public void setClassifier(@Nullable String classifier) {
        // This is used by the Kotlin plugin, we should upstream a fix to avoid this API first.
        // DeprecationLogger.deprecateProperty(AbstractArchiveTask.class, "classifier").replaceWith("archiveClassifier").withDslReference().nagUser();
        archiveClassifier.convention(classifier);
        archiveClassifier.set(classifier);
    }

    /**
     * Returns the classifier part of the archive name, if any.
     *
     * @return The classifier. Internal property may be null.
     * @since 5.1
     */
    @Internal("Represented as part of archiveFile")
    public Property<String> getArchiveClassifier() {
        return archiveClassifier;
    }

    /**
     * Specifies the destination directory *inside* the archive for the files.
     * The destination is evaluated as per {@link org.gradle.api.Project#file(Object)}.
     * Don't mix it up with {@link #getDestinationDirectory()} which specifies the output directory for the archive.
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
     * Don't mix it up with {@link #getDestinationDirectory()} which specifies the output directory for the archive.
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
     * Don't mix it up with {@link #getDestinationDirectory()} which specifies the output directory for the archive.
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
     * @return <tt>true</tt> if file timestamps should be preserved for archive entries
     * @since 3.4
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
     * @param preserveFileTimestamps <tt>true</tt> if file timestamps should be preserved for archive entries
     * @since 3.4
     */
    public void setPreserveFileTimestamps(boolean preserveFileTimestamps) {
        archivePreserveFileTimestamps.set(preserveFileTimestamps);
    }

    /**
     * Specifies whether to enforce a reproducible file order when reading files from directories.
     * <p>
     * Gradle will then walk the directories on disk which are part of this archive in a reproducible order
     * independent of file systems and operating systems.
     * This helps Gradle reliably produce byte-for-byte reproducible archives.
     * </p>
     *
     * @return <tt>true</tt> if the files should read from disk in a reproducible order.
     * @since 3.4
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
     * @param reproducibleFileOrder <tt>true</tt> if the files should read from disk in a reproducible order.
     * @since 3.4
     */
    public void setReproducibleFileOrder(boolean reproducibleFileOrder) {
        archiveReproducibleFileOrder.set(reproducibleFileOrder);
    }

    @Override
    protected CopyActionExecuter createCopyActionExecuter() {
        Instantiator instantiator = getInstantiator();
        FileSystem fileSystem = getFileSystem();

        return new CopyActionExecuter(instantiator, fileSystem, isReproducibleFileOrder());
    }
}
