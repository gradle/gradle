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

import org.gradle.api.tasks.compile.CompileOptions;

import java.io.File;
import java.util.List;

public class DefaultJavaCompileSpec extends DefaultJvmLanguageCompileSpec implements JavaCompileSpec {
    private CompileOptions compileOptions;
    private File dependencyCacheDir;
    private List<File> annotationProcessorPath;

    @Override
    public CompileOptions getCompileOptions() {
        return compileOptions;
    }

    public void setCompileOptions(CompileOptions compileOptions) {
        this.compileOptions = compileOptions;
    }

    @Deprecated
    @Override
    public File getDependencyCacheDir() {
        return dependencyCacheDir;
    }

    @Deprecated
    @Override
    public void setDependencyCacheDir(File dependencyCacheDir) {
        this.dependencyCacheDir = dependencyCacheDir;
    }

    @Override
    public List<File> getAnnotationProcessorPath() {
        return annotationProcessorPath;
    }

    @Override
    public void setAnnotationProcessorPath(List<File> annotationProcessorPath) {
        this.annotationProcessorPath = annotationProcessorPath;
    }
}
