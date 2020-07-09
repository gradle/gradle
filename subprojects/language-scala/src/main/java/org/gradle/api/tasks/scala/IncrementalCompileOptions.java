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
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.LocalState;

import javax.inject.Inject;

/**
 * Options for incremental compilation of Scala code. Only used for compilation with Zinc.
 *
 * This is not sent to the compiler daemon as options.
 */
public class IncrementalCompileOptions {
    private final RegularFileProperty analysisFile;
    private final RegularFileProperty classfileBackupDir;
    private final RegularFileProperty publishedCode;

    @Inject
    public IncrementalCompileOptions(ObjectFactory objectFactory) {
        this.analysisFile = objectFactory.fileProperty();
        this.classfileBackupDir = objectFactory.fileProperty();
        this.publishedCode = objectFactory.fileProperty();
    }

    /**
     * Returns the file path where results of code analysis are to be stored.
     */
    @LocalState
    public RegularFileProperty getAnalysisFile() {
        return analysisFile;
    }

    /**
     * Returns the path to the directory where previously generated class files are backed up during the next, incremental compilation.
     * If the compilation fails, class files are restored from the backup.
     *
     * @since 6.6
     */
    @LocalState
    @Incubating
    public RegularFileProperty getClassfileBackupDir() {
        return classfileBackupDir;
    }

    /**
     * Returns the directory or archive path by which the code produced by this task
     * is published to other {@code ScalaCompile} tasks.
     */
    // only an input for other task instances
    @Internal
    public RegularFileProperty getPublishedCode() {
        return publishedCode;
    }
}
