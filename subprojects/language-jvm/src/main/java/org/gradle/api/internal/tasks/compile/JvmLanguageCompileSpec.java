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

package org.gradle.api.internal.tasks.compile;

import org.gradle.api.file.FileCollection;
import org.gradle.language.base.internal.compile.CompileSpec;

import java.io.File;
import java.util.List;

public interface JvmLanguageCompileSpec extends CompileSpec {
    File getTempDir();

    void setTempDir(File tempDir);

    File getWorkingDir();

    void setWorkingDir(File workingDir);

    File getDestinationDir();

    void setDestinationDir(File destinationDir);

    FileCollection getSource();

    void setSource(FileCollection source);

    @Deprecated
    Iterable<File> getClasspath();

    @Deprecated
    void setClasspath(Iterable<File> classpath);

    List<File> getCompileClasspath();

    void setCompileClasspath(List<File> classpath);

    String getSourceCompatibility();

    void setSourceCompatibility(String sourceCompatibility);

    String getTargetCompatibility();

    void setTargetCompatibility(String targetCompatibility);
}
