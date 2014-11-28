/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.tasks.scala;

import org.gradle.api.Incubating;
import org.gradle.api.tasks.Input;

import java.io.File;
import java.io.Serializable;

/**
 * Options for incremental compilation of Scala code. Only used if
 * {@link org.gradle.api.tasks.scala.ScalaCompileOptions#isUseAnt()} is {@code false}.
 */
@Incubating
public class IncrementalCompileOptions implements Serializable {
    private static final long serialVersionUID = 0;

    private File analysisFile;
    private File publishedCode;

    /**
     * Returns the file path where results of code analysis are to be stored.
     *
     * @return the file path where which results of code analysis are to be stored
     */
    @Input
    public File getAnalysisFile() {
        return analysisFile;
    }

    /**
     * Sets the file path where results of code analysis are to be stored.
     */
    public void setAnalysisFile(File analysisFile) {
        this.analysisFile = analysisFile;
    }

    /**
     * Returns the directory or archive path by which the code produced by this task
     * is published to other {@code ScalaCompile} tasks.
     *
     * @return the directory or archive path by which the code produced by this task
     * is published to other {@code ScalaCompile} tasks
     */
    // only an input for other task instances
    public File getPublishedCode() {
        return publishedCode;
    }

    /**
     * Sets the directory or archive path by which the code produced by this task
     * is published to other {@code ScalaCompile} tasks.
     */
    public void setPublishedCode(File publishedCode) {
        this.publishedCode = publishedCode;
    }
}
