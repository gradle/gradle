/*
 * Copyright 2021 the original author or authors.
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

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

/**
 * Handles classes which can affect other classes simply by being newly added to a source set.
 */
public class WellKnownSourceFileClassNameConverter implements SourceFileClassNameConverter {
    private final SourceFileClassNameConverter delegate;
    private final String fileExtension;

    public WellKnownSourceFileClassNameConverter(SourceFileClassNameConverter delegate, String fileExtension) {
        this.delegate = delegate;
        this.fileExtension = fileExtension;
    }

    @Override
    public Collection<String> getClassNames(String sourceFileRelativePath) {
        String withoutExtension = StringUtils.removeEnd(sourceFileRelativePath, fileExtension);
        if (withoutExtension.endsWith("module-info")) {
            return Collections.singleton("module-info");
        }
        if (withoutExtension.endsWith("package-info")) {
            String packageName = withoutExtension.replace('/', '.');
            return Collections.singleton(packageName);
        }
        return delegate.getClassNames(sourceFileRelativePath);
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public Optional<String> getRelativeSourcePath(String className) {
        if (className.equals("module-info") || className.endsWith("package-info")) {
            return Optional.of(className.replace('.', '/') + fileExtension);
        }
        return delegate.getRelativeSourcePath(className);
    }
}
