/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.jvm.tasks;

import com.google.common.collect.ImmutableList;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.copy.CopySpecInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.java.archives.internal.CustomManifestInternalWrapper;
import org.gradle.api.java.archives.internal.DefaultManifest;
import org.gradle.api.java.archives.internal.ManifestInternal;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.internal.execution.OutputChangeListener;
import org.gradle.internal.serialization.Cached;
import org.gradle.util.internal.ConfigureUtil;
import org.gradle.work.DisableCachingByDefault;

import java.nio.charset.Charset;

import static org.gradle.api.internal.lambdas.SerializableLambdas.action;

/**
 * Assembles a JAR archive.
 */
@DisableCachingByDefault(because = "Not worth caching")
public abstract class Jar extends Zip {

    public static final String DEFAULT_EXTENSION = "jar";
    private String manifestContentCharset = DefaultManifest.DEFAULT_CONTENT_CHARSET;
    private Manifest manifest;
    private final CopySpecInternal metaInf;

    public Jar() {
        getArchiveExtension().set(DEFAULT_EXTENSION);
        setMetadataCharset("UTF-8");

        manifest = new DefaultManifest(getFileResolver());
        // Add these as separate specs, so they are not affected by the changes to the main spec
        metaInf = (CopySpecInternal) getRootSpec().addFirst().into("META-INF");
        metaInf.addChild().from(manifestFileTree());
        getMainSpec().appendCachingSafeCopyAction(new ExcludeManifestAction());
    }

    private FileTreeInternal manifestFileTree() {
        final Cached<ManifestInternal> manifest = Cached.of(this::computeManifest);
        final OutputChangeListener outputChangeListener = outputChangeListener();
        return fileCollectionFactory().generated(
            getTemporaryDirFactory(),
            "MANIFEST.MF",
            action(file -> outputChangeListener.invalidateCachesFor(ImmutableList.of(file.getAbsolutePath()))),
            action(outputStream -> manifest.get().writeTo(outputStream))
        );
    }

    private ManifestInternal computeManifest() {
        Manifest manifest = getManifest();
        if (manifest == null) {
            manifest = new DefaultManifest(null);
        }
        ManifestInternal manifestInternal;
        if (manifest instanceof ManifestInternal) {
            manifestInternal = (ManifestInternal) manifest;
        } else {
            manifestInternal = new CustomManifestInternalWrapper(manifest);
        }
        manifestInternal.setContentCharset(manifestContentCharset);
        return manifestInternal;
    }

    private FileCollectionFactory fileCollectionFactory() {
        return getServices().get(FileCollectionFactory.class);
    }

    private OutputChangeListener outputChangeListener() {
        return getServices().get(OutputChangeListener.class);
    }

    /**
     * The character set used to encode JAR metadata like file names.
     * Defaults to UTF-8.
     * You can change this property but it is not recommended as JVMs expect JAR metadata to be encoded using UTF-8
     *
     * @return the character set used to encode JAR metadata like file names
     * @since 2.14
     */
    @Override
    public String getMetadataCharset() {
        return super.getMetadataCharset();
    }

    /**
     * The character set used to encode JAR metadata like file names.
     * Defaults to UTF-8.
     * You can change this property but it is not recommended as JVMs expect JAR metadata to be encoded using UTF-8
     *
     * @param metadataCharset the character set used to encode JAR metadata like file names
     * @since 2.14
     */
    @Override
    public void setMetadataCharset(String metadataCharset) {
        super.setMetadataCharset(metadataCharset);
    }

    /**
     * The character set used to encode the manifest content.
     * Defaults to UTF-8.
     * You can change this property but it is not recommended as JVMs expect manifests content to be encoded using UTF-8.
     *
     * @return the character set used to encode the manifest content
     * @since 2.14
     */
    @Input
    public String getManifestContentCharset() {
        return manifestContentCharset;
    }

    /**
     * The character set used to encode the manifest content.
     *
     * @param manifestContentCharset the character set used to encode the manifest content
     * @see #getManifestContentCharset()
     * @since 2.14
     */
    public void setManifestContentCharset(String manifestContentCharset) {
        if (manifestContentCharset == null) {
            throw new InvalidUserDataException("manifestContentCharset must not be null");
        }
        if (!Charset.isSupported(manifestContentCharset)) {
            throw new InvalidUserDataException(String.format("Charset for manifestContentCharset '%s' is not supported by your JVM", manifestContentCharset));
        }
        this.manifestContentCharset = manifestContentCharset;
    }

    /**
     * Returns the manifest for this JAR archive.
     *
     * @return The manifest
     */
    @Internal
    public Manifest getManifest() {
        return manifest;
    }

    /**
     * Sets the manifest for this JAR archive.
     *
     * @param manifest The manifest. May be null.
     */
    public void setManifest(Manifest manifest) {
        this.manifest = manifest;
    }

    /**
     * Configures the manifest for this JAR archive.
     *
     * <p>The given closure is executed to configure the manifest. The {@link org.gradle.api.java.archives.Manifest} is passed to the closure as its delegate.</p>
     *
     * @param configureClosure The closure.
     * @return This.
     */
    public Jar manifest(@DelegatesTo(Manifest.class) Closure<?> configureClosure) {
        ConfigureUtil.configure(configureClosure, forceManifest());
        return this;
    }

    /**
     * Configures the manifest for this JAR archive.
     *
     * <p>The given action is executed to configure the manifest.</p>
     *
     * @param configureAction The action.
     * @return This.
     * @since 3.5
     */
    public Jar manifest(Action<? super Manifest> configureAction) {
        configureAction.execute(forceManifest());
        return this;
    }

    private Manifest forceManifest() {
        if (manifest == null) {
            manifest = new DefaultManifest(((ProjectInternal) getProject()).getFileResolver());
        }
        return manifest;
    }

    @Internal
    public CopySpec getMetaInf() {
        return metaInf.addChild();
    }

    /**
     * Adds content to this JAR archive's META-INF directory.
     *
     * <p>The given closure is executed to configure a {@code CopySpec}. The {@link org.gradle.api.file.CopySpec} is passed to the closure as its delegate.</p>
     *
     * @param configureClosure The closure.
     * @return The created {@code CopySpec}
     */
    public CopySpec metaInf(@DelegatesTo(CopySpec.class) Closure<?> configureClosure) {
        return ConfigureUtil.configure(configureClosure, getMetaInf());
    }

    /**
     * Adds content to this JAR archive's META-INF directory.
     *
     * <p>The given action is executed to configure a {@code CopySpec}.</p>
     *
     * @param configureAction The action.
     * @return The created {@code CopySpec}
     * @since 3.5
     */
    public CopySpec metaInf(Action<? super CopySpec> configureAction) {
        CopySpec metaInf = getMetaInf();
        configureAction.execute(metaInf);
        return metaInf;
    }

    private static class ExcludeManifestAction implements Action<FileCopyDetails> {
        @Override
        public void execute(FileCopyDetails details) {
            if (details.getPath().equalsIgnoreCase("META-INF/MANIFEST.MF")) {
                details.exclude();
            }
        }
    }
}
