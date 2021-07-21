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

/**
 * Handles classes which can affect other classes simply by being newly added to a source set.
 */
public class WellKnownSourceFileClassNameConverter implements SourceFileClassNameConverter {
    private static final String MODULE_INFO = "module-info";
    private static final String PACKAGE_INFO = "package-info";

    private final SourceFileClassNameConverter delegate;
    private final String fileExtension;

    public WellKnownSourceFileClassNameConverter(SourceFileClassNameConverter delegate, String fileExtension) {
        this.delegate = delegate;
        this.fileExtension = fileExtension;
    }

    public static boolean isPackageInfo(String className) {
        return className.endsWith(PACKAGE_INFO);
    }

    public static boolean isModuleInfo(String className) {
        return className.equals(MODULE_INFO);
    }

    @Override
    public Collection<String> getClassNames(String sourceFileRelativePath) {
        String withoutExtension = StringUtils.removeEnd(sourceFileRelativePath, fileExtension);
        if (isModuleInfo(withoutExtension)) {
            return Collections.singleton(MODULE_INFO);
        }
        if (isPackageInfo(withoutExtension)) {
            String packageName = withoutExtension.replace('/', '.');
            return Collections.singleton(packageName);
        }
        return delegate.getClassNames(sourceFileRelativePath);
    }

    @Override
    public Collection<String> getRelativeSourcePaths(String className) {
        if (isModuleInfo(className) || isPackageInfo(className)) {
            return Collections.singleton(className.replace('.', '/') + fileExtension);
        }
        return delegate.getRelativeSourcePaths(className);
    }
}
