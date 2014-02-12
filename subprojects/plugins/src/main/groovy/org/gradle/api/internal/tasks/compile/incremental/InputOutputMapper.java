/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental;

import org.gradle.util.GFileUtils;

import java.io.File;

import static java.lang.String.format;

public class InputOutputMapper {

    private Iterable<File> sourceDirs;
    private File compileDestination;

    public InputOutputMapper(Iterable<File> sourceDirs, File compileDestination) {
        this.sourceDirs = sourceDirs;
        this.compileDestination = compileDestination;
    }

    public JavaSourceClass toJavaSourceClass(File javaSourceClass) {
        for (File sourceDir : sourceDirs) {
            if (javaSourceClass.getAbsolutePath().startsWith(sourceDir.getAbsolutePath())) { //perf tweak only
                String relativePath = GFileUtils.relativePath(sourceDir, javaSourceClass);
                if (!relativePath.startsWith("..")) {
                    return new JavaSourceClass(relativePath, compileDestination);
                }
            }
        }
        throw new IllegalArgumentException(format("Unable to find source java class: '%s' because it does not belong to any of the source dirs: '%s'",
                javaSourceClass, sourceDirs));

    }

    public JavaSourceClass toJavaSourceClass(String className) {
        String relativePath = className.replaceAll("\\.", "/").concat(".java");
        for (File sourceDir : sourceDirs) {
            File sourceFile = new File(sourceDir, relativePath);
            if (sourceFile.isFile()) {
                return new JavaSourceClass(relativePath, compileDestination);
            }
        }
        throw new IllegalArgumentException(format("Unable to find source java class for '%s'. The source file '%s' was not found in source dirs: '%s'",
                className, relativePath, sourceDirs));
    }
}