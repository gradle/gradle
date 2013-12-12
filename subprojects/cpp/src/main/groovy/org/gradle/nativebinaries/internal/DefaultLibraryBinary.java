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
import org.gradle.language.HeaderExportingSourceSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.DefaultBinaryNamingScheme;
import org.gradle.nativebinaries.BuildType;
import org.gradle.nativebinaries.Flavor;
import org.gradle.nativebinaries.Library;
import org.gradle.nativebinaries.LibraryBinary;
import org.gradle.nativebinaries.internal.resolve.LibraryNativeDependencySet;
import org.gradle.nativebinaries.internal.resolve.NativeDependencyResolver;
import org.gradle.nativebinaries.platform.Platform;
import org.gradle.nativebinaries.toolchain.internal.ToolChainInternal;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public abstract class DefaultLibraryBinary extends DefaultNativeBinary implements LibraryBinaryInternal {
    private final Library library;

    protected DefaultLibraryBinary(Library library, Flavor flavor, ToolChainInternal toolChain, Platform targetPlatform, BuildType buildType,
                                   DefaultBinaryNamingScheme namingScheme, NativeDependencyResolver resolver) {
        super(library, flavor, toolChain, targetPlatform, buildType, namingScheme, resolver);
        this.library = library;
    }

    public Library getComponent() {
        return library;
    }

    protected MinimalFileSet getHeaderDirs() {
        return new MinimalFileSet() {
            public String getDisplayName() {
                return String.format("Headers for %s", getName());
            }

            public Set<File> getFiles() {
                Set<File> headerDirs = new LinkedHashSet<File>();
                for (HeaderExportingSourceSet sourceSet : getSource().withType(HeaderExportingSourceSet.class)) {
                    headerDirs.addAll(sourceSet.getExportedHeaders().getSrcDirs());
                }
                return headerDirs;
            }
        };
    }

    protected abstract MinimalFileSet getLinkFiles();

    protected abstract MinimalFileSet getRuntimeFiles();

    public LibraryNativeDependencySet resolve() {
        return new LibraryNativeDependencySet() {
            public FileCollection getIncludeRoots() {
                return new FileCollectionAdapter(getHeaderDirs());
            }

            public FileCollection getLinkFiles() {
                return new FileCollectionAdapter(DefaultLibraryBinary.this.getLinkFiles());
            }

            public FileCollection getRuntimeFiles() {
                return new FileCollectionAdapter(DefaultLibraryBinary.this.getRuntimeFiles());
            }

            public LibraryBinary getLibraryBinary() {
                return DefaultLibraryBinary.this;
            }
        };
    }

    protected boolean hasSources() {
        for (LanguageSourceSet sourceSet : getSource()) {
            if (!sourceSet.getSource().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    protected MinimalFileSet emptyLibraryOutputs() {
        return new EmptyLibraryOutputs();
    }

    protected abstract class LibraryOutputs implements MinimalFileSet, Buildable {
        public final Set<File> getFiles() {
            if (hasOutputs()) {
                return getOutputs();
            }
            return Collections.emptySet();
        }

        public final String getDisplayName() {
            return DefaultLibraryBinary.this.toString();
        }

        public final TaskDependency getBuildDependencies() {
            if (hasOutputs()) {
                return DefaultLibraryBinary.this.getBuildDependencies();
            }
            return new DefaultTaskDependency();
        }

        protected abstract boolean hasOutputs();

        protected abstract Set<File> getOutputs();
    }

    private class EmptyLibraryOutputs extends LibraryOutputs {
        @Override
        protected boolean hasOutputs() {
            return false;
        }

        @Override
        protected Set<File> getOutputs() {
            return Collections.emptySet();
        }
    }
}
