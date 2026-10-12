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

package org.gradle.nativeplatform.internal.resolve;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileCollectionFactory;

/**
 * Adapts an 'api' library requirement to a default linkage, and then wraps the result so that only headers are provided.
 */
@SuppressWarnings("deprecation")
public class ApiRequirementNativeDependencyResolver implements NativeDependencyResolver {
    private final NativeDependencyResolver delegate;

    public ApiRequirementNativeDependencyResolver(NativeDependencyResolver delegate) {
        this.delegate = delegate;
    }

    @Override
    public void resolve(NativeBinaryResolveResult nativeBinaryResolveResult) {
        for (NativeBinaryRequirementResolveResult resolution : nativeBinaryResolveResult.getAllResolutions()) {
            String linkage = getLinkage(resolution);
            if ("api".equals(linkage)) {
                resolution.setRequirement(new ApiAdaptedNativeLibraryRequirement(resolution.getRequirement()));
            }
        }

        delegate.resolve(nativeBinaryResolveResult);

        for (NativeBinaryRequirementResolveResult resolution : nativeBinaryResolveResult.getAllResolutions()) {
            if (resolution.getRequirement() instanceof ApiAdaptedNativeLibraryRequirement) {
                ApiAdaptedNativeLibraryRequirement adaptedRequirement = (ApiAdaptedNativeLibraryRequirement) resolution.getRequirement();
                resolution.setRequirement(adaptedRequirement.getOriginal());
//                resolution.setLibraryBinary(null);
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

    private static class ApiAdaptedNativeLibraryRequirement implements org.gradle.nativeplatform.NativeLibraryRequirement {
        private final org.gradle.nativeplatform.NativeLibraryRequirement original;
        public ApiAdaptedNativeLibraryRequirement(org.gradle.nativeplatform.NativeLibraryRequirement original) {
            this.original = original;
        }

        public org.gradle.nativeplatform.NativeLibraryRequirement getOriginal() {
            return original;
        }

        @Override
        public org.gradle.nativeplatform.NativeLibraryRequirement withProjectPath(String projectPath) {
            return new ApiAdaptedNativeLibraryRequirement(original.withProjectPath(projectPath));
        }

        @Override
        public String getProjectPath() {
            return original.getProjectPath();
        }

        @Override
        public String getLibraryName() {
            return original.getLibraryName();
        }

        @Override
        public String getLinkage() {
            // Rely on the default linkage for providing the headers
            return null;
        }
    }

    private static class ApiNativeDependencySet implements org.gradle.nativeplatform.NativeDependencySet {
        private final org.gradle.nativeplatform.NativeDependencySet delegate;

        public ApiNativeDependencySet(org.gradle.nativeplatform.NativeDependencySet delegate) {
            this.delegate = delegate;
        }

        @Override
        public FileCollection getIncludeRoots() {
            return delegate.getIncludeRoots();
        }

        @Override
        public FileCollection getLinkFiles() {
            return FileCollectionFactory.empty(delegate.getLinkFiles().toString());
        }

        @Override
        public FileCollection getRuntimeFiles() {
            return FileCollectionFactory.empty(delegate.getRuntimeFiles().toString());
        }
    }
}
