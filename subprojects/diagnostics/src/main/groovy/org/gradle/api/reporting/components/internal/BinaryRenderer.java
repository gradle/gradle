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

package org.gradle.api.reporting.components.internal;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.tasks.diagnostics.internal.text.TextReportBuilder;
import org.gradle.jvm.ClassDirectoryBinarySpec;
import org.gradle.jvm.JarBinarySpec;
import org.gradle.jvm.JvmBinarySpec;
import org.gradle.logging.StyledTextOutput;
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.nativeplatform.NativeExecutableBinarySpec;
import org.gradle.nativeplatform.SharedLibraryBinarySpec;
import org.gradle.nativeplatform.StaticLibraryBinarySpec;
import org.gradle.nativeplatform.test.NativeTestSuiteBinarySpec;
import org.gradle.platform.base.BinarySpec;
import org.gradle.reporting.ReportRenderer;

class BinaryRenderer extends ReportRenderer<BinarySpec, TextReportBuilder> {
    private final FileResolver fileResolver;

    BinaryRenderer(FileResolver fileResolver) {
        this.fileResolver = fileResolver;
    }

    @Override
    public void render(BinarySpec binary, TextReportBuilder builder) {
        StyledTextOutput textOutput = builder.getOutput();

        textOutput.append(StringUtils.capitalize(binary.getDisplayName()));
        if (!binary.isBuildable()) {
            textOutput.append(" (not buildable)");
        }
        textOutput.println();

        textOutput.formatln("    build using task: %s", binary.getBuildTask().getPath());

        if (binary instanceof NativeBinarySpec) {
            NativeBinarySpec nativeBinary = (NativeBinarySpec) binary;
            textOutput.formatln("    platform: %s", nativeBinary.getTargetPlatform().getName());
            textOutput.formatln("    build type: %s", nativeBinary.getBuildType().getName());
            textOutput.formatln("    flavor: %s", nativeBinary.getFlavor().getName());
            textOutput.formatln("    tool chain: %s", nativeBinary.getToolChain().getDisplayName());
            if (binary instanceof NativeExecutableBinarySpec) {
                NativeExecutableBinarySpec executableBinary = (NativeExecutableBinarySpec) binary;
                textOutput.formatln("    executable file: %s", fileResolver.resolveAsRelativePath(executableBinary.getExecutableFile()));
            }
            if (binary instanceof NativeTestSuiteBinarySpec) {
                NativeTestSuiteBinarySpec executableBinary = (NativeTestSuiteBinarySpec) binary;
                textOutput.formatln("    executable file: %s", fileResolver.resolveAsRelativePath(executableBinary.getExecutableFile()));
            }
            if (binary instanceof SharedLibraryBinarySpec) {
                SharedLibraryBinarySpec libraryBinary = (SharedLibraryBinarySpec) binary;
                textOutput.formatln("    shared library file: %s", fileResolver.resolveAsRelativePath(libraryBinary.getSharedLibraryFile()));
            }
            if (binary instanceof StaticLibraryBinarySpec) {
                StaticLibraryBinarySpec libraryBinary = (StaticLibraryBinarySpec) binary;
                textOutput.formatln("    static library file: %s", fileResolver.resolveAsRelativePath(libraryBinary.getStaticLibraryFile()));
            }
        }

        if (binary instanceof JvmBinarySpec) {
            JvmBinarySpec libraryBinary = (JvmBinarySpec) binary;
            textOutput.formatln("    platform: %s", libraryBinary.getTargetPlatform().getName());
            textOutput.formatln("    tool chain: %s", libraryBinary.getToolChain().toString());
            if (binary instanceof JarBinarySpec) {
                JarBinarySpec jarBinary = (JarBinarySpec) binary;
                textOutput.formatln("    Jar file: %s", fileResolver.resolveAsRelativePath(jarBinary.getJarFile()));
            }
            if (binary instanceof ClassDirectoryBinarySpec) {
                ClassDirectoryBinarySpec classDirectoryBinary = (ClassDirectoryBinarySpec) binary;
                textOutput.formatln("    classes dir: %s", fileResolver.resolveAsRelativePath(classDirectoryBinary.getClassesDir()));
                textOutput.formatln("    resources dir: %s", fileResolver.resolveAsRelativePath(classDirectoryBinary.getResourcesDir()));
            }
        }
    }
}
