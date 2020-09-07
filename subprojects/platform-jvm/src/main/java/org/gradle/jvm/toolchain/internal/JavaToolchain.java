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
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.jvm.toolchain.JavaCompiler;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.jvm.toolchain.JavadocTool;
import org.gradle.util.VersionNumber;

import javax.inject.Inject;
import java.nio.file.Path;

public class JavaToolchain implements Describable, JavaInstallationMetadata {

    private final boolean isJdk;
    private final JavaCompilerFactory compilerFactory;
    private final ToolchainToolFactory toolFactory;
    private final Directory javaHome;
    private final VersionNumber implementationVersion;
    private final JavaLanguageVersion javaVersion;

    @Inject
    public JavaToolchain(JavaInstallationProbe.ProbeResult probe, JavaCompilerFactory compilerFactory, ToolchainToolFactory toolFactory, FileFactory fileFactory) {
        this(probe.getJavaHome(), JavaLanguageVersion.of(Integer.parseInt(probe.getJavaVersion().getMajorVersion())), probe.getImplementationJavaVersion(), probe.getInstallType() == JavaInstallationProbe.InstallType.IS_JDK, compilerFactory, toolFactory, fileFactory);
    }

    JavaToolchain(Path javaHome, JavaLanguageVersion version, String implementationJavaVersion, boolean isJdk, JavaCompilerFactory compilerFactory, ToolchainToolFactory toolFactory, FileFactory fileFactory) {
        this.javaHome = fileFactory.dir(computeEnclosingJavaHome(javaHome).toFile());
        this.javaVersion = version;
        this.isJdk = isJdk;
        this.compilerFactory = compilerFactory;
        this.toolFactory = toolFactory;
        this.implementationVersion = VersionNumber.parse(implementationJavaVersion);
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

    @Input
    public JavaLanguageVersion getLanguageVersion() {
        return javaVersion;
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
        return isJdk;
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
