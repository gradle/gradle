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

package org.gradle.api.internal.tasks.compile;

import org.gradle.api.JavaVersion;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.internal.Factory;
import org.gradle.internal.jvm.Jvm;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.jvm.toolchain.internal.JavaExecutableUtils;

import javax.annotation.Nullable;
import java.io.File;

public abstract class AbstractJavaCompileSpecFactory<T extends JavaCompileSpec> implements Factory<T> {
    private final CompileOptions compileOptions;

    private final JavaInstallationMetadata toolchain;

    public AbstractJavaCompileSpecFactory(CompileOptions compileOptions, @Nullable JavaInstallationMetadata toolchain) {
        this.compileOptions = compileOptions;
        this.toolchain = toolchain;
    }

    @Override
    public T create() {
        if (toolchain != null) {
            return chooseSpecForToolchain();
        }

        if (compileOptions.isFork()) {
            return chooseSpecFromCompileOptions(Jvm.current().getJavaHome());
        }

        return getDefaultSpec();
    }

    private T chooseSpecForToolchain() {
        File toolchainJavaHome = toolchain.getInstallationPath().getAsFile();
        if (!toolchain.getLanguageVersion().canCompileOrRun(8)) {
            return getCommandLineSpec(Jvm.forHome(toolchainJavaHome).getJavacExecutable());
        }

        if (compileOptions.isFork()) {
            return chooseSpecFromCompileOptions(toolchainJavaHome);
        }

        if (!toolchain.isCurrentJvm()) {
            return getForkingSpec(toolchainJavaHome, toolchain.getLanguageVersion().asInt());
        }

        return getDefaultSpec();
    }

    private T chooseSpecFromCompileOptions(File fallbackJavaHome) {
        File forkJavaHome = compileOptions.getForkOptions().getJavaHome();
        if (forkJavaHome != null) {
            return getCommandLineSpec(Jvm.forHome(forkJavaHome).getJavacExecutable());
        }

        String forkExecutable = compileOptions.getForkOptions().getExecutable();
        if (forkExecutable != null) {
            return getCommandLineSpec(JavaExecutableUtils.resolveExecutable(forkExecutable));
        }

        final int languageVersion;
        if (toolchain != null) {
            languageVersion = toolchain.getLanguageVersion().asInt();
        } else {
            languageVersion = JavaVersion.current().getMajorVersionNumber();
        }

        return getForkingSpec(fallbackJavaHome, languageVersion);
    }

    abstract protected T getCommandLineSpec(File executable);

    abstract protected T getForkingSpec(File javaHome, int javaLanguageVersion);

    abstract protected T getDefaultSpec();
}
