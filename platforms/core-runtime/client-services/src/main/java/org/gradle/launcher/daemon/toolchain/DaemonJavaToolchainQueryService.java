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

package org.gradle.launcher.daemon.toolchain;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.GradleException;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.jvm.inspection.JavaInstallationRegistry;
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata;
import org.gradle.internal.jvm.inspection.JvmInstallationMetadataComparator;
import org.gradle.internal.jvm.inspection.JvmToolchainMetadata;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.jvm.toolchain.internal.JvmInstallationMetadataMatcher;

import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Predicate;

public class DaemonJavaToolchainQueryService {

    private final JavaInstallationRegistry javaInstallationRegistry;
    private final File currentJavaHome;

    public DaemonJavaToolchainQueryService(JavaInstallationRegistry javaInstallationRegistry) {
        this(javaInstallationRegistry, Jvm.current().getJavaHome());
    }

    @VisibleForTesting
    public DaemonJavaToolchainQueryService(JavaInstallationRegistry javaInstallationRegistry, File currentJavaHome) {
        this.javaInstallationRegistry = javaInstallationRegistry;
        this.currentJavaHome = currentJavaHome;
    }

    public JvmInstallationMetadata findMatchingToolchain(DaemonJvmCriteria.Spec toolchainSpec) throws GradleException {
        Optional<JvmToolchainMetadata> installation = locateToolchain(toolchainSpec);
        if (!installation.isPresent()) {
            String exceptionMessage = String.format(
                "Cannot find a Java installation on your machine (%s) matching: %s.", OperatingSystem.current(), toolchainSpec
            );
            throw new GradleException(exceptionMessage);
        }
        return installation.get().metadata;
    }

    private Optional<JvmToolchainMetadata> locateToolchain(DaemonJvmCriteria.Spec toolchainSpec) {
        Predicate<JvmInstallationMetadata> matcher = new JvmInstallationMetadataMatcher(
            toolchainSpec.getJavaVersion(), toolchainSpec.getVendorSpec(), toolchainSpec.getJvmImplementation(), Collections.emptySet()
        );
        JvmInstallationMetadataComparator metadataComparator = new JvmInstallationMetadataComparator(currentJavaHome);

        return javaInstallationRegistry.toolchains().stream()
            .filter(result -> result.metadata.isValidInstallation())
            .filter(result -> matcher.test(result.metadata))
            .min(Comparator.comparing(result -> result.metadata, metadataComparator));
    }
}
