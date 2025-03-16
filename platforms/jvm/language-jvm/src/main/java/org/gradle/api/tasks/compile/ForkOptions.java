/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.tasks.compile;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;
import org.jspecify.annotations.Nullable;

import java.io.File;

/**
 * Fork options for Java compilation. Only take effect if {@code CompileOptions.fork} is {@code true}.
 */
public abstract class ForkOptions extends ProviderAwareCompilerDaemonForkOptions {
    private static final long serialVersionUID = 0;

    private String executable;

    private String tempDir;

    private File javaHome;

    /**
     * Returns the compiler executable to be used.
     * <p>
     * Only takes effect if {@code CompileOptions.fork} is {@code true}. Defaults to {@code null}.
     * <p>
     * Setting the executable disables task output caching.
     */
    @Nullable
    @Optional
    @Input
    @ToBeReplacedByLazyProperty
    public String getExecutable() {
        return executable;
    }

    /**
     * Sets the compiler executable to be used.
     * <p>
     * Only takes effect if {@code CompileOptions.fork} is {@code true}. Defaults to {@code null}.
     * <p>
     * Setting the executable disables task output caching.
     */
    public void setExecutable(@Nullable String executable) {
        this.executable = executable;
    }

    /**
     * Returns the Java home which contains the compiler to use.
     * <p>
     * Only takes effect if {@code CompileOptions.fork} is {@code true}. Defaults to {@code null}.
     *
     * @since 3.5
     */
    @Deprecated
    @Internal
    @Nullable
    public File getJavaHome() {
        DeprecationLogger.deprecateMethod(ForkOptions.class, "getJavaHome()")
            .withAdvice("The 'javaHome' property of ForkOptions is deprecated and will be removed in Gradle 9. Use JVM toolchains or the 'executable' property instead.")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "deprecated_fork_options_java_home")
            .nagUser();
        return javaHome;
    }

    /**
     * Sets the Java home which contains the compiler to use.
     * <p>
     * Only takes effect if {@code CompileOptions.fork} is {@code true}. Defaults to {@code null}.
     *
     * @since 3.5
     */
    @Deprecated
    public void setJavaHome(@Nullable File javaHome) {
        DeprecationLogger.deprecateMethod(ForkOptions.class, "setJavaHome(File)")
            .withAdvice("The 'javaHome' property of ForkOptions is deprecated and will be removed in Gradle 9. Use JVM toolchains or the 'executable' property instead.")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "deprecated_fork_options_java_home")
            .nagUser();
        this.javaHome = javaHome;
    }

    /**
     * Returns the directory used for temporary files that may be created to pass
     * command line arguments to the compiler process. Defaults to {@code null},
     * in which case the directory will be chosen automatically.
     */
    @Internal
    @Nullable
    @ToBeReplacedByLazyProperty
    public String getTempDir() {
        return tempDir;
    }

    /**
     * Sets the directory used for temporary files that may be created to pass
     * command line arguments to the compiler process. Defaults to {@code null},
     * in which case the directory will be chosen automatically.
     */
    public void setTempDir(@Nullable String tempDir) {
        this.tempDir = tempDir;
    }
}
