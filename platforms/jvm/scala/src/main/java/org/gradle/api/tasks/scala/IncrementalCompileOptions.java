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
public abstract class IncrementalCompileOptions {
    @Inject
    public IncrementalCompileOptions(@SuppressWarnings("unused") ObjectFactory objectFactory) {
    }

    /**
     * Returns the file path where results of code analysis are to be stored.
     */
    @LocalState
    public abstract RegularFileProperty getAnalysisFile();

    /**
     * Returns the path to the directory where previously generated class files are backed up during the next, incremental compilation.
     * If the compilation fails, class files are restored from the backup.
     *
     * @since 6.6
     */
    @LocalState
    public abstract RegularFileProperty getClassfileBackupDir();

    /**
     * Returns the directory or archive path by which the code produced by this task
     * is published to other {@code ScalaCompile} tasks.
     */
    // only an input for other task instances
    @Internal
    public abstract RegularFileProperty getPublishedCode();
}
