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
import org.gradle.jvm.toolchain.JavaCompiler;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavadocTool;
import org.gradle.util.internal.VersionNumber;

import java.nio.file.Path;

public class JavaToolchain implements Describable, JavaInstallationMetadata {

    private final JavaCompilerFactory compilerFactory;
    private final ToolchainToolFactory toolFactory;
    private final Directory javaHome;
    private final VersionNumber implementationVersion;
    private final JavaLanguageVersion javaVersion;
    private final JvmInstallationMetadata metadata;
    private final JavaToolchainInput input;

    public JavaToolchain(JvmInstallationMetadata metadata, JavaCompilerFactory compilerFactory, ToolchainToolFactory toolFactory, FileFactory fileFactory, JavaToolchainInput input) {
        this.javaHome = fileFactory.dir(computeEnclosingJavaHome(metadata.getJavaHome()).toFile());
        this.javaVersion = JavaLanguageVersion.of(metadata.getLanguageVersion().getMajorVersion());
        this.compilerFactory = compilerFactory;
        this.toolFactory = toolFactory;
        this.implementationVersion = VersionNumber.withPatchNumber().parse(metadata.getImplementationVersion());
        this.metadata = metadata;
        this.input = input;
    }

    @Nested
    protected JavaToolchainInput getTaskInputs() {
        return input;
    }

    @Internal
    public JavaCompiler getJavaCompiler() {
        return new DefaultToolchainJavaCompiler(this, compilerFactory);
    }

    @Internal
    public JavaLauncher getJavaLauncher() {
        return new DefaultToolchainJavaLauncher(this);
    }

    @Internal
    public JavadocTool getJavadocTool() {
        return toolFactory.create(JavadocTool.class, this);
    }

    @Internal
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
    public VersionNumber getToolVersion() {
        return implementationVersion;
    }

    @Internal
    public Directory getInstallationPath() {
        return javaHome;
    }

    @Internal
    public boolean isJdk() {
        return metadata.hasCapability(JvmInstallationMetadata.JavaInstallationCapability.JAVA_COMPILER);
    }

    @Internal
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

    public RegularFile findExecutable(String toolname) {
        return getInstallationPath().file(getBinaryPath(toolname));
    }

    private Path computeEnclosingJavaHome(Path home) {
        final Path parentPath = home.getParent();
        final boolean isEmbeddedJre = home.getFileName().toString().equalsIgnoreCase("jre");
        if (isEmbeddedJre && parentPath.resolve(getBinaryPath("java")).toFile().exists()) {
            return parentPath;
        }
        return home;
    }

    private String getBinaryPath(String java) {
        return "bin/" + OperatingSystem.current().getExecutableName(java);
    }
}
