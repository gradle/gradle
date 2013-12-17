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
import org.gradle.language.base.internal.DefaultBinaryNamingScheme;
import org.gradle.nativebinaries.BuildType;
import org.gradle.nativebinaries.Flavor;
import org.gradle.nativebinaries.Library;
import org.gradle.nativebinaries.Tool;
import org.gradle.nativebinaries.internal.resolve.NativeDependencyResolver;
import org.gradle.nativebinaries.platform.Platform;
import org.gradle.nativebinaries.toolchain.internal.ToolChainInternal;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DefaultStaticLibraryBinary extends DefaultLibraryBinary implements StaticLibraryBinaryInternal {
    private final DefaultTool staticLibArchiver = new DefaultTool();
    private final List<FileCollection> additionalLinkFiles = new ArrayList<FileCollection>();

    public DefaultStaticLibraryBinary(Library library, Flavor flavor, ToolChainInternal toolChain, Platform platform, BuildType buildType,
                                      DefaultBinaryNamingScheme namingScheme, NativeDependencyResolver resolver) {
        super(library, flavor, toolChain, platform, buildType, namingScheme.withTypeString("StaticLibrary"), resolver);
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

    public FileCollection getLinkFiles() {
        return new StaticLibraryLinkOutputs();
    }

    public FileCollection getRuntimeFiles() {
        return emptyLibraryOutputs();
    }

    private class StaticLibraryLinkOutputs extends LibraryOutputs {
        @Override
        protected boolean hasOutputs() {
            return hasSources() || !additionalLinkFiles.isEmpty();
        }

        @Override
        protected Set<File> getOutputs() {
            Set<File> allFiles = new LinkedHashSet<File>();
            if (hasSources()) {
                allFiles.add(getOutputFile());
            }
            for (FileCollection resourceSet : additionalLinkFiles) {
                allFiles.addAll(resourceSet.getFiles());
            }
            return allFiles;
        }
    }
}
