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
package org.gradle.nativebinaries.internal.configure;

import org.gradle.api.Action;
import org.gradle.nativebinaries.NativeBinarySpec;
import org.gradle.nativebinaries.NativeExecutableBinarySpec;
import org.gradle.nativebinaries.SharedLibraryBinarySpec;
import org.gradle.nativebinaries.StaticLibraryBinarySpec;
import org.gradle.nativebinaries.internal.NativeBinarySpecInternal;
import org.gradle.nativebinaries.toolchain.internal.ToolChainInternal;
import org.gradle.runtime.base.internal.BinaryNamingScheme;

import java.io.File;

public class NativeBinarySpecInitializer implements Action<NativeBinarySpec> {
    private final File binariesOutputDir;

    public NativeBinarySpecInitializer(File buildDir) {
        binariesOutputDir = new File(buildDir, "binaries");
    }

    public void execute(NativeBinarySpec nativeBinary) {
        ToolChainInternal tc = (ToolChainInternal) nativeBinary.getToolChain();
        BinaryNamingScheme namingScheme = ((NativeBinarySpecInternal) nativeBinary).getNamingScheme();
        File binaryOutputDir = new File(binariesOutputDir, namingScheme.getOutputDirectoryBase());
        String baseName = nativeBinary.getComponent().getBaseName();

        if (nativeBinary instanceof NativeExecutableBinarySpec) {
            ((NativeExecutableBinarySpec) nativeBinary).setExecutableFile(new File(binaryOutputDir, tc.getExecutableName(baseName)));
        } else if (nativeBinary instanceof SharedLibraryBinarySpec) {
            ((SharedLibraryBinarySpec) nativeBinary).setSharedLibraryFile(new File(binaryOutputDir, tc.getSharedLibraryName(baseName)));
            ((SharedLibraryBinarySpec) nativeBinary).setSharedLibraryLinkFile(new File(binaryOutputDir, tc.getSharedLibraryLinkFileName(baseName)));
        } else if (nativeBinary instanceof StaticLibraryBinarySpec) {
            ((StaticLibraryBinarySpec) nativeBinary).setStaticLibraryFile(new File(binaryOutputDir, tc.getStaticLibraryName(baseName)));
        }
    }
}
