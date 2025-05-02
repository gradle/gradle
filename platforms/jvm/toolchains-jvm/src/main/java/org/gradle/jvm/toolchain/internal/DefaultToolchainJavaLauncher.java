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

import org.gradle.api.file.RegularFile;
import org.gradle.api.tasks.Internal;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.jvm.toolchain.JavaLauncher;

public class DefaultToolchainJavaLauncher implements JavaLauncher {

    private final JavaToolchain javaToolchain;

    public DefaultToolchainJavaLauncher(JavaToolchain javaToolchain) {
        this.javaToolchain = javaToolchain;
    }

    @Override
    @Internal
    public RegularFile getExecutablePath() {
        return javaToolchain.findExecutable("java");
    }

    @Override
    public JavaInstallationMetadata getMetadata() {
        return javaToolchain;
    }
}
