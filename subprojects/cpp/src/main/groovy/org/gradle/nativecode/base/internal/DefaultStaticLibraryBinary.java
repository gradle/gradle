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

package org.gradle.nativecode.base.internal;

import org.gradle.api.Buildable;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.collections.FileCollectionAdapter;
import org.gradle.api.internal.file.collections.MinimalFileSet;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.nativecode.base.Flavor;
import org.gradle.nativecode.base.Library;
import org.gradle.nativecode.base.NativeDependencySet;
import org.gradle.nativecode.base.StaticLibraryBinary;

import java.io.File;
import java.util.Collections;
import java.util.Set;

public class DefaultStaticLibraryBinary extends DefaultNativeBinary implements StaticLibraryBinary {
    private final Library library;

    public DefaultStaticLibraryBinary(Library library, Flavor flavor, ToolChainInternal toolChain) {
        super(library, flavor, "StaticLibrary", toolChain);
        this.library = library;
    }

    public Library getComponent() {
        return library;
    }

    public String getOutputFileName() {
        return getToolChain().getStaticLibraryName(getComponent().getBaseName());
    }

    public NativeDependencySet resolve() {
        return getAsNativeDependencySet();
    }

    public NativeDependencySet getAsNativeDependencySet() {
        return new NativeDependencySet() {
            public FileCollection getIncludeRoots() {
                return library.getHeaders();
            }

            public FileCollection getLinkFiles() {
                return new FileCollectionAdapter(new LibraryFile());
            }

            public FileCollection getRuntimeFiles() {
                return new SimpleFileCollection() {
                    @Override
                    public String getDisplayName() {
                        return DefaultStaticLibraryBinary.this.toString();
                    }
                };
            }
        };
    }

    private class LibraryFile implements MinimalFileSet, Buildable {
        public Set<File> getFiles() {
            return Collections.singleton(getOutputFile());
        }

        public String getDisplayName() {
            return DefaultStaticLibraryBinary.this.toString();
        }

        public TaskDependency getBuildDependencies() {
            return DefaultStaticLibraryBinary.this.getBuildDependencies();
        }
    }
}
