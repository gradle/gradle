/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.recomp;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

class JavaConventionalSourceFileClassNameConverter {
    private final CompilationSourceDirs sourceDirs;

    public JavaConventionalSourceFileClassNameConverter(CompilationSourceDirs sourceDirs) {
        this.sourceDirs = sourceDirs;
    }

    public Collection<String> getClassNames(File sourceFile) {
        return sourceDirs.relativize(sourceFile)
            .map(relativePath -> Collections.singletonList(relativePath.replace('/', '.').replaceAll("\\.java$", "")))
            .orElse(Collections.emptyList());
    }
}
