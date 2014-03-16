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

package org.gradle.api.tasks.bundling

import org.gradle.api.file.CopySpec
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.collections.FileTreeAdapter
import org.gradle.api.internal.file.collections.MapFileTree
import org.gradle.api.internal.file.copy.DefaultCopySpec
import org.gradle.api.java.archives.Manifest
import org.gradle.api.java.archives.internal.DefaultManifest
import org.gradle.internal.nativeplatform.filesystem.Chmod
import org.gradle.util.ConfigureUtil

/**
 * Assembles a JAR archive.
 */
public class Jar extends Zip {
    public static final String DEFAULT_EXTENSION = 'jar'

    private Manifest manifest
    private final DefaultCopySpec metaInf

    Jar() {
        extension = DEFAULT_EXTENSION
        manifest = new DefaultManifest(getServices().get(FileResolver))
        // Add these as separate specs, so they are not affected by the changes to the main spec
        metaInf = rootSpec.addFirst().into('META-INF')
        metaInf.addChild().from {
            MapFileTree manifestSource = new MapFileTree(temporaryDirFactory, services.get(Chmod))
            manifestSource.add('MANIFEST.MF') {OutputStream outstr ->
                Manifest manifest = getManifest() ?: new DefaultManifest(null)
                manifest.writeTo(new OutputStreamWriter(outstr))
            }
            return new FileTreeAdapter(manifestSource)
        }
        mainSpec.eachFile { FileCopyDetails details ->
            if (details.path.equalsIgnoreCase('META-INF/MANIFEST.MF')) {
                details.exclude()
            }
        }
    }

    /**
     * Returns the manifest for this JAR archive.
     * @return The manifest
     */
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
     * <p>The given closure is executed to configure the manifest. The {@link org.gradle.api.java.archives.Manifest}
     * is passed to the closure as its delegate.</p>
     *
     * @param configureClosure The closure.
     * @return This.
     */
    public Jar manifest(Closure configureClosure) {
        if (getManifest() == null) {
            manifest = new DefaultManifest(project.fileResolver)
        }
        ConfigureUtil.configure(configureClosure, getManifest());
        return this;
    }

    public CopySpec getMetaInf() {
        return metaInf.addChild()
    }

    /**
     * Adds content to this JAR archive's META-INF directory.
     *
     * <p>The given closure is executed to configure a {@code CopySpec}. The {@link CopySpec} is passed to the closure
     * as its delegate.</p>
     *
     * @param configureClosure The closure.
     * @return The created {@code CopySpec}
     */
    public CopySpec metaInf(Closure configureClosure) {
        return ConfigureUtil.configure(configureClosure, getMetaInf())
    }
}