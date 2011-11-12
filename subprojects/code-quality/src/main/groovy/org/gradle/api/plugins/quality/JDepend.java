/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.plugins.quality;

import java.io.File;

import org.gradle.api.DefaultTask;
import org.gradle.api.plugins.quality.internal.AntJDepend;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.VerificationTask;

/**
 * <p>
 * Gradle task that runs a JDepend analysis on your code.
 * </p>
 * <p>
 * This implementation uses the {@link AntJDepend} class to do the work.
 * </p>
 * <p>
 * See {link: http://clarkware.com/software/JDepend.html} for more information
 * about the tool.
 * </p>
 */
public class JDepend extends DefaultTask implements VerificationTask {
    private File classesDir;
    private File resultsFile;
    private boolean ignoreFailures;
    
    private AntJDepend antJDepend = new AntJDepend();
    
    /**
     * Runs the JDepend analysis.
     * 
     * <ul>
     * <li>{@code classesDir} specifies the directory to analyze</li>
     * <li>{@code resultsFile} specifies where the XML results will 
     * be generated.</li>
     * </ul>
     */
    @TaskAction
    void check() {
        antJDepend.call(getAnt(), getProject(), getClassesDir(),
            getResultsFile(), isIgnoreFailures());
    }

    /**
     * Gets the directory containing the classes to analyze. 
     * @return the classesDir
     */
    @InputDirectory
    @SkipWhenEmpty
    public File getClassesDir() {
        return classesDir;
    }

    /**
     * Sets the directory containing the classes to analyze.
     * @param classesDir the classesDir to set
     */
    public void setClassesDir(File classesDir) {
        this.classesDir = classesDir;
    }

    /**
     * Gets the file that will contain the XMl results.
     * @return the resultsFile
     */
    @OutputFile
    public File getResultsFile() {
        return resultsFile;
    }

    /**
     * Sets the file that will contain the XMl results.
     * @param resultsFile the resultsFile to set
     */
    public void setResultsFile(File resultsFile) {
        this.resultsFile = resultsFile;
    }

    /**
     * Gets whether this task will ignore failures and continue running
     * the build.
     * @return the ignoreFailures
     */
    public boolean isIgnoreFailures() {
        return ignoreFailures;
    }

    /**
     * Sets whether this task will ignore failures and continue running
     * the build.
     * @param ignoreFailures the ignoreFailures to set
     * @return {@code this}
     */
    public VerificationTask setIgnoreFailures(boolean ignoreFailures) {
        this.ignoreFailures = ignoreFailures;
        return this;
    }
}
