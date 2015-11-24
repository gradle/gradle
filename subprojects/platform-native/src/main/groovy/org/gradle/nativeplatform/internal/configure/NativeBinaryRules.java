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

import org.gradle.model.Defaults;
import org.gradle.model.RuleSource;
import org.gradle.nativeplatform.*;
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;

import java.io.File;

public class NativeBinaryRules extends RuleSource {
    @Defaults
    public static void assignTools(NativeBinarySpec nativeBinarySpec, NativeToolChainRegistryInternal toolChains, /* @Path("buildDir") */ File buildDir) {
        NativeBinarySpecInternal nativeBinary = (NativeBinarySpecInternal) nativeBinarySpec;
        assignToolsToNativeBinary(nativeBinary, nativeBinarySpec, toolChains);
        assignToolsToNativeBinaryExtension(nativeBinary, buildDir);
    }

    private static void assignToolsToNativeBinary(NativeBinarySpecInternal nativeBinary, NativeBinarySpec nativeBinarySpec, NativeToolChainRegistryInternal toolChains) {
        NativeToolChainInternal toolChain = toolChainFor(nativeBinarySpec, toolChains);
        PlatformToolProvider toolProvider = toolChain.select((NativePlatformInternal) nativeBinarySpec.getTargetPlatform());
        nativeBinary.setToolChain(toolChain);
        nativeBinary.setPlatformToolProvider(toolProvider);
    }

    private static void assignToolsToNativeBinaryExtension(NativeBinarySpecInternal nativeBinary, File buildDir) {
        if (nativeBinary instanceof NativeExecutableBinarySpec) {
            assignToolsToNativeExecutableBinary(nativeBinary, buildDir);
        } else if (nativeBinary instanceof SharedLibraryBinarySpec) {
            assignToolsToSharedLibraryBinary(nativeBinary, buildDir);
        } else if (nativeBinary instanceof StaticLibraryBinarySpec) {
            assignToolsToStaticLibraryBinary(buildDir, nativeBinary);
        }
    }

    private static void assignToolsToNativeExecutableBinary(NativeBinarySpecInternal nativeBinary, File buildDir) {
        NativeExecutableBinarySpec nativeExecutable = (NativeExecutableBinarySpec) nativeBinary;
        NativeExecutableFileSpec executable = nativeExecutable.getExecutable();
        executable.setFile(executableFileFor(nativeBinary, buildDir));
        executable.setToolChain(nativeBinary.getToolChain());
        nativeExecutable.getInstallation().setDirectory(installationDirFor(nativeBinary, buildDir));
    }

    private static void assignToolsToSharedLibraryBinary(NativeBinarySpecInternal nativeBinary, File buildDir) {
        SharedLibraryBinarySpec sharedLibrary = (SharedLibraryBinarySpec) nativeBinary;
        sharedLibrary.setSharedLibraryFile(sharedLibraryFileFor(nativeBinary, buildDir));
        sharedLibrary.setSharedLibraryLinkFile(sharedLibraryLinkFileFor(nativeBinary, buildDir));
    }

    private static void assignToolsToStaticLibraryBinary(File buildDir, NativeBinarySpecInternal nativeBinary) {
        StaticLibraryBinarySpec staticLibrary = (StaticLibraryBinarySpec) nativeBinary;
        staticLibrary.setStaticLibraryFile(staticLibraryFileFor(nativeBinary, buildDir));
    }

    private static File executableFileFor(NativeBinarySpecInternal nativeBinary, File buildDir) {
        return binaryOutputFileFor(nativeBinary, buildDir, executableNameFor(nativeBinary));
    }

    private static File sharedLibraryLinkFileFor(NativeBinarySpecInternal nativeBinary, File buildDir) {
        return binaryOutputFileFor(nativeBinary, buildDir, sharedLibraryLinkFileNameFor(nativeBinary));
    }

    private static File sharedLibraryFileFor(NativeBinarySpecInternal nativeBinary, File buildDir) {
        return binaryOutputFileFor(nativeBinary, buildDir, sharedLibraryNameFor(nativeBinary));
    }

    private static File staticLibraryFileFor(NativeBinarySpecInternal nativeBinary, File buildDir) {
        return binaryOutputFileFor(nativeBinary, buildDir, staticLibraryNameFor(nativeBinary));
    }

    private static File binaryOutputFileFor(NativeBinarySpecInternal nativeBinary, File buildDir, String fileName) {
        return new File(binaryOutputDirFor(nativeBinary, buildDir), fileName);
    }

    private static File binaryOutputDirFor(NativeBinarySpecInternal nativeBinary, File buildDir) {
        return outputDirectoryFor(nativeBinary, buildDir, "binaries");
    }

    private static File installationDirFor(NativeBinarySpecInternal nativeBinary, File buildDir) {
        return outputDirectoryFor(nativeBinary, buildDir, "install");
    }

    private static File outputDirectoryFor(NativeBinarySpecInternal nativeBinary, File buildDir, String purpose) {
        return nativeBinary.getNamingScheme().withOutputType(purpose).getOutputDirectory(buildDir);
    }

    private static String executableNameFor(NativeBinarySpecInternal nativeBinary) {
        return nativeBinary.getPlatformToolProvider().getExecutableName(baseNameOf(nativeBinary));
    }

    private static String sharedLibraryLinkFileNameFor(NativeBinarySpecInternal nativeBinary) {
        return nativeBinary.getPlatformToolProvider().getSharedLibraryLinkFileName(baseNameOf(nativeBinary));
    }

    private static String sharedLibraryNameFor(NativeBinarySpecInternal nativeBinary) {
        return nativeBinary.getPlatformToolProvider().getSharedLibraryName(baseNameOf(nativeBinary));
    }

    private static String staticLibraryNameFor(NativeBinarySpecInternal nativeBinary) {
        return nativeBinary.getPlatformToolProvider().getStaticLibraryName(baseNameOf(nativeBinary));
    }

    private static String baseNameOf(NativeBinarySpecInternal nativeBinary) {
        return nativeBinary.getComponent().getBaseName();
    }

    private static NativeToolChainInternal toolChainFor(NativeBinarySpec nativeBinary, NativeToolChainRegistryInternal toolChains) {
        return (NativeToolChainInternal) toolChains.getForPlatform(nativeBinary.getTargetPlatform());
    }

}
