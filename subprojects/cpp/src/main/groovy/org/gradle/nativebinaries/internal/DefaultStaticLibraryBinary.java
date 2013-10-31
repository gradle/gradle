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
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.language.base.internal.DefaultBinaryNamingScheme;
import org.gradle.nativebinaries.*;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DefaultStaticLibraryBinary extends DefaultNativeBinary implements StaticLibraryBinary {
    private final Library library;
    private final DefaultTool staticLibArchiver = new DefaultTool();
    private final List<FileCollection> resources = new ArrayList<FileCollection>();

    public DefaultStaticLibraryBinary(Library library, Flavor flavor, ToolChainInternal toolChain, Platform platform, BuildType buildType, DefaultBinaryNamingScheme namingScheme) {
        super(library, flavor, toolChain, platform, buildType, namingScheme.withTypeString("StaticLibrary"));
        this.library = library;
    }

    public Library getComponent() {
        return library;
    }

    public String getOutputFileName() {
        return getToolChain().getStaticLibraryName(getComponent().getBaseName());
    }

    public Tool getStaticLibArchiver() {
        return staticLibArchiver;
    }

    public void resources(FileCollection additionalOutputs) {
        this.resources.add(additionalOutputs);
    }

    public NativeDependencySet resolve() {
        return new NativeDependencySet() {
            public FileCollection getIncludeRoots() {
                return library.getHeaders();
            }

            public FileCollection getLinkFiles() {
                return new FileCollectionAdapter(new StaticLibraryOutputs());
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

    private class StaticLibraryOutputs implements MinimalFileSet, Buildable {
        public Set<File> getFiles() {
            System.out.println("Resolving static library link files");
            Set<File> allFiles = new LinkedHashSet<File>();
            allFiles.add(getOutputFile());
            for (FileCollection resourceSet : resources) {
                System.out.println("Resolving " + resourceSet + ": " + resourceSet.getFiles());
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
}
