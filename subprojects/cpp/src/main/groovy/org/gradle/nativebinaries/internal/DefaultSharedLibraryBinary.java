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
import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultSharedLibraryBinary extends DefaultNativeBinary implements SharedLibraryBinary {
    private final Library library;

    public DefaultSharedLibraryBinary(Library library, Flavor flavor, ToolChainInternal toolChain, Platform platform, BuildType buildType, DefaultBinaryNamingScheme namingScheme) {
        super(library, flavor, toolChain, platform, buildType, namingScheme.withTypeString("SharedLibrary"));
        this.library = library;
    }

    public Library getComponent() {
        return library;
    }

    public String getOutputFileName() {
        return getToolChain().getSharedLibraryName(getComponent().getBaseName());
    }

    private boolean isResourceOnly() {
        // TODO:DAZ Generalise this to 'has exported functions'
        if (getSource().withType(WindowsResourceSet.class).size() == 0) {
            return false;
        }

        for (LanguageSourceSet languageSourceSet : getSource()) {
            if (!(languageSourceSet instanceof WindowsResourceSet)) {
                if (languageSourceSet.getSource().getFiles().size() > 0) {
                    return false;
                }
            }
        }
        return true;
    }

    public NativeDependencySet resolve() {
        return new NativeDependencySet() {
            public FileCollection getIncludeRoots() {
                return library.getHeaders();
            }

            public FileCollection getLinkFiles() {
                return new FileCollectionAdapter(getSharedLibraryLinkOutputs());
            }

            public FileCollection getRuntimeFiles() {
                return new FileCollectionAdapter(new SharedLibraryOutputs(getOutputFile()));
            }
        };
    }

    private SharedLibraryOutputs getSharedLibraryLinkOutputs() {
        if (isResourceOnly()) {
            return new SharedLibraryOutputs();
        }
        String sharedLibraryLinkFileName = getToolChain().getSharedLibraryLinkFileName(getOutputFile().getPath());
        return new SharedLibraryOutputs(new File(sharedLibraryLinkFileName));
    }

    private class SharedLibraryOutputs implements MinimalFileSet, Buildable {
        private final Set<File> outputFiles = new LinkedHashSet<File>();

        private SharedLibraryOutputs(File... outputFiles) {
            Collections.addAll(this.outputFiles, outputFiles);
        }

        public Set<File> getFiles() {
            return outputFiles;
        }

        public String getDisplayName() {
            return DefaultSharedLibraryBinary.this.toString();
        }

        public TaskDependency getBuildDependencies() {
            return DefaultSharedLibraryBinary.this.getBuildDependencies();
        }
    }
}
