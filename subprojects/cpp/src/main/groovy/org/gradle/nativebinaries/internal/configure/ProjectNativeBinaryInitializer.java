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
import org.gradle.api.Project;
import org.gradle.language.base.internal.BinaryNamingScheme;
import org.gradle.nativebinaries.ExecutableBinary;
import org.gradle.nativebinaries.ProjectNativeBinary;
import org.gradle.nativebinaries.SharedLibraryBinary;
import org.gradle.nativebinaries.StaticLibraryBinary;
import org.gradle.nativebinaries.internal.ProjectNativeBinaryInternal;
import org.gradle.nativebinaries.toolchain.internal.ToolChainInternal;

import java.io.File;

class ProjectNativeBinaryInitializer implements Action<ProjectNativeBinary> {
    private final File binariesOutputDir;

    ProjectNativeBinaryInitializer(Project project) {
        binariesOutputDir = new File(project.getBuildDir(), "binaries");
    }

    public void execute(ProjectNativeBinary nativeBinary) {
        ToolChainInternal tc = (ToolChainInternal) nativeBinary.getToolChain();
        BinaryNamingScheme namingScheme = ((ProjectNativeBinaryInternal) nativeBinary).getNamingScheme();
        File binaryOutputDir = new File(binariesOutputDir, namingScheme.getOutputDirectoryBase());
        String baseName = nativeBinary.getComponent().getBaseName();

        if (nativeBinary instanceof ExecutableBinary) {
            ((ExecutableBinary) nativeBinary).setExecutableFile(new File(binaryOutputDir, tc.getExecutableName(baseName)));
        } else if (nativeBinary instanceof SharedLibraryBinary) {
            ((SharedLibraryBinary) nativeBinary).setSharedLibraryFile(new File(binaryOutputDir, tc.getSharedLibraryName(baseName)));
            ((SharedLibraryBinary) nativeBinary).setSharedLibraryLinkFile(new File(binaryOutputDir, tc.getSharedLibraryLinkFileName(baseName)));
        } else if (nativeBinary instanceof StaticLibraryBinary) {
            ((StaticLibraryBinary) nativeBinary).setStaticLibraryFile(new File(binaryOutputDir, tc.getStaticLibraryName(baseName)));
        }
    }
}
