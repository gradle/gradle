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

import org.apache.commons.lang.StringUtils;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A converter which infers the class names from the file name.
 */
public class FileNameDerivingClassNameConverter implements SourceFileClassNameConverter {
    private final SourceFileClassNameConverter delegate;
    private final Set<String> fileExtensions;

    public FileNameDerivingClassNameConverter(SourceFileClassNameConverter delegate, Set<String> fileExtensions) {
        this.delegate = delegate;
        this.fileExtensions = fileExtensions;
    }

    @Override
    public Set<String> getClassNames(String sourceFileRelativePath) {
        Set<String> classNames = delegate.getClassNames(sourceFileRelativePath);
        if (!classNames.isEmpty()) {
            return classNames;
        }

        for (String fileExtension : fileExtensions) {
            if (sourceFileRelativePath.endsWith(fileExtension)) {
                return Collections.singleton(StringUtils.removeEnd(sourceFileRelativePath.replace('/', '.'), fileExtension));
            }
        }

        return Collections.emptySet();
    }

    @Override
    public Set<String> getRelativeSourcePaths(String className) {
        Set<String> sourcePaths = delegate.getRelativeSourcePaths(className);
        if (!sourcePaths.isEmpty()) {
            return sourcePaths;
        }

        Set<String> paths = fileExtensions.stream()
            .map(fileExtension -> classNameToRelativePath(className, fileExtension))
            .collect(Collectors.toSet());

        // Classes with $ may be inner classes
        int innerClassIdx = className.indexOf("$");
        if (innerClassIdx > 0) {
            String baseName = className.substring(0, innerClassIdx);
            fileExtensions.stream()
                .map(fileExtension -> classNameToRelativePath(baseName, fileExtension))
                .forEach(paths::add);
        }

        return paths;
    }

    private String classNameToRelativePath(String className, String fileExtension) {
        return className.replace('.', '/') + fileExtension;
    }
}
