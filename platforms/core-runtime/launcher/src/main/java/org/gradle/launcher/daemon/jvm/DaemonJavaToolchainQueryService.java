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

package org.gradle.launcher.daemon.jvm;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.StartParameter;
import org.gradle.api.GradleException;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.jvm.inspection.JavaInstallationRegistry;
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata;
import org.gradle.internal.jvm.inspection.JvmInstallationMetadataComparator;
import org.gradle.internal.jvm.inspection.JvmToolchainMetadata;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.internal.JvmInstallationMetadataMatcher;

import java.io.File;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Predicate;

public class DaemonJavaToolchainQueryService {

    private final JavaInstallationRegistryFactory javaInstallationRegistryFactory;
    private final File currentJavaHome;

    public DaemonJavaToolchainQueryService(JavaInstallationRegistryFactory javaInstallationRegistryFactory) {
        this(javaInstallationRegistryFactory, Jvm.current().getJavaHome());
    }

    @VisibleForTesting
    public DaemonJavaToolchainQueryService(JavaInstallationRegistryFactory javaInstallationRegistryFactory, File currentJavaHome) {
        this.javaInstallationRegistryFactory = javaInstallationRegistryFactory;
        this.currentJavaHome = currentJavaHome;
    }

    public JvmInstallationMetadata findMatchingToolchain(DaemonJvmCriteria toolchainSpec, StartParameter parameters) throws GradleException {
        Optional<JvmToolchainMetadata> installation = locateToolchain(toolchainSpec, parameters);
        if (!installation.isPresent()) {
            String exceptionMessage = String.format(
                "Cannot find a Java installation on your machine matching the Daemon JVM defined requirements: %s for %s.", toolchainSpec, OperatingSystem.current()
            );
            throw new GradleException(exceptionMessage);
        }
        return installation.get().metadata;
    }

    private Optional<JvmToolchainMetadata> locateToolchain(DaemonJvmCriteria toolchainSpec, StartParameter parameters) {
        JavaInstallationRegistry registry = javaInstallationRegistryFactory.getRegistry(parameters);
        Predicate<JvmInstallationMetadata> matcher = new JvmInstallationMetadataMatcher(JavaLanguageVersion.of(toolchainSpec.getJavaVersion().getMajorVersion()), toolchainSpec.getVendorSpec(), toolchainSpec.getJvmImplementation());
        JvmInstallationMetadataComparator metadataComparator = new JvmInstallationMetadataComparator(currentJavaHome);

        return registry.toolchains().stream()
            .filter(result -> result.metadata.isValidInstallation())
            .filter(result -> matcher.test(result.metadata))
            .min(Comparator.comparing(result -> result.metadata, metadataComparator));
    }
}
