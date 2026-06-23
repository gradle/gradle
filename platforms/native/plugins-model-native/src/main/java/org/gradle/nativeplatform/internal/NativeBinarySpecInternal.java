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
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.language.nativeplatform.DependentSourceSet;
import org.gradle.nativeplatform.*;
import org.gradle.nativeplatform.internal.resolve.NativeBinaryRequirementResolveResult;
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolver;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.PreCompiledHeader;
import org.gradle.platform.base.internal.BinarySpecInternal;

import java.io.File;
import java.util.Collection;
import java.util.Map;

public interface NativeBinarySpecInternal extends NativeBinarySpec, BinarySpecInternal {

    void setFlavor(Flavor flavor);

    void setToolChain(NativeToolChain toolChain);

    void setTargetPlatform(NativePlatform targetPlatform);

    void setBuildType(BuildType buildType);

    Tool getToolByName(String name);

    PlatformToolProvider getPlatformToolProvider();

    void setPlatformToolProvider(PlatformToolProvider toolProvider);

    void setResolver(NativeDependencyResolver resolver);

    void setFileCollectionFactory(FileCollectionFactory fileCollectionFactory);

    File getPrimaryOutput();

    Collection<NativeDependencySet> getLibs(DependentSourceSet sourceSet);

    Collection<NativeLibraryBinary> getDependentBinaries();

    /**
     * Adds some files to include as input to the link/assemble step of this binary.
     */
    void binaryInputs(FileCollection files);

    Collection<NativeBinaryRequirementResolveResult> getAllResolutions();

    Map<File, PreCompiledHeader> getPrefixFileToPCH();

    void addPreCompiledHeaderFor(DependentSourceSet sourceSet);
}
