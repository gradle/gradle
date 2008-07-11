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
import org.gradle.api.tasks.util.FileCollection
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.List;
import java.io.File;

/**
 * @author Hans Dockter
 */
class War extends Jar {
    public static final String WAR_EXTENSION = 'war'

    private static Logger logger = LoggerFactory.getLogger(Jar)

    AntWar antWar = new AntWar()

    List classesFileSets = null

    String libConfiguration

    List additionalLibFileSets = null

    List webInfFileSets = null

    File webXml

    War(Project project, String name) {
        super(project, name)
        extension = WAR_EXTENSION
    }

    Closure createAntArchiveTask() {
        {->
            List files = getLibConfiguration() ? dependencyManager.resolve(getLibConfiguration()) : []
            antWar.execute(new AntMetaArchiveParameter(getResourceCollections(), getMergeFileSets(), getMergeGroupFileSets(), getFileSetManifest(),
                    getCreateIfEmpty(), getDestinationDir(), getArchiveName(), getManifest(), getMetaInfResourceCollections(), project.ant),
                    getClassesFileSets(), files, getAdditionalLibFileSets(), getWebInfFileSets(), getWebXml())
        }
    }

    public AntWar getAntWar() {
        return antWar;
    }

    public void setAntWar(AntWar antWar) {
        this.antWar = antWar;
    }

    public List getClassesFileSets() {
        return conv(classesFileSets, "classesFileSets");
    }

    public void setClassesFileSets(List classesFileSets) {
        this.classesFileSets = classesFileSets;
    }

    public String getLibConfiguration() {
        return conv(libConfiguration, "libConfiguration");
    }

    public void setLibConfiguration(String libConfiguration) {
        this.libConfiguration = libConfiguration;
    }

    public List getAdditionalLibFileSets() {
        return conv(additionalLibFileSets, "additionalLibFileSets");
    }

    public void setAdditionalLibFileSets(List additionalLibFileSets) {
        this.additionalLibFileSets = additionalLibFileSets;
    }

    public List getWebInfFileSets() {
        return conv(webInfFileSets, "webInfFileSets");
    }

    public void setWebInfFileSets(List webInfFileSets) {
        this.webInfFileSets = webInfFileSets;
    }

    public File getWebXml() {
        return conv(webXml, "webXml");
    }

    public void setWebXml(File webXml) {
        this.webXml = webXml;
    }
}
