/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.nativeplatform.NativeExecutableBinarySpec;
import org.gradle.nativeplatform.SharedLibraryBinarySpec;
import org.gradle.nativeplatform.StaticLibraryBinarySpec;
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.platform.base.internal.BinaryNamingScheme;

import java.io.File;

public class NativeBinarySpecInitializer implements Action<NativeBinarySpec> {
    private final File binariesOutputDir;

    public NativeBinarySpecInitializer(File buildDir) {
        binariesOutputDir = new File(buildDir, "binaries");
    }

    public void execute(NativeBinarySpec nativeBinary) {
        BinaryNamingScheme namingScheme = ((NativeBinarySpecInternal) nativeBinary).getNamingScheme();
        PlatformToolProvider toolProvider = ((NativeBinarySpecInternal) nativeBinary).getPlatformToolProvider();
        File binaryOutputDir = new File(binariesOutputDir, namingScheme.getOutputDirectoryBase());
        String baseName = nativeBinary.getComponent().getBaseName();

        if (nativeBinary instanceof NativeExecutableBinarySpec) {
            ((NativeExecutableBinarySpec) nativeBinary).setExecutableFile(new File(binaryOutputDir, toolProvider.getExecutableName(baseName)));
        } else if (nativeBinary instanceof SharedLibraryBinarySpec) {
            ((SharedLibraryBinarySpec) nativeBinary).setSharedLibraryFile(new File(binaryOutputDir, toolProvider.getSharedLibraryName(baseName)));
            ((SharedLibraryBinarySpec) nativeBinary).setSharedLibraryLinkFile(new File(binaryOutputDir, toolProvider.getSharedLibraryLinkFileName(baseName)));
        } else if (nativeBinary instanceof StaticLibraryBinarySpec) {
            ((StaticLibraryBinarySpec) nativeBinary).setStaticLibraryFile(new File(binaryOutputDir, toolProvider.getStaticLibraryName(baseName)));
        }
    }
}
