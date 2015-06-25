/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.nativeplatform.internal.configure;

import org.gradle.api.plugins.ExtensionAware;
import org.gradle.language.base.internal.registry.LanguageTransform;
import org.gradle.language.base.internal.registry.LanguageTransformContainer;
import org.gradle.model.Defaults;
import org.gradle.model.RuleSource;
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.nativeplatform.NativeExecutableBinarySpec;
import org.gradle.nativeplatform.SharedLibraryBinarySpec;
import org.gradle.nativeplatform.StaticLibraryBinarySpec;
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.platform.base.internal.BinaryNamingScheme;

import java.io.File;
import java.util.Map;

public class NativeBinaryRules extends RuleSource {
    @Defaults
    void addToolExtensions(NativeBinarySpec nativeBinarySpec, LanguageTransformContainer languageTransforms) {
        for (LanguageTransform<?, ?> language : languageTransforms) {
            Map<String, Class<?>> binaryTools = language.getBinaryTools();
            for (String toolName : binaryTools.keySet()) {
                ((ExtensionAware) nativeBinarySpec).getExtensions().create(toolName, binaryTools.get(toolName));
            }
        }
    }

    @Defaults
    public static void assignTools(NativeBinarySpec nativeBinarySpec, NativeToolChainRegistryInternal toolChains, /* @Path("buildDir") */ File buildDir) {
        NativeBinarySpecInternal binarySpecInternal = (NativeBinarySpecInternal) nativeBinarySpec;

        NativeToolChainInternal toolChain = (NativeToolChainInternal) toolChains.getForPlatform(nativeBinarySpec.getTargetPlatform());
        binarySpecInternal.setToolChain(toolChain);
        PlatformToolProvider toolProvider = toolChain.select((NativePlatformInternal) nativeBinarySpec.getTargetPlatform());
        binarySpecInternal.setPlatformToolProvider(toolProvider);

        File binariesOutputDir = new File(buildDir, "binaries");
        BinaryNamingScheme namingScheme = binarySpecInternal.getNamingScheme();
        File binaryOutputDir = new File(binariesOutputDir, namingScheme.getOutputDirectoryBase());
        String baseName = binarySpecInternal.getComponent().getBaseName();

        if (binarySpecInternal instanceof NativeExecutableBinarySpec) {
            ((NativeExecutableBinarySpec) binarySpecInternal).setExecutableFile(new File(binaryOutputDir, toolProvider.getExecutableName(baseName)));
        } else if (binarySpecInternal instanceof SharedLibraryBinarySpec) {
            ((SharedLibraryBinarySpec) binarySpecInternal).setSharedLibraryFile(new File(binaryOutputDir, toolProvider.getSharedLibraryName(baseName)));
            ((SharedLibraryBinarySpec) binarySpecInternal).setSharedLibraryLinkFile(new File(binaryOutputDir, toolProvider.getSharedLibraryLinkFileName(baseName)));
        } else if (binarySpecInternal instanceof StaticLibraryBinarySpec) {
            ((StaticLibraryBinarySpec) binarySpecInternal).setStaticLibraryFile(new File(binaryOutputDir, toolProvider.getStaticLibraryName(baseName)));
        }
    }
}
