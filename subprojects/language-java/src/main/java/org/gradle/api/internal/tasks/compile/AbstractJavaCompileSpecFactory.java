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
            File customJavaHome = compileOptions.getForkOptions().getJavaHome();
            if (customJavaHome != null) {
                return getCommandLineSpec(Jvm.forHome(customJavaHome).getJavacExecutable());
            }

            String customExecutable = compileOptions.getForkOptions().getExecutable();
            if (customExecutable != null) {
                return getCommandLineSpec(JavaExecutableUtils.resolveExecutable(customExecutable));
            }

            return getForkingSpec(Jvm.current().getJavaHome());
        }

        return getDefaultSpec();
    }

    private T chooseSpecForToolchain() {
        File toolchainJavaHome = toolchain.getInstallationPath().getAsFile();
        if (!toolchain.getLanguageVersion().canCompileOrRun(8)) {
            return getCommandLineSpec(Jvm.forHome(toolchainJavaHome).getJavacExecutable());
        }

        if (compileOptions.isFork()) {
            // Presence of the fork options means that the user has explicitly requested a command-line compiler
            if (compileOptions.getForkOptions().getJavaHome() != null || compileOptions.getForkOptions().getExecutable() != null) {
                // We use the toolchain path because the fork options must agree with the selected toolchain
                return getCommandLineSpec(Jvm.forHome(toolchainJavaHome).getJavacExecutable());
            }

            return getForkingSpec(toolchainJavaHome);
        }

        if (!toolchain.isCurrentJvm()) {
            return getForkingSpec(toolchainJavaHome);
        }

        return getDefaultSpec();
    }

    abstract protected T getCommandLineSpec(File executable);

    abstract protected T getForkingSpec(File javaHome);

    abstract protected T getDefaultSpec();
}
