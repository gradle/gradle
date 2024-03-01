/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.jvm.inspection;

import org.gradle.util.internal.VersionNumber;

import java.io.File;
import java.util.Comparator;

public class JvmInstallationMetadataComparator implements Comparator<JvmInstallationMetadata> {

    private final File currentJavaHome;

    public JvmInstallationMetadataComparator(File currentJavaHome) {
        this.currentJavaHome = currentJavaHome;
    }

    @Override
    public int compare(JvmInstallationMetadata o1, JvmInstallationMetadata o2) {
        return Comparator
            .comparing(this::isCurrentJvm)
            .thenComparing(this::isJdk)
            .thenComparing(this::extractVendor, Comparator.reverseOrder())
            .thenComparing(this::getToolchainVersion)
            // It is possible for different JDK builds to have exact same version. The input order
            // may change so the installation path breaks ties to keep sorted output consistent
            // between runs.
            .thenComparing(JvmInstallationMetadata::getJavaHome)
            .reversed()
            .compare(o1, o2);
    }

    boolean isCurrentJvm(JvmInstallationMetadata metadata) {
        return metadata.getJavaHome().toFile().equals(currentJavaHome);
    }

    private boolean isJdk(JvmInstallationMetadata metadata) {
        return metadata.hasCapability(JvmInstallationMetadata.JavaInstallationCapability.JAVA_COMPILER);
    }

    private JvmVendor.KnownJvmVendor extractVendor(JvmInstallationMetadata metadata) {
        return metadata.getVendor().getKnownVendor();
    }

    private VersionNumber getToolchainVersion(JvmInstallationMetadata metadata) {
        return VersionNumber.withPatchNumber().parse(metadata.getJavaVersion());
    }
}
