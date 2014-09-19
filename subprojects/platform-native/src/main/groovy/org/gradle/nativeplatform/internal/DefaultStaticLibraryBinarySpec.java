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
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.nativeplatform.BuildType;
import org.gradle.nativeplatform.Flavor;
import org.gradle.nativeplatform.NativeLibrarySpec;
import org.gradle.nativeplatform.StaticLibraryBinary;
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolver;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.platform.base.internal.BinaryNamingScheme;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DefaultStaticLibraryBinarySpec extends AbstractNativeLibraryBinarySpec implements StaticLibraryBinary, StaticLibraryBinarySpecInternal {
    private final List<FileCollection> additionalLinkFiles = new ArrayList<FileCollection>();
    private File staticLibraryFile;

    public DefaultStaticLibraryBinarySpec(NativeLibrarySpec library, Flavor flavor, NativeToolChainInternal toolChain, PlatformToolProvider toolProvider, NativePlatform platform,
                                          BuildType buildType, BinaryNamingScheme namingScheme, NativeDependencyResolver resolver) {
        super(library, flavor, toolChain, toolProvider, platform, buildType, namingScheme, resolver);
    }

    public File getStaticLibraryFile() {
        return staticLibraryFile;
    }

    public void setStaticLibraryFile(File staticLibraryFile) {
        this.staticLibraryFile = staticLibraryFile;
    }

    public File getPrimaryOutput() {
        return getStaticLibraryFile();
    }

    public void additionalLinkFiles(FileCollection files) {
        this.additionalLinkFiles.add(files);
    }

    public FileCollection getLinkFiles() {
        return new StaticLibraryLinkOutputs();
    }

    public FileCollection getRuntimeFiles() {
        return new SimpleFileCollection();
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
                allFiles.add(getStaticLibraryFile());
            }
            for (FileCollection resourceSet : additionalLinkFiles) {
                allFiles.addAll(resourceSet.getFiles());
            }
            return allFiles;
        }
    }
}
