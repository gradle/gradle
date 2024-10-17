/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.api.tasks.compile.ForkOptions;
import org.gradle.internal.deprecation.DeprecationLogger;

import javax.annotation.Nullable;
import java.io.File;

public class MinimalJavaCompilerDaemonForkOptions extends MinimalCompilerDaemonForkOptions {
    private String executable;

    private String tempDir;

    private File javaHome;

    public MinimalJavaCompilerDaemonForkOptions(ForkOptions forkOptions) {
        super(forkOptions);
        @SuppressWarnings("deprecation")
        File customJavaHome = DeprecationLogger.whileDisabled(forkOptions::getJavaHome);
        this.javaHome = customJavaHome;
        this.executable = forkOptions.getExecutable().getOrNull();
        this.tempDir = forkOptions.getTempDir().getOrNull();
        setJvmArgs(forkOptions.getAllJvmArgs().get());
    }

    @Nullable
    public String getExecutable() {
        return executable;
    }

    public void setExecutable(@Nullable String executable) {
        this.executable = executable;
    }

    @Nullable
    public String getTempDir() {
        return tempDir;
    }

    public void setTempDir(@Nullable String tempDir) {
        this.tempDir = tempDir;
    }

    @Nullable
    public File getJavaHome() {
        return javaHome;
    }

    public void setJavaHome(@Nullable File javaHome) {
        this.javaHome = javaHome;
    }
}
