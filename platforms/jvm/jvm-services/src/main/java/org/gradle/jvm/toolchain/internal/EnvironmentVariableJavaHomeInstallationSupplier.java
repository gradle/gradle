/*
 * Copyright 2025 the original author or authors.
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

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collections;
import java.util.Set;

public class EnvironmentVariableJavaHomeInstallationSupplier implements InstallationSupplier {

    private final @Nullable String javaHomePath;

    public EnvironmentVariableJavaHomeInstallationSupplier(ToolchainConfiguration buildOptions) {
        this.javaHomePath = buildOptions.getEnvironmentVariableValue("JAVA_HOME");
    }

    @Override
    public String getSourceName() {
        return "environment variable 'JAVA_HOME'";
    }

    @Override
    public Set<InstallationLocation> get() {
        if (javaHomePath == null || javaHomePath.isEmpty()) {
            return Collections.emptySet();
        }
        File javaHome = new File(javaHomePath);
        InstallationLocation installationLocation = InstallationLocation.userDefined(javaHome, getSourceName());
        return Collections.singleton(installationLocation);
    }
}
