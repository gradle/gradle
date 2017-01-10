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

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.internal.file.collections.FileTreeAdapter;
import org.gradle.api.internal.file.collections.MapFileTree;
import org.gradle.api.internal.file.copy.CopySpecInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.java.archives.internal.CustomManifestInternalWrapper;
import org.gradle.api.java.archives.internal.DefaultManifest;
import org.gradle.api.java.archives.internal.ManifestInternal;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.ParallelizableTask;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.util.ConfigureUtil;

import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.concurrent.Callable;

/**
 * Assembles a JAR archive.
 */
@ParallelizableTask
@CacheableTask
@Incubating
public class Jar extends Zip {

    public static final String DEFAULT_EXTENSION = "jar";
    private String manifestContentCharset = DefaultManifest.DEFAULT_CONTENT_CHARSET;
    private Manifest manifest;
    private final CopySpecInternal metaInf;

    public Jar() {
        setExtension(DEFAULT_EXTENSION);
        setMetadataCharset("UTF-8");

        manifest = new DefaultManifest(getFileResolver());
        // Add these as separate specs, so they are not affected by the changes to the main spec
        metaInf = (CopySpecInternal) getRootSpec().addFirst().into("META-INF");
        metaInf.addChild().from(new Callable<FileTreeAdapter>() {
            public FileTreeAdapter call() throws Exception {
                MapFileTree manifestSource = new MapFileTree(getTemporaryDirFactory(), getFileSystem());
                manifestSource.add("MANIFEST.MF", new Action<OutputStream>() {
                    public void execute(OutputStream outputStream) {
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
                        manifestInternal.writeTo(outputStream);
                    }
                });
                return new FileTreeAdapter(manifestSource);
            }
        });
        getMainSpec().appendCachingSafeCopyAction(new Action<FileCopyDetails>() {
            public void execute(FileCopyDetails details) {
                if (details.getPath().equalsIgnoreCase("META-INF/MANIFEST.MF")) {
                    details.exclude();
                }
            }
        });
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
    @Incubating
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
    @Incubating
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
    public Jar manifest(Closure<?> configureClosure) {
        if (getManifest() == null) {
            manifest = new DefaultManifest(((ProjectInternal) getProject()).getFileResolver());
        }

        ConfigureUtil.configure(configureClosure, getManifest());
        return this;
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
    public CopySpec metaInf(Closure<?> configureClosure) {
        return ConfigureUtil.configure(configureClosure, getMetaInf());
    }
}
