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

import org.gradle.api.specs.Specs
import org.gradle.api.tasks.util.FileSet
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.InputFile
import org.gradle.api.artifacts.ProjectDependency

/**
 * @author Hans Dockter
 */
class War extends Jar {
    public static final String WAR_EXTENSION = 'war'

    private static Logger logger = LoggerFactory.getLogger(Jar)

    AntWar antWar = new AntWar()

    List classesFileSets = []

    List libConfigurations = []

    List libExcludeConfigurations = []

    List additionalLibFileSets = []

    List webInfFileSets = []

    File webXml

    War() {
        extension = WAR_EXTENSION
    }

    Closure createAntArchiveTask() {
        {->
            List files = dependencies(true, true)
            AntMetaArchiveParameter param = new AntMetaArchiveParameter(getResourceCollections(), getMergeFileSets(), getMergeGroupFileSets(), getFileSetManifest(),
                    getCreateIfEmpty(), getDestinationDir(), getArchiveName(), getManifest(), getMetaInfResourceCollections(), project.ant)
            antWar.execute(param, getClassesFileSets(), files, getAdditionalLibFileSets(), getWebInfFileSets(), getWebXml(), project.fileResolver)
        }
    }

    public List dependencies(boolean failForMissingDependencies, boolean includeProjectDependencies) {
        List files = []
        def filteredDependencies = {String configurationName ->
            project.configurations.getByName(configurationName).files(
                    includeProjectDependencies ? Specs.SATISFIES_ALL : { dependency -> !(dependency instanceof ProjectDependency) })  as List
        }

        getLibConfigurations().each {String configurationName ->
            files.addAll(filteredDependencies(configurationName))
        }
        getLibExcludeConfigurations().each {String configurationName ->
            files.removeAll(filteredDependencies(configurationName))
        }
        files
    }

    /**
     * Adds a fileset to the list of webinf fileset's.
     * @param args key-value pairs for setting field values of the created fileset.
     * @param configureClosure (optional) closure which is applied against the newly created fileset.
     */
    FileSet webInf(Map args, Closure configureClosure = null) {
        FileSet fileSet = createFileSetInternal(args, FileSet, configureClosure)
        webInfFileSets = getWebInfFileSets() + [fileSet]
        fileSet
    }

    FileSet classes(Map args, Closure configureClosure = null) {
        FileSet fileSet = createFileSetInternal(args, FileSet, configureClosure)
        classesFileSets = getClassesFileSets() + [fileSet]
        fileSet
    }

    FileSet additionalLibs(Map args, Closure configureClosure = null) {
        FileSet fileSet = createFileSetInternal(args, FileSet, configureClosure)
        additionalLibFileSets = getAdditionalLibFileSets() + [fileSet]
        fileSet
    }

    War libConfigurations(String ... libConfigurations) {
        List list = Arrays.asList(libConfigurations)
        this.libConfigurations = getLibConfigurations() + list
        this
    }

    War libExcludeConfigurations(String ... libExcludeConfigurations) {
        this.libExcludeConfigurations = getLibExcludeConfigurations() + Arrays.asList(libExcludeConfigurations)
        this
    }

    public AntWar getAntWar() {
        return antWar;
    }

    public void setAntWar(AntWar antWar) {
        this.antWar = antWar;
    }

    public List getClassesFileSets() {
        return classesFileSets;
    }

    public void setClassesFileSets(List classesFileSets) {
        this.classesFileSets = classesFileSets;
    }

    public List getLibConfigurations() {
        return libConfigurations;
    }

    public void setLibExcludeConfigurations(List libExcludeConfigurations) {
        this.libExcludeConfigurations = libExcludeConfigurations;
    }

    public List getLibExcludeConfigurations() {
        return libExcludeConfigurations;
    }

    public void setLibConfigurations(List libConfigurations) {
        this.libConfigurations = libConfigurations;
    }

    public List getAdditionalLibFileSets() {
        return additionalLibFileSets;
    }

    public void setAdditionalLibFileSets(List additionalLibFileSets) {
        this.additionalLibFileSets = additionalLibFileSets;
    }

    public List getWebInfFileSets() {
        return webInfFileSets;
    }

    public void setWebInfFileSets(List webInfFileSets) {
        this.webInfFileSets = webInfFileSets;
    }

    @InputFile @Optional
    public File getWebXml() {
        return webXml;
    }

    public void setWebXml(File webXml) {
        this.webXml = webXml;
    }
}
