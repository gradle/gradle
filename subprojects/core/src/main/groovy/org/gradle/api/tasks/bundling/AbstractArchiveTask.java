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
import org.gradle.api.tasks.AbstractCopyTask;
import org.gradle.api.tasks.OutputFile;
import org.gradle.util.GUtil;

import java.io.File;

/**
 * {@code AbstractArchiveTask} is the base class for all archive tasks.
 */
public abstract class AbstractArchiveTask extends AbstractCopyTask {
    private File destinationDir;
    private String customName;
    private String baseName;
    private String appendix;
    private String version;
    private String extension;
    private String classifier = "";

    /**
     * Returns the archive name. If the name has not been explicitly set, the pattern for the name is:
     * <code>[baseName]-[appendix]-[version]-[classifier].[extension]</code>
     *
     * @return the archive name.
     */
    public String getArchiveName() {
        if (customName != null) {
            return customName;
        }
        String name = GUtil.elvis(getBaseName(), "") + maybe(getBaseName(), getAppendix());
        name += maybe(name, getVersion());
        name += maybe(name, getClassifier());
        name += GUtil.isTrue(getExtension()) ? "." + getExtension() : "";
        return name;
    }

    /**
     * Sets the archive name.
     *
     * @param name the archive name.
     */
    public void setArchiveName(String name) {
        customName = name;
    }

    private String maybe(String prefix, String value) {
        if (GUtil.isTrue(value)) {
            if (GUtil.isTrue(prefix)) {
                return String.format("-%s", value);
            } else {
                return value;
            }
        }
        return "";
    }

    /**
     * The path where the archive is constructed. The path is simply the {@code destinationDir} plus the {@code archiveName}.
     *
     * @return a File object with the path to the archive
     */
    @OutputFile
    public File getArchivePath() {
        return new File(getDestinationDir(), getArchiveName());
    }

    /**
     * Returns the directory where the archive is generated into.
     *
     * @return the directory
     */
    public File getDestinationDir() {
        return destinationDir;
    }

    public void setDestinationDir(File destinationDir) {
        this.destinationDir = destinationDir;
    }

    /**
     * Returns the base name of the archive.
     *
     * @return the base name.
     */
    public String getBaseName() {
        return baseName;
    }

    public void setBaseName(String baseName) {
        this.baseName = baseName;
    }

    /**
     * Returns the appendix part of the archive name, if any.
     *
     * @return the appendix. May be null
     */
    public String getAppendix() {
        return appendix;
    }

    public void setAppendix(String appendix) {
        this.appendix = appendix;
    }

    /**
     * Returns the version part of the archive name, if any.
     *
     * @return the version. May be null.
     */
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * Returns the extension part of the archive name.
     */
    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    /**
     * Returns the classifier part of the archive name, if any.
     *
     * @return The classifier. May be null.
     */
    public String getClassifier() {
        return classifier;
    }

    public void setClassifier(String classifier) {
        this.classifier = classifier;
    }

    /**
     * Specifies the destination directory *inside* the archive for the files.
     * The destination is evaluated as per {@link org.gradle.api.Project#file(Object)}.
     * Don't mix it up with {@link #getDestinationDir()} which specifies the output directory for the archive.
     *
     * @param destPath destination directory *inside* the archive for the files
     * @return this
     */
    public AbstractArchiveTask into(Object destPath) {
        super.into(destPath);
        return this;
    }

    /**
     * Creates and configures a child {@code CopySpec} with a destination directory *inside* the archive for the files.
     * The destination is evaluated as per {@link org.gradle.api.Project#file(Object)}.
     * Don't mix it up with {@link #getDestinationDir()} which specifies the output directory for the archive.
     *
     * @param destPath destination directory *inside* the archive for the files
     * @param configureClosure The closure to use to configure the child {@code CopySpec}.
     * @return this
     */
    public AbstractArchiveTask into(Object destPath, Closure configureClosure) {
        super.into(destPath, configureClosure);
        return this;
    }

}
