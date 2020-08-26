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
import org.gradle.api.JavaVersion;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.jvm.toolchain.JavaCompiler;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavadocTool;
import org.gradle.util.VersionNumber;

import javax.inject.Inject;
import java.io.File;

public class JavaToolchain implements Describable {

    private final boolean isJdk;
    private final JavaCompilerFactory compilerFactory;
    private final ToolchainToolFactory toolFactory;
    private final File javaHome;
    private final VersionNumber implementationVersion;
    private final JavaVersion javaVersion;

    @Inject
    public JavaToolchain(JavaInstallationProbe.ProbeResult probe, JavaCompilerFactory compilerFactory, ToolchainToolFactory toolFactory) {
        this(probe.getJavaHome(), probe.getJavaVersion(), probe.getImplementationJavaVersion(), probe.getInstallType() == JavaInstallationProbe.InstallType.IS_JDK, compilerFactory, toolFactory);
    }

    JavaToolchain(File javaHome, JavaVersion version, String implementationJavaVersion, boolean isJdk, JavaCompilerFactory compilerFactory, ToolchainToolFactory toolFactory) {
        this.javaHome = computeEnclosingJavaHome(javaHome);
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
        return new DefaultToolchainJavaLauncher(findExecutable("java"));
    }

    @Internal
    public JavadocTool getJavadocTool() {
        return toolFactory.create(JavadocTool.class, this);
    }

    @Input
    public JavaVersion getJavaMajorVersion() {
        return javaVersion;
    }

    @Internal
    public VersionNumber getToolVersion() {
        return implementationVersion;
    }

    @Internal
    public File getJavaHome() {
        return javaHome;
    }

    @Internal
    public boolean isJdk() {
        return isJdk;
    }

    @Internal
    @Override
    public String getDisplayName() {
        return javaHome.getAbsolutePath();
    }

    public File findExecutable(String toolname) {
        return new File(getJavaHome(), getBinaryPath(toolname));
    }

    private File computeEnclosingJavaHome(File home) {
        final File parentFile = home.getParentFile();
        final boolean isEmbeddedJre = home.getName().equalsIgnoreCase("jre");
        if (isEmbeddedJre && new File(parentFile, getBinaryPath("java")).exists()) {
            return parentFile;
        }
        return home;
    }

    private String getBinaryPath(String java) {
        return "bin/" + OperatingSystem.current().getExecutableName(java);
    }
}
