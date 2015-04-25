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
package org.gradle.api.tasks.mirah;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.tasks.*;
import org.gradle.util.GUtil;

import javax.inject.Inject;
import java.io.File;

/**
 * Generates HTML API documentation for Mirah source files.
 */
public class MirahDoc extends SourceTask {

    private File destinationDir;

    private FileCollection classpath;
    private FileCollection mirahClasspath;
    private MirahDocOptions mirahDocOptions = new MirahDocOptions();
    private String title;

    @Inject
    protected IsolatedAntBuilder getAntBuilder() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the directory to generate the API documentation into.
     */
    @OutputDirectory
    public File getDestinationDir() {
        return destinationDir;
    }

    public void setDestinationDir(File destinationDir) {
        this.destinationDir = destinationDir;
    }

    /**
     * <p>Returns the classpath to use to locate classes referenced by the documented source.</p>
     *
     * @return The classpath.
     */
    @InputFiles
    public FileCollection getClasspath() {
        return classpath;
    }

    public void setClasspath(FileCollection classpath) {
        this.classpath = classpath;
    }

    /**
     * Returns the classpath to use to load the MirahDoc tool.
     */
    @InputFiles
    public FileCollection getMirahClasspath() {
        return mirahClasspath;
    }

    public void setMirahClasspath(FileCollection mirahClasspath) {
        this.mirahClasspath = mirahClasspath;
    }

    /**
     * Returns the MirahDoc generation options.
     */
    @Nested
    public MirahDocOptions getMirahDocOptions() {
        return mirahDocOptions;
    }

    public void setMirahDocOptions(MirahDocOptions mirahDocOptions) {
        this.mirahDocOptions = mirahDocOptions;
    }

    /**
     * Returns the documentation title.
     */
    @Input @Optional
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @TaskAction
    protected void generate() {
        MirahDocOptions options = getMirahDocOptions();
        if (!GUtil.isTrue(options.getDocTitle())) {
            options.setDocTitle(getTitle());
        }
        AntMirahDoc antMirahDoc = new AntMirahDoc(getAntBuilder());
        antMirahDoc.execute(getSource(), getDestinationDir(), getClasspath(), getMirahClasspath(), options);
    }

}
