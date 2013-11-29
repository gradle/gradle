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
import org.gradle.api.internal.file.collections.LazilyInitializedFileCollection;
import org.gradle.nativebinaries.LibraryBinary;
import org.gradle.nativebinaries.NativeBinary;
import org.gradle.nativebinaries.NativeLibraryDependency;

class DeferredResolutionLibraryNativeDependencySet implements LibraryNativeDependencySet {
    private final NativeLibraryDependency dependency;
    private final NativeBinary target;
    private LibraryNativeDependencySet delegate;

    public DeferredResolutionLibraryNativeDependencySet(NativeLibraryDependency dependency, NativeBinary target) {
        this.dependency = dependency;
        this.target = target;
    }

    private LibraryNativeDependencySet doResolve() {
        if (delegate == null) {
            delegate = new DefaultLibraryResolver(dependency)
                    .withFlavor(target.getFlavor())
                    .withToolChain(target.getToolChain())
                    .withPlatform(target.getTargetPlatform())
                    .withBuildType(target.getBuildType())
                    .resolve();
        }
        return delegate;
    }

    public FileCollection getIncludeRoots() {
        return new LazilyInitializedFileCollection() {
            @Override
            public FileCollection createDelegate() {
                return doResolve().getIncludeRoots();
            }
        };
    }

    public FileCollection getLinkFiles() {
        return new LazilyInitializedFileCollection() {
            @Override
            public FileCollection createDelegate() {
                return doResolve().getLinkFiles();
            }
        };
    }

    public FileCollection getRuntimeFiles() {
        return new LazilyInitializedFileCollection() {
            @Override
            public FileCollection createDelegate() {
                return doResolve().getRuntimeFiles();
            }
        };
    }

    public LibraryBinary getLibraryBinary() {
        return doResolve().getLibraryBinary();
    }
}
