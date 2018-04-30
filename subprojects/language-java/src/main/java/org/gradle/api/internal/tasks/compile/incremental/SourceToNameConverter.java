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

import java.io.File;
import java.util.List;
import java.util.regex.Pattern;

import static java.lang.String.format;

public class SourceToNameConverter {

    private static final Pattern SOURCE_EXTENSION = Pattern.compile("\\.java$");

    private CompilationSourceDirs sourceDirs;

    public SourceToNameConverter(CompilationSourceDirs sourceDirs) {
        this.sourceDirs = sourceDirs;
    }

    public String getClassName(File javaSourceClass) {
        List<File> dirs = sourceDirs.getSourceRoots();
        for (File sourceDir : dirs) {
            String javaSourceClassAbsolutePath = javaSourceClass.getAbsolutePath();
            String sourceDirAbsolutePath = sourceDir.getAbsolutePath();
            if (javaSourceClassAbsolutePath.startsWith(sourceDirAbsolutePath) && (javaSourceClassAbsolutePath.charAt(sourceDirAbsolutePath.length()) == File.separatorChar)) {
                String relativePath = javaSourceClassAbsolutePath.substring(sourceDirAbsolutePath.length() + 1);
                return SOURCE_EXTENSION.matcher(relativePath).replaceFirst("").replace(File.separatorChar, '.');
            }
        }
        throw new IllegalArgumentException(format("Unable to find source java class: '%s' because it does not belong to any of the source dirs: '%s'",
                javaSourceClass, dirs));

    }
}
