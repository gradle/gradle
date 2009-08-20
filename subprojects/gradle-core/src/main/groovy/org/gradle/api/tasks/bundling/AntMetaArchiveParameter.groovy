/*
 * Copyright 2007 the original author or authors.
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

/**
 * @author Hans Dockter
 */
class AntMetaArchiveParameter extends AntArchiveParameter {
    GradleManifest gradleManifest
    List metaInfFileSets
    String fileSetManifest

    AntMetaArchiveParameter(List resourceCollections, List mergeFileSets, List mergeGroupFileSets, String fileSetManifest,
                            boolean createIfEmpty, File destinationDir, String archiveName,
                            GradleManifest gradleManifest, List metaInfFileSets, AntBuilder ant) {
        super(resourceCollections, mergeFileSets, mergeGroupFileSets, createIfEmpty, destinationDir, archiveName, ant)
        this.gradleManifest = gradleManifest
        this.metaInfFileSets = metaInfFileSets
        this.fileSetManifest = fileSetManifest
    }

    void addToArgs(Map args) {
        if (gradleManifest?.file) {
            args.manifest = gradleManifest.file.absolutePath
        }
        args.destfile = "${destinationDir.absolutePath}/$archiveName"
        args.whenmanifestonly = emptyPolicy()
        if (fileSetManifest) {args.filesetmanifest}
    }

}
