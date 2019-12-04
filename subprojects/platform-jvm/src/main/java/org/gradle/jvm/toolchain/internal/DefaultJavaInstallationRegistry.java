/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.api.GradleException;
import org.gradle.api.JavaVersion;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.jvm.toolchain.JavaInstallation;
import org.gradle.jvm.toolchain.JavaInstallationRegistry;

import javax.annotation.Nullable;
import java.io.File;

public class DefaultJavaInstallationRegistry implements JavaInstallationRegistry {
    private final JavaInstallationProbe installationProbe;
    private final ProviderFactory providerFactory;

    public DefaultJavaInstallationRegistry(JavaInstallationProbe installationProbe, ProviderFactory providerFactory) {
        this.installationProbe = installationProbe;
        this.providerFactory = providerFactory;
    }

    @Override
    public JavaInstallation getThisVirtualMachine() {
        DefaultJavaInstallation installation = new DefaultJavaInstallation();
        installationProbe.current(installation);
        return installation;
    }

    @Override
    public Provider<JavaInstallation> forDirectory(File javaHomeDir) {
        return providerFactory.provider(() -> {
            DefaultJavaInstallation installation = new DefaultJavaInstallation();
            try {
                installationProbe.checkJdk(javaHomeDir).configure(installation);
            } catch (Exception e) {
                throw new JavaInstallationDiscoveryException(String.format("Could not determine the details of Java installation in directory %s.", javaHomeDir), e);
            }
            return installation;
        });
    }

    @Contextual
    private static class JavaInstallationDiscoveryException extends GradleException {
        public JavaInstallationDiscoveryException(String message, @Nullable Throwable cause) {
            super(message, cause);
        }
    }

    // This is effectively immutable. The setters are only used by the probe
    private static class DefaultJavaInstallation implements LocalJavaInstallation, JavaInstallation {
        private JavaVersion javaVersion;
        private File javaHome;

        @Override
        public JavaVersion getJavaVersion() {
            return javaVersion;
        }

        @Override
        public void setJavaVersion(JavaVersion javaVersion) {
            this.javaVersion = javaVersion;
        }

        @Override
        public File getJavaHome() {
            return javaHome;
        }

        @Override
        public void setJavaHome(File javaHome) {
            this.javaHome = javaHome;
        }

        @Override
        public String getDisplayName() {
            return null;
        }

        @Override
        public void setDisplayName(String displayName) {
        }

        @Override
        public String getName() {
            return null;
        }
    }
}
