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
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.jvm.Jvm;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.jvm.toolchain.internal.JavaExecutableUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public abstract class AbstractJavaCompileSpecFactory<T extends JavaCompileSpec> implements Factory<T> {
    private final static Logger LOGGER = LoggerFactory.getLogger(AbstractJavaCompileSpecFactory.class);

    private final CompileOptions compileOptions;
    private final JavaInstallationMetadata toolchain;

    public AbstractJavaCompileSpecFactory(CompileOptions compileOptions, JavaInstallationMetadata toolchain) {
        this.compileOptions = compileOptions;
        this.toolchain = toolchain;
    }

    @Override
    public T create() {
        File toolchainJavaHome = toolchain.getInstallationPath().getAsFile();
        if (!toolchain.getLanguageVersion().canCompileOrRun(8)) {
            LOGGER.info("Compilation mode: command line compilation");
            return getCommandLineSpec(Jvm.forHome(toolchainJavaHome).getJavacExecutable());
        }

        if (compileOptions.isFork()) {
            @SuppressWarnings("deprecation")
            File forkJavaHome = DeprecationLogger.whileDisabled(compileOptions.getForkOptions()::getJavaHome);
            if (forkJavaHome != null) {
                LOGGER.info("Compilation mode: command line compilation");
                return getCommandLineSpec(Jvm.forHome(forkJavaHome).getJavacExecutable());
            }

            String forkExecutable = compileOptions.getForkOptions().getExecutable();
            if (forkExecutable != null) {
                LOGGER.info("Compilation mode: command line compilation");
                return getCommandLineSpec(JavaExecutableUtils.resolveExecutable(forkExecutable));
            }

            int languageVersion = toolchain.getLanguageVersion().asInt();
            LOGGER.info("Compilation mode: forking compiler");
            return getForkingSpec(toolchainJavaHome, languageVersion);
        }

        if (toolchain.isCurrentJvm() && JdkJavaCompiler.canBeUsed()) {
            // Please keep it in mind, that when using TestKit with debug enabled (i.e. in embedded mode), this line won't be reached after Java 16 (JEP 396)
            // If you need this to be executed, add the necessary configs from JPMSConfiguration to the test runner executing Gradle
            LOGGER.info("Compilation mode: in-process compilation");
            return getInProcessSpec();
        }

        LOGGER.info("Compilation mode: default, forking compiler");
        return getForkingSpec(toolchainJavaHome, toolchain.getLanguageVersion().asInt());
    }

    abstract protected T getCommandLineSpec(File executable);

    abstract protected T getForkingSpec(File javaHome, int javaLanguageVersion);

    abstract protected T getInProcessSpec();
}
