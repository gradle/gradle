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

package org.gradle.nativeplatform.internal;

import org.gradle.api.file.FileCollection;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.nativeplatform.NativeResourceSet;
import org.gradle.nativeplatform.*;
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolver;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.platform.base.internal.BinaryNamingScheme;

import java.io.File;
import java.util.Collections;
import java.util.Set;

public class DefaultSharedLibraryBinarySpec extends AbstractNativeLibraryBinarySpec implements SharedLibraryBinary, SharedLibraryBinarySpecInternal {
    private File sharedLibraryFile;
    private File sharedLibraryLinkFile;

    public DefaultSharedLibraryBinarySpec(NativeLibrarySpec library, Flavor flavor, NativeToolChainInternal toolChain, PlatformToolProvider toolProvider, NativePlatform platform, BuildType buildType,
                                          BinaryNamingScheme namingScheme, NativeDependencyResolver resolver) {
        super(library, flavor, toolChain, toolProvider, platform, buildType, namingScheme, resolver);
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
            for (NativeResourceSet windowsResourceSet : getSource().withType(NativeResourceSet.class)) {
                if (!windowsResourceSet.getSource().isEmpty()) {
                    return true;
                }
            }
            return false;
        }

        private boolean hasExportedSymbols() {
            for (LanguageSourceSet languageSourceSet : getSource()) {
                if (!(languageSourceSet instanceof NativeResourceSet)) {
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
