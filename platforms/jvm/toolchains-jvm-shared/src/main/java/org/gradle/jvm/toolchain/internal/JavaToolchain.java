/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.api.Describable;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.file.FileFactory;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

public class JavaToolchain implements Describable, JavaInstallationMetadata {

    private final Directory javaHome;
    private final JavaLanguageVersion javaVersion;
    private final JvmInstallationMetadata metadata;
    private final JavaToolchainInput input;
    private final boolean isFallbackToolchain;

    public JavaToolchain(
        JvmInstallationMetadata metadata,
        FileFactory fileFactory,
        JavaToolchainInput input,
        boolean isFallbackToolchain
    ) {
        this.javaHome = fileFactory.dir(metadata.getJavaHome().toFile());
        this.javaVersion = JavaLanguageVersion.of(metadata.getJavaMajorVersion());
        this.metadata = metadata;
        this.input = input;
        this.isFallbackToolchain = isFallbackToolchain;
    }

    @Nested
    protected JavaToolchainInput getTaskInputs() {
        return input;
    }

    @Override
    public JavaLanguageVersion getLanguageVersion() {
        return javaVersion;
    }

    @Internal
    @Override
    public String getJavaRuntimeVersion() {
        return metadata.getRuntimeVersion();
    }

    @Override
    public String getJvmVersion() {
        return metadata.getJvmVersion();
    }

    @Internal
    @Override
    public Directory getInstallationPath() {
        return javaHome;
    }

    @Internal
    @Override
    public boolean isCurrentJvm() {
        return javaHome.getAsFile().equals(Jvm.current().getJavaHome());
    }

    @Internal
    public JvmInstallationMetadata getMetadata() {
        return metadata;
    }

    @Override
    public String getVendor() {
        return metadata.getVendor().getDisplayName();
    }

    @Internal
    @Override
    public String getDisplayName() {
        return javaHome.toString();
    }

    @Internal
    public boolean isFallbackToolchain() {
        return isFallbackToolchain;
    }

    public RegularFile findExecutable(String toolName) {
        return getInstallationPath().file(getBinaryPath(toolName));
    }

    @Override
    public String toString() {
        return "JavaToolchain(javaHome=" + getDisplayName() + ")";
    }

    private String getBinaryPath(String java) {
        return "bin/" + OperatingSystem.current().getExecutableName(java);
    }
}
