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
import org.gradle.jvm.toolchain.JavaCompiler;
import org.gradle.jvm.toolchain.JavaInstallation;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavadocTool;

import javax.inject.Inject;
import java.io.File;

public class JavaToolchain implements Describable {

    private final JavaInstallation installation;
    private JavaCompilerFactory compilerFactory;
    private final ToolchainToolFactory toolFactory;

    @Inject
    public JavaToolchain(JavaInstallation installation, JavaCompilerFactory compilerFactory, ToolchainToolFactory toolFactory) {
        this.installation = installation;
        this.compilerFactory = compilerFactory;
        this.toolFactory = toolFactory;
    }

    @Internal
    public JavaCompiler getJavaCompiler() {
        return new DefaultToolchainJavaCompiler(this, compilerFactory);
    }

    @Internal
    public JavaLauncher getJavaLauncher() {
        return new DefaultToolchainJavaLauncher(installation.getJavaExecutable().getAsFile());
    }

    @Internal
    public JavadocTool getJavadocTool() {
        return toolFactory.create(JavadocTool.class, this);
    }

    @Input
    public JavaVersion getJavaMajorVersion() {
        return installation.getJavaVersion();
    }

    @Internal
    public File getJavaHome() {
        return installation.getInstallationDirectory().getAsFile();
    }

    @Internal
    @Override
    public String getDisplayName() {
        return getJavaHome().getAbsolutePath();
    }

    public String findExecutable(String toolname) {
        return new File(getJavaHome(), "bin/javadoc").getAbsolutePath();
    }
}
