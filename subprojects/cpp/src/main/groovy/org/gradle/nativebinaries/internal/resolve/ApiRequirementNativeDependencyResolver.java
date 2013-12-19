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

package org.gradle.nativebinaries.internal.resolve;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.nativebinaries.NativeDependencySet;

public class ApiRequirementNativeDependencyResolver implements NativeDependencyResolver {
    private final NativeDependencyResolver delegate;

    public ApiRequirementNativeDependencyResolver(NativeDependencyResolver delegate) {
        this.delegate = delegate;
    }

    public void resolve(NativeBinaryResolveResult nativeBinaryResolveResult) {
        delegate.resolve(nativeBinaryResolveResult);

        for (NativeBinaryRequirementResolveResult resolution : nativeBinaryResolveResult.getAllResolutions()) {
            String linkage = getLinkage(resolution);
            if ("api".equals(linkage)) {
                resolution.setLibraryBinary(null);
                resolution.setNativeDependencySet(new ApiNativeDependencySet(resolution.getNativeDependencySet()));
            }
        }
    }

    private String getLinkage(NativeBinaryRequirementResolveResult resolution) {
        if (resolution.getRequirement() == null) {
            return null;
        }
        return resolution.getRequirement().getLinkage();
    }

    private class ApiNativeDependencySet implements NativeDependencySet {
        private final NativeDependencySet delegate;

        public ApiNativeDependencySet(NativeDependencySet delegate) {
            this.delegate = delegate;
        }

        public FileCollection getIncludeRoots() {
            return delegate.getIncludeRoots();
        }

        public FileCollection getLinkFiles() {
            return new SimpleFileCollection();
        }

        public FileCollection getRuntimeFiles() {
            return new SimpleFileCollection();
        }
    }
}
