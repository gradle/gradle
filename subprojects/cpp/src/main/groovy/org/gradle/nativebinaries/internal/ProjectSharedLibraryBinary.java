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

import org.gradle.api.file.FileCollection;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.BinaryNamingScheme;
import org.gradle.language.rc.WindowsResourceSet;
import org.gradle.nativebinaries.BuildType;
import org.gradle.nativebinaries.Flavor;
import org.gradle.nativebinaries.Library;
import org.gradle.nativebinaries.SharedLibraryBinary;
import org.gradle.nativebinaries.internal.resolve.NativeDependencyResolver;
import org.gradle.nativebinaries.platform.Platform;
import org.gradle.nativebinaries.toolchain.internal.ToolChainInternal;

import java.io.File;
import java.util.Collections;
import java.util.Set;

public class ProjectSharedLibraryBinary extends AbstractProjectLibraryBinary implements SharedLibraryBinary {
    private File sharedLibraryFile;
    private File sharedLibraryLinkFile;

    public ProjectSharedLibraryBinary(Library library, Flavor flavor, ToolChainInternal toolChain, Platform platform, BuildType buildType,
                                      BinaryNamingScheme namingScheme, NativeDependencyResolver resolver) {
        super(library, flavor, toolChain, platform, buildType, namingScheme, resolver);
    }

    public File getSharedLibraryFile() {
        return sharedLibraryFile;
    }

    public void setSharedLibraryFile(File sharedLibraryFile) {
        this.sharedLibraryFile = sharedLibraryFile;
    }

    public File getSharedLibraryLinkFile() {
        return sharedLibraryLinkFile;
    }

    public void setSharedLibraryLinkFile(File sharedLibraryLinkFile) {
        this.sharedLibraryLinkFile = sharedLibraryLinkFile;
    }

    public File getPrimaryOutput() {
        return getSharedLibraryFile();
    }

    public FileCollection getLinkFiles() {
        return new SharedLibraryLinkOutputs();
    }

    public FileCollection getRuntimeFiles() {
        return new SharedLibraryRuntimeOutputs();
    }

    private class SharedLibraryLinkOutputs extends LibraryOutputs {
        @Override
        protected boolean hasOutputs() {
            return hasSources() && !isResourceOnly();
        }

        @Override
        protected Set<File> getOutputs() {
            return Collections.singleton(getSharedLibraryLinkFile());
        }

        private boolean isResourceOnly() {
            return hasResources() && !hasExportedSymbols();
        }

        private boolean hasResources() {
            for (WindowsResourceSet windowsResourceSet : getSource().withType(WindowsResourceSet.class)) {
                if (!windowsResourceSet.getSource().isEmpty()) {
                    return true;
                }
            }
            return false;
        }

        private boolean hasExportedSymbols() {
            // TODO:DAZ This is a very rough approximation: actually inspect the binary to determine if there are exported symbols
            for (LanguageSourceSet languageSourceSet : getSource()) {
                if (!(languageSourceSet instanceof WindowsResourceSet)) {
                    if (!languageSourceSet.getSource().isEmpty()) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private class SharedLibraryRuntimeOutputs extends LibraryOutputs {
        @Override
        protected boolean hasOutputs() {
            return hasSources();
        }

        @Override
        protected Set<File> getOutputs() {
            return Collections.singleton(getSharedLibraryFile());
        }
    }
}
