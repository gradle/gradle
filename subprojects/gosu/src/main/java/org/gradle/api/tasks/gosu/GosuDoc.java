/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.tasks.gosu;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.File;

/**
 * Generates HTML API documentation for Gosu source files.
 */
public class GosuDoc extends SourceTask {

    private FileCollection _classpath;
    private FileCollection _gosuClasspath;
    private File _destinationDir;
    private GosuDocOptions _gosuDocOptions = new GosuDocOptions();
    private String _title;

    @Inject
    protected IsolatedAntBuilder getAntBuilder() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the target directory to generate the API documentation.
     * @return the target directory to generate the API documentation.
     */
    @OutputDirectory
    public File getDestinationDir() {
        return _destinationDir;
    }

    public void setDestinationDir(File destinationDir) {
        _destinationDir = destinationDir;
    }

    /**
     * <p>Returns the classpath to use to locate classes referenced by the documented source.</p>
     *
     * @return The classpath.
     */
    @InputFiles
    public FileCollection getClasspath() {
        return _classpath;
    }

    public void setClasspath(FileCollection classpath) {
        _classpath = classpath;
    }

    /**
     * Returns the classpath to use to load the Gosu runtime.
     */
    @InputFiles
    public FileCollection getGosuClasspath() {
        return _gosuClasspath;
    }

    public void setGosuClasspath(FileCollection gosuClasspath) {
        _gosuClasspath = gosuClasspath;
    }

    /**
     * Returns the gosu-doc generation options.
     * @return the gosu-doc options
     */
    @Nested
    public GosuDocOptions getGosuDocOptions() {
        return _gosuDocOptions;
    }

    public void setGosuDocOptions(GosuDocOptions gosuDocOptions) {
        _gosuDocOptions = gosuDocOptions;
    }

    /**
     * Returns the documentation title.
     * @return the documentation title.
     */
    @Input
    @Optional
    public String getTitle() {
        return _title;
    }

    public void setTitle(String title) {
        this._title = title;
    }

    @TaskAction
    protected void generate() {
        GosuDocOptions options = getGosuDocOptions();
        if (options.getTitle() != null && !options.getTitle().isEmpty()) {
            options.setTitle(getTitle());
        }
        AntGosuDoc antGosuDoc = new AntGosuDoc(getAntBuilder());
        antGosuDoc.execute(getSource(), getDestinationDir(), getClasspath(), getGosuClasspath(), options, getProject());
    }
}
