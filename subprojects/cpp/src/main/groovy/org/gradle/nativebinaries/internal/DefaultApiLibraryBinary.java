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

package org.gradle.nativebinaries.internal;

import org.gradle.api.Buildable;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.collections.FileCollectionAdapter;
import org.gradle.api.internal.file.collections.MinimalFileSet;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.language.base.internal.DefaultBinaryNamingScheme;
import org.gradle.nativebinaries.*;
import org.gradle.nativebinaries.internal.resolve.LibraryNativeDependencySet;
import org.gradle.nativebinaries.internal.resolve.NativeDependencyResolver;
import org.gradle.nativebinaries.platform.Platform;
import org.gradle.nativebinaries.toolchain.internal.ToolChainInternal;

import java.io.File;
import java.util.Collections;
import java.util.Set;

public class DefaultApiLibraryBinary extends DefaultLibraryBinary implements ApiLibraryBinary {

    public DefaultApiLibraryBinary(Library library, Flavor flavor, ToolChainInternal toolChain, Platform platform, BuildType buildType,
                                   DefaultBinaryNamingScheme namingScheme, NativeDependencyResolver resolver) {
        super(library, flavor, toolChain, platform, buildType, namingScheme.withTypeString("LibraryApi"), resolver);
    }

    public String getOutputFileName() {
        // TODO:DAZ Should not need this
        return "api";
    }

    @Override
    public boolean isBuildable() {
        return false;
    }

    public LibraryNativeDependencySet resolve() {
        return new LibraryNativeDependencySet() {
            public FileCollection getIncludeRoots() {
                return getHeaderDirs();
            }

            public FileCollection getLinkFiles() {
                return new FileCollectionAdapter(new EmptyLibraryOutputs());
            }

            public FileCollection getRuntimeFiles() {
                return new FileCollectionAdapter(new EmptyLibraryOutputs());
            }

            public LibraryBinary getLibraryBinary() {
                return DefaultApiLibraryBinary.this;
            }
        };
    }


    private class EmptyLibraryOutputs implements MinimalFileSet, Buildable {
        public Set<File> getFiles() {
            return Collections.emptySet();
        }

        public String getDisplayName() {
            return DefaultApiLibraryBinary.this.toString();
        }

        public TaskDependency getBuildDependencies() {
            return new DefaultTaskDependency();
        }
    }
}
