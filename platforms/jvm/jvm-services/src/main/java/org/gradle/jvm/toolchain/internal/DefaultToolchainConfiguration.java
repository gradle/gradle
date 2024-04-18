/*
 * Copyright 2024 the original author or authors.
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

import java.util.Collection;
import java.util.Collections;

public class DefaultToolchainConfiguration implements ToolchainConfiguration {
    private Collection<String> javaInstallationsFromEnvironment = Collections.emptyList();
    private Collection<String> installationsFromPaths = Collections.emptyList();
    private boolean autoDetectEnabled = true;
    private boolean downloadEnabled = true;

    @Override
    public Collection<String> getJavaInstallationsFromEnvironment() {
        return javaInstallationsFromEnvironment;
    }

    @Override
    public void setJavaInstallationsFromEnvironment(Collection<String> javaInstallationsFromEnvironment) {
        this.javaInstallationsFromEnvironment = javaInstallationsFromEnvironment;
    }

    @Override
    public Collection<String> getInstallationsFromPaths() {
        return installationsFromPaths;
    }

    @Override
    public void setInstallationsFromPaths(Collection<String> installationsFromPaths) {
        this.installationsFromPaths = installationsFromPaths;
    }

    @Override
    public boolean isAutoDetectEnabled() {
        return autoDetectEnabled;
    }

    @Override
    public void setAutoDetectEnabled(boolean autoDetectEnabled) {
        this.autoDetectEnabled = autoDetectEnabled;
    }

    @Override
    public boolean isDownloadEnabled() {
        return downloadEnabled;
    }

    @Override
    public void setDownloadEnabled(boolean enabled) {
        this.downloadEnabled = enabled;
    }
}
