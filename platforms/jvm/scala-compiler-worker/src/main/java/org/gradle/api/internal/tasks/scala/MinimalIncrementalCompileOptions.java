/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.tasks.scala;

import javax.annotation.Nullable;
import java.io.File;
import java.io.Serializable;

/**
 * Counterpart to {@link org.gradle.api.tasks.scala.IncrementalCompileOptions} intended to be
 * serialized and loaded by Scala compiler worker in a separate process.
 */
public class MinimalIncrementalCompileOptions implements Serializable {

    private final File analysisFile;
    private final File classfileBackupDir;
    private final File publishedCode;

    public MinimalIncrementalCompileOptions(
        File analysisFile,
        File classfileBackupDir,
        @Nullable File publishedCode
    ) {
        this.analysisFile = analysisFile;
        this.classfileBackupDir = classfileBackupDir;
        this.publishedCode = publishedCode;
    }

    public File getAnalysisFile() {
        return analysisFile;
    }

    public File getClassfileBackupDir() {
        return classfileBackupDir;
    }

    @Nullable
    public File getPublishedCode() {
        return publishedCode;
    }
}
