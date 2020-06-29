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

import org.gradle.api.JavaVersion;
import org.gradle.api.internal.file.FileFactory;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.provider.Provider;
import org.gradle.jvm.toolchain.JavaInstallationRegistry;

import javax.inject.Inject;
import java.io.File;

public class JavaToolchain {

    // TODO: this is going to be a JavaInstallation
    private final File javaHome;
    private final JavaInstallationRegistry installationRegistry;
    private final FileFactory factory;

    @Inject
    public JavaToolchain(File javaHome, JavaInstallationRegistry installationRegistry, FileFactory fileFactory) {
        this.javaHome = javaHome;
        this.installationRegistry = installationRegistry;
        this.factory = fileFactory;
    }

    public Provider<DefaultJavaCompiler> getJavaCompiler() {
        return Providers.of(new DefaultJavaCompiler(this));
    }

    // TODO: cache me?
    public JavaVersion getJavaMajorVersion() {
        return installationRegistry.installationForDirectory(factory.dir(javaHome)).get().getJavaVersion();
    }

}
