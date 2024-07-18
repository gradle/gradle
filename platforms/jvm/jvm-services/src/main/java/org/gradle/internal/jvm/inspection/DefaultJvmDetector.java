/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.GradleException;
import org.gradle.internal.jvm.Jvm;
import org.gradle.jvm.toolchain.internal.InstallationLocation;

import javax.annotation.Nullable;
import java.io.File;

public class DefaultJvmDetector implements JvmDetector {

    private final JvmMetadataDetector detector;

    public DefaultJvmDetector(JvmMetadataDetector detector) {
        this.detector = detector;
    }

    @Nullable
    @Override
    public Jvm tryDetectJvm(File javaHome) {
        if (!javaHome.exists()) {
            return null;
        }

        JvmInstallationMetadata metadata = detector.getMetadata(InstallationLocation.autoDetected(javaHome, "specific path"));

        if (!metadata.isValidInstallation()) {
            return null;
        }

        return metadata.asJvm();
    }

    @Override
    public Jvm detectJvm(File javaHome) {
        if (!javaHome.exists()) {
            throw new GradleException("JVM at path '" + javaHome + "' does not exist.");
        }

        JvmInstallationMetadata metadata = detector.getMetadata(InstallationLocation.autoDetected(javaHome, "specific path"));

        if (!metadata.isValidInstallation()) {
            throw new GradleException("JVM at path '" + javaHome + "' is invalid.", metadata.getErrorCause());
        }

        return metadata.asJvm();
    }
}
