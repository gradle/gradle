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
import org.gradle.internal.jvm.Jvm;
import org.gradle.jvm.toolchain.JavaDevelopmentKit;
import org.gradle.jvm.toolchain.JavaInstallation;
import org.gradle.jvm.toolchain.JavaInstallationRegistry;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Optional;

public class DefaultJavaInstallationRegistry implements JavaInstallationRegistry {
    private final JavaInstallationProbe installationProbe;
    private final ProviderFactory providerFactory;

    public DefaultJavaInstallationRegistry(JavaInstallationProbe installationProbe, ProviderFactory providerFactory) {
        this.installationProbe = installationProbe;
        this.providerFactory = providerFactory;
    }

    @Override
    public JavaInstallation getInstallationForCurrentVirtualMachine() {
        // TODO - should probably return a provider too
        return new DefaultJavaInstallation(installationProbe.current());
    }

    @Override
    public Provider<JavaInstallation> installationForDirectory(File javaHomeDir) {
        // TODO - should be a value source and so a build input if queried during configuration time
        return providerFactory.provider(() -> {
            try {
                return new DefaultJavaInstallation(installationProbe.checkJdk(javaHomeDir));
            } catch (Exception e) {
                throw new JavaInstallationDiscoveryException(String.format("Could not determine the details of Java installation in directory %s.", javaHomeDir), e);
            }
        });
    }

    @Contextual
    private static class JavaInstallationDiscoveryException extends GradleException {
        public JavaInstallationDiscoveryException(String message, @Nullable Throwable cause) {
            super(message, cause);
        }
    }

    private static class DefaultJavaInstallation implements JavaInstallation {
        private final Jvm jvm;

        public DefaultJavaInstallation(JavaInstallationProbe.ProbeResult probeResult) {
            this.jvm = Jvm.discovered(probeResult.getJavaHome(), probeResult.getImplementationJavaVersion(), probeResult.getJavaVersion());
        }

        @Override
        public JavaVersion getJavaVersion() {
            return jvm.getJavaVersion();
        }

        @Override
        public File getInstallationDirectory() {
            return jvm.getJavaHome();
        }

        @Override
        public File getJavaExecutable() {
            return jvm.getJavaExecutable();
        }

        @Override
        public Optional<JavaDevelopmentKit> getJdk() {
            if (jvm.isJdk()) {
                return Optional.of(new JavaDevelopmentKit() {
                });
            } else {
                return Optional.empty();
            }
        }
    }
}
