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

import org.gradle.api.Project
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.List

/**
* @author Hans Dockter
*/
public class Jar extends Zip {
    public static final String DEFAULT_EXTENSION = 'jar'

    private static Logger logger = LoggerFactory.getLogger(Jar)

    AntJar antJar = new AntJar()

    GradleManifest manifest

    List metaInfResourceCollections

    String fileSetManifest

    Jar(Project project, String name) {
        super(project, name);
        extension = DEFAULT_EXTENSION
    }   

    Closure createAntArchiveTask() {
        {-> antJar.execute(new AntMetaArchiveParameter(getResourceCollections(), getMergeFileSets(), getMergeGroupFileSets(), getFileSetManifest(),
                getCreateIfEmpty(), getDestinationDir(), getArchiveName(), getManifest(), getMetaInfResourceCollections(), project.ant))}
    }

    public AntJar getAntJar() {
        return antJar;
    }

    public void setAntJar(AntJar antJar) {
        this.antJar = antJar;
    }

    public GradleManifest getManifest() {
        return manifest;
    }

    public void setManifest(GradleManifest manifest) {
        this.manifest = manifest;
    }

    public List getMetaInfResourceCollections() {
        return metaInfResourceCollections;
    }

    public void setMetaInfResourceCollections(List metaInfResourceCollections) {
        this.metaInfResourceCollections = metaInfResourceCollections;
    }

    public String getFileSetManifest() {
        return fileSetManifest;
    }

    public void setFileSetManifest(String fileSetManifest) {
        this.fileSetManifest = fileSetManifest;
    }
}
