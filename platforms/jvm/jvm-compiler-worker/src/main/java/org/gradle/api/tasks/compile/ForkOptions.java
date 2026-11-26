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
import org.gradle.internal.instrumentation.api.annotations.BackedByLazyProperty;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;
import org.jspecify.annotations.Nullable;

import java.io.File;

/**
 * Fork options for Java compilation. Only take effect if {@code CompileOptions.fork} is {@code true}.
 * @since 0.7
 */
public abstract class ForkOptions extends ProviderAwareCompilerDaemonForkOptions {
    private static final long serialVersionUID = 0;

    private CompileOptionStorage<String> executable = CompileOptionStorage.inField(null);

    private CompileOptionStorage<String> tempDir = CompileOptionStorage.inField(null);

    private CompileOptionStorage<File> javaHome = CompileOptionStorage.inField(null);

    @Nullable
    private ForkOptionsInputs lazyProperties;

    void attach(ForkOptionsInputs lazyProperties) {
        super.attach(lazyProperties);
        this.lazyProperties = lazyProperties;
        executable = executable.movedTo(lazyProperties.getExecutable());
        tempDir = tempDir.movedTo(lazyProperties.getTempDir());
        javaHome = javaHome.movedTo(lazyProperties.getJavaHome());
    }

    // Read through @BackedByLazyProperty, see BaseForkOptions
    @Nullable
    @SuppressWarnings("unused")
    private ForkOptionsInputs getLazyProperties() {
        return lazyProperties;
    }

    /**
     * Returns the compiler executable to be used.
     * <p>
     * Only takes effect if {@code CompileOptions.fork} is {@code true}. Defaults to {@code null}.
     * <p>
     * Setting the executable disables task output caching.
     * @since 0.7
     */
    @Nullable
    @Optional
    @Input
    @ToBeReplacedByLazyProperty
    @BackedByLazyProperty("lazyProperties.executable")
    public String getExecutable() {
        return executable.get();
    }

    /**
     * Sets the compiler executable to be used.
     * <p>
     * Only takes effect if {@code CompileOptions.fork} is {@code true}. Defaults to {@code null}.
     * <p>
     * Setting the executable disables task output caching.
     * @since 0.7
     */
    public void setExecutable(@Nullable String executable) {
        this.executable.set(executable);
    }

    /**
     * Returns the Java home which contains the compiler to use.
     * <p>
     * Only takes effect if {@code CompileOptions.fork} is {@code true}. Defaults to {@code null}.
     *
     * @since 3.5
     */
    @Internal
    @Nullable
    @ToBeReplacedByLazyProperty
    @BackedByLazyProperty("lazyProperties.javaHome")
    public File getJavaHome() {
        return javaHome.get();
    }

    /**
     * Sets the Java home which contains the compiler to use.
     * <p>
     * Only takes effect if {@code CompileOptions.fork} is {@code true}. Defaults to {@code null}.
     *
     * @since 3.5
     */
    public void setJavaHome(@Nullable File javaHome) {
        this.javaHome.set(javaHome);
    }

    /**
     * Returns the directory used for temporary files that may be created to pass
     * command line arguments to the compiler process. Defaults to {@code null},
     * in which case the directory will be chosen automatically.
     * @since 0.7
     */
    @Internal
    @Nullable
    @ToBeReplacedByLazyProperty
    @BackedByLazyProperty("lazyProperties.tempDir")
    public String getTempDir() {
        return tempDir.get();
    }

    /**
     * Sets the directory used for temporary files that may be created to pass
     * command line arguments to the compiler process. Defaults to {@code null},
     * in which case the directory will be chosen automatically.
     * @since 0.7
     */
    public void setTempDir(@Nullable String tempDir) {
        this.tempDir.set(tempDir);
    }
}
