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
import org.gradle.api.tasks.TaskDependency;
import org.gradle.language.base.internal.DefaultBinaryNamingScheme;
import org.gradle.nativebinaries.*;

import java.io.File;
import java.util.*;

public class DefaultStaticLibraryBinary extends DefaultLibraryBinary implements StaticLibraryBinaryInternal {
    private final DefaultTool staticLibArchiver = new DefaultTool();
    private final List<FileCollection> additionalLinkFiles = new ArrayList<FileCollection>();

    public DefaultStaticLibraryBinary(Library library, Flavor flavor, ToolChainInternal toolChain, Platform platform, BuildType buildType, DefaultBinaryNamingScheme namingScheme) {
        super(library, flavor, toolChain, platform, buildType, namingScheme.withTypeString("StaticLibrary"));
    }

    public String getOutputFileName() {
        return getToolChain().getStaticLibraryName(getComponent().getBaseName());
    }

    public Tool getStaticLibArchiver() {
        return staticLibArchiver;
    }

    public void additionalLinkFiles(FileCollection files) {
        this.additionalLinkFiles.add(files);
    }

    public LibraryNativeDependencySet resolve() {
        return new LibraryNativeDependencySet() {
            public FileCollection getIncludeRoots() {
                return getHeaderDirs();
            }

            public FileCollection getLinkFiles() {
                return new FileCollectionAdapter(new StaticLibraryLinkOutputs());
            }

            public FileCollection getRuntimeFiles() {
                return new FileCollectionAdapter(new StaticLibraryRuntimeOutputs());
            }

            public LibraryBinary getLibraryBinary() {
                return DefaultStaticLibraryBinary.this;
            }
        };
    }

    private class StaticLibraryLinkOutputs implements MinimalFileSet, Buildable {
        public Set<File> getFiles() {
            Set<File> allFiles = new LinkedHashSet<File>();
            allFiles.add(getOutputFile());
            for (FileCollection resourceSet : additionalLinkFiles) {
                allFiles.addAll(resourceSet.getFiles());
            }
            return allFiles;
        }

        public String getDisplayName() {
            return DefaultStaticLibraryBinary.this.toString();
        }

        public TaskDependency getBuildDependencies() {
            return DefaultStaticLibraryBinary.this.getBuildDependencies();
        }
    }

    private class StaticLibraryRuntimeOutputs implements MinimalFileSet, Buildable {
        public Set<File> getFiles() {
            return Collections.emptySet();
        }

        public String getDisplayName() {
            return DefaultStaticLibraryBinary.this.toString();
        }

        public TaskDependency getBuildDependencies() {
            return DefaultStaticLibraryBinary.this.getBuildDependencies();
        }
    }
}
