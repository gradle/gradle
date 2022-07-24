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

package org.gradle.api.internal.tasks.compile.incremental.deps;

import java.io.File;

import static org.gradle.util.internal.RelativePathUtil.relativePath;

class OutputToNameConverter {

    private final File compiledClassesDir;

    public OutputToNameConverter(File compiledClassesDir) {
        this.compiledClassesDir = compiledClassesDir;
    }

    public String getClassName(File classFile) {
        String path = relativePath(compiledClassesDir, classFile);
        if (path.startsWith("/") || path.startsWith(".")) {
            throw new IllegalArgumentException("Given input class file: '" + classFile + "' is not located inside of '" + compiledClassesDir + "'.");
        }
        return path.replaceAll("/", ".").replaceAll("\\.class", "");
    }
}
