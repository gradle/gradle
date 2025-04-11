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

import com.google.common.annotations.VisibleForTesting;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.os.OperatingSystem;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public class DefaultToolchainConfiguration implements ToolchainConfiguration {
    private Collection<String> javaInstallationsFromEnvironment;
    private Collection<String> installationsFromPaths;
    private boolean autoDetectEnabled;
    private boolean downloadEnabled;
    private File intellijInstallationDirectory;

    private final SystemProperties systemProperties;
    private final Map<String, String> environment;

    @Inject
    public DefaultToolchainConfiguration() {
        this(System.getenv());
    }

    public DefaultToolchainConfiguration(Map<String, String> environment) {
        this(OperatingSystem.current(), SystemProperties.getInstance(), environment);
    }

    @VisibleForTesting
    DefaultToolchainConfiguration(OperatingSystem os, SystemProperties systemProperties, Map<String, String> environment) {
        this.systemProperties = systemProperties;
        this.environment = environment;
        this.autoDetectEnabled = true;
        this.downloadEnabled = true;
        this.intellijInstallationDirectory = defaultJdksDirectory(os);
        this.javaInstallationsFromEnvironment = Collections.emptyList();
        this.installationsFromPaths = Collections.emptyList();
    }

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

    @Override
    public File getAsdfDataDirectory() {
        String asdfEnvVar = environment.get("ASDF_DATA_DIR");
        if (asdfEnvVar != null) {
            return new File(asdfEnvVar);
        }
        return new File(systemProperties.getUserHome(), ".asdf");
    }

    @Override
    public File getIntelliJdkDirectory() {
        return intellijInstallationDirectory;
    }

    @Override
    public void setIntelliJdkDirectory(File intellijInstallationDirectory) {
        this.intellijInstallationDirectory = intellijInstallationDirectory;
    }

    private File defaultJdksDirectory(OperatingSystem os) {
        if (os.isMacOsX()) {
            return new File(systemProperties.getUserHome(), "Library/Java/JavaVirtualMachines");
        }
        return new File(systemProperties.getUserHome(), ".jdks");
    }

    @Override
    public File getJabbaHomeDirectory() {
        String jabbaHome = environment.get("JABBA_HOME");
        if (jabbaHome != null) {
            return new File(jabbaHome);
        }
        return null;
    }

    @Override
    public File getSdkmanCandidatesDirectory() {
        String asdfEnvVar = environment.get("SDKMAN_CANDIDATES_DIR");
        if (asdfEnvVar != null) {
            return new File(asdfEnvVar);
        }
        return new File(systemProperties.getUserHome(), ".sdkman/candidates");
    }

    @Nullable
    @Override
    public String getEnvironmentVariableValue(String variableName) {
        return environment.get(variableName);
    }
}
