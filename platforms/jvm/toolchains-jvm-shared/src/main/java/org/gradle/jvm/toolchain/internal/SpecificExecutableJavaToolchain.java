/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.jvm.toolchain.internal;

import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.file.FileFactory;
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata;

import java.io.File;

/**
 * A Java toolchain that uses a specific 'java' executable.  This toolchain does not support finding other tools.
 */
public class SpecificExecutableJavaToolchain extends JavaToolchain {
    private final RegularFile javaExecutable;

    public SpecificExecutableJavaToolchain(JvmInstallationMetadata metadata, FileFactory fileFactory, JavaToolchainInput input, boolean isFallbackToolchain, File javaExecutableFile) {
        super(metadata, fileFactory, input, isFallbackToolchain);
        this.javaExecutable = fileFactory.file(javaExecutableFile);
    }

    @Override
    public RegularFile findExecutable(String toolName) {
        if (toolName.equals("java")) {
            return javaExecutable;
        } else {
            throw new UnsupportedOperationException("This toolchain only supports retrieving the 'java' executable at " + javaExecutable.getAsFile().getAbsolutePath() + ".  It cannot be used to resolve the '" + toolName + "' executable.");
        }
    }
}
