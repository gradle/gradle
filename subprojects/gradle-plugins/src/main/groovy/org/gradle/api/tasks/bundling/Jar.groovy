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
import org.gradle.api.internal.file.MapFileTree
import org.gradle.api.java.archives.internal.DefaultManifest
import org.gradle.util.ConfigureUtil
import org.gradle.api.internal.file.copy.CopySpecImpl
import org.gradle.api.file.FileCopyDetails

/**
 * Assembles a JAR archive.
 *
 * @author Hans Dockter
 */
public class Jar extends Zip {
    public static final String DEFAULT_EXTENSION = 'jar'

    private DefaultManifest manifest
    private final CopySpecImpl metaInf

    Jar() {
        extension = DEFAULT_EXTENSION
        manifest = new DefaultManifest(project.fileResolver)
        // Add these as separate specs, so they are not affected by the changes to the main spec
        metaInf = copyAction.rootSpec.addFirst().into('META-INF')
        metaInf.addChild().from {
            MapFileTree manifestSource = new MapFileTree(temporaryDir)
            manifestSource.add('MANIFEST.MF') {OutputStream outstr ->
                DefaultManifest manifest = getManifest() ?: new DefaultManifest(null)
                manifest.writeTo(new OutputStreamWriter(outstr))
            }
            manifestSource
        }
        copyAction.mainSpec.eachFile { FileCopyDetails details ->
            if (details.path.equalsIgnoreCase('META-INF/MANIFEST.MF')) {
                details.exclude()
            }
        }
    }

    public DefaultManifest getManifest() {
        return manifest;
    }

    public void setManifest(DefaultManifest manifest) {
        this.manifest = manifest;
    }

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

    public CopySpec metaInf(Closure configureClosure) {
        return ConfigureUtil.configure(configureClosure, getMetaInf())
    }
}