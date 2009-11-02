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

import org.gradle.api.tasks.AbstractCopyTask
import org.gradle.api.tasks.OutputFile
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author Hans Dockter
 */
public abstract class AbstractArchiveTask extends AbstractCopyTask {
    private static Logger logger = LoggerFactory.getLogger(AbstractArchiveTask.class);

    /**
     * A list with all entities (e.g. filesets) which describe the files of this archive.
     */
    private List resourceCollections = []

    /**
     * Controls if an archive gets created if no files would go into it.  
     */
    boolean createIfEmpty

    /**
     * The dir where the created archive is placed.
     */
    private File destinationDir

    /**
     * Usually the archive name is composed out of the baseName, the version and the extension. If the custom name is set,
     * solely the customName is used as the archiveName.
     */
    private String customName

    /**
     * The baseName of the archive.
     */
    private String baseName

    /**
     * The appendix of the archive.
     */
    private String appendix

    /**
     * The version part of the archive name
     */
    private String version

    /**
     * The extension part of the archive name
     */
    private String extension

    /**
     * The classifier part of the archive name. Default to an empty string. Could be 'src'. 
     */
    private String classifier = ''

    /**
     * Returns the archive name. If the customName is not set, the pattern for the name is:
     * [baseName]-[version].[extension]
     */
    public String getArchiveName() {
        if (customName) { return customName }
        getBaseName() +
                (getAppendix() ? "-${getAppendix()}" : "") +
                (getVersion() ? "-${getVersion()}" : "") +
                (getClassifier() ? "-${getClassifier()}" : "") +
                ".${getExtension()}"
    }

    /**
     * The path where the archive is constructed. The path is simply the destinationDir plus the archiveName.
     * @return a File object with the path to the archive
     */
    @OutputFile
    public File getArchivePath() {
        new File(getDestinationDir(), getArchiveName())
    }

    public List getResourceCollections() {
        return resourceCollections;
    }

    public void setResourceCollections(List resourceCollections) {
        this.resourceCollections = resourceCollections;
    }

    // todo Uncomment after refacotring to Java
//    public boolean isCreateIfEmpty() {
//        return conv(createIfEmpty, "createIfEmpty");
//    }

    public void setCreateIfEmpty(boolean createIfEmpty) {
        this.createIfEmpty = createIfEmpty;
    }

    public File getDestinationDir() {
        return destinationDir;
    }

    public void setDestinationDir(File destinationDir) {
        this.destinationDir = destinationDir;
    }

    public String getCustomName() {
        return customName;
    }

    public void setCustomName(String customName) {
        this.customName = customName;
    }

    public String getBaseName() {
        return baseName;
    }

    public void setBaseName(String baseName) {
        this.baseName = baseName;
    }

    public String getAppendix() {
        return appendix;
    }

    public void setAppendix(String appendix) {
        this.appendix = appendix;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public String getClassifier() {
        return classifier;
    }

    public void setClassifier(String classifier) {
        this.classifier = classifier;
    }
}
