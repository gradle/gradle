/*
 * Copyright 2020 the original author or authors.
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

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

/**
 * A converter which infers the class names from the file name.
 */
public class FileNameDerivingClassNameConverter implements SourceFileClassNameConverter {
    @Override
    public Collection<String> getClassNames(String sourceFileRelativePath) {
        return Collections.singleton(findClassNameForRelativePath(sourceFileRelativePath));
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public Optional<String> getRelativeSourcePath(String className) {
        int innerClassIdx = className.indexOf("$");
        String baseName = innerClassIdx>0 ? className.substring(0, innerClassIdx) : className;
        return Optional.of(baseName.replace('.', '/') + ".java");
    }

    private static String findClassNameForRelativePath(String relativePath) {
        return relativePath.replace('/', '.').replaceAll("\\.java$", "");
    }
}
