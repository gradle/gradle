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
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.DefaultBinaryNamingScheme;
import org.gradle.language.rc.WindowsResourceSet;
import org.gradle.nativebinaries.*;

import java.io.File;
import java.util.Collections;
import java.util.Set;

public class DefaultSharedLibraryBinary extends DefaultLibraryBinary implements SharedLibraryBinary {

    public DefaultSharedLibraryBinary(Library library, Flavor flavor, ToolChainInternal toolChain, Platform platform, BuildType buildType, DefaultBinaryNamingScheme namingScheme) {
        super(library, flavor, toolChain, platform, buildType, namingScheme.withTypeString("SharedLibrary"));
    }

    public String getOutputFileName() {
        return getToolChain().getSharedLibraryName(getComponent().getBaseName());
    }

    public LibraryNativeDependencySet resolve() {
        return new LibraryNativeDependencySet() {
            public FileCollection getIncludeRoots() {
                return getHeaderDirs();
            }

            public FileCollection getLinkFiles() {
                return new FileCollectionAdapter(new SharedLibraryLinkOutputs());
            }

            public FileCollection getRuntimeFiles() {
                return new FileCollectionAdapter(new SharedLibraryRuntimeOutputs());
            }

            public LibraryBinary getLibraryBinary() {
                return DefaultSharedLibraryBinary.this;
            }
        };
    }

    private class SharedLibraryLinkOutputs implements MinimalFileSet, Buildable {
        public Set<File> getFiles() {
            if (isResourceOnly()) {
                return Collections.emptySet();
            }
            String sharedLibraryLinkFileName = getToolChain().getSharedLibraryLinkFileName(getOutputFile().getPath());
            return Collections.singleton(new File(sharedLibraryLinkFileName));
        }

        private boolean isResourceOnly() {
            return hasResources() && !hasExportedSymbols();
        }

        private boolean hasResources() {
            for (WindowsResourceSet windowsResourceSet : getSource().withType(WindowsResourceSet.class)) {
                if (windowsResourceSet.getSource().getFiles().size() > 0) {
                    return true;
                }
            }
            return false;
        }

        private boolean hasExportedSymbols() {
            // TODO:DAZ This is a very rough approximation: actually inspect the binary to determine if there are exported symbols
            for (LanguageSourceSet languageSourceSet : getSource()) {
                if (!(languageSourceSet instanceof WindowsResourceSet)) {
                    if (languageSourceSet.getSource().getFiles().size() > 0) {
                        return true;
                    }
                }
            }
            return false;
        }

        public String getDisplayName() {
            return DefaultSharedLibraryBinary.this.toString();
        }

        public TaskDependency getBuildDependencies() {
            return DefaultSharedLibraryBinary.this.getBuildDependencies();
        }
    }

    private class SharedLibraryRuntimeOutputs implements MinimalFileSet, Buildable {
        public Set<File> getFiles() {
            return Collections.singleton(getOutputFile());
        }

        public String getDisplayName() {
            return DefaultSharedLibraryBinary.this.toString();
        }

        public TaskDependency getBuildDependencies() {
            return DefaultSharedLibraryBinary.this.getBuildDependencies();
        }
    }

}
