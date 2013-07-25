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
import org.gradle.api.tasks.TaskDependency;
import org.gradle.nativecode.base.*;

import java.io.File;
import java.util.Collections;
import java.util.Set;

public class DefaultSharedLibraryBinary extends DefaultNativeBinary implements SharedLibraryBinary {
    private final Library library;

    public DefaultSharedLibraryBinary(Library library, Flavor flavor, ToolChainInternal toolChain) {
        super(library, flavor, "SharedLibrary", toolChain);
        this.library = library;
    }

    public Library getComponent() {
        return library;
    }

    public String getOutputFileName() {
        return getToolChain().getSharedLibraryName(getComponent().getBaseName());
    }

    private File getLinkFile() {
        return new File(getToolChain().getSharedLibraryLinkFileName(getOutputFile().getPath()));
    }

    public NativeDependencySet resolve() {
        return new NativeDependencySet() {
            public FileCollection getIncludeRoots() {
                return library.getHeaders();
            }

            public FileCollection getLinkFiles() {
                return new FileCollectionAdapter(new RuntimeFiles(getLinkFile()));
            }

            public FileCollection getRuntimeFiles() {
                return new FileCollectionAdapter(new RuntimeFiles(getOutputFile()));
            }
        };
    }

    private class RuntimeFiles implements MinimalFileSet, Buildable {
        private final File outputFile;

        private RuntimeFiles(File outputFile) {
            this.outputFile = outputFile;
        }

        public Set<File> getFiles() {
            return Collections.singleton(outputFile);
        }

        public String getDisplayName() {
            return DefaultSharedLibraryBinary.this.toString();
        }

        public TaskDependency getBuildDependencies() {
            return DefaultSharedLibraryBinary.this.getBuildDependencies();
        }
    }
}
