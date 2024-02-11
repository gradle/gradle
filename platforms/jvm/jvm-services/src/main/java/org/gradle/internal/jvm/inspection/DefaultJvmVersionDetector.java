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
import org.gradle.api.JavaVersion;
import org.gradle.internal.jvm.JavaInfo;
import org.gradle.jvm.toolchain.internal.InstallationLocation;
import org.gradle.process.internal.ExecException;

import java.io.File;
import java.nio.file.NoSuchFileException;

public class DefaultJvmVersionDetector implements JvmVersionDetector {

    private final JvmMetadataDetector detector;

    public DefaultJvmVersionDetector(JvmMetadataDetector detector) {
        this.detector = detector;
    }

    @Override
    public JavaVersion getJavaVersion(JavaInfo jvm) {
        return getVersionFromJavaHome(jvm.getJavaHome());
    }

    @Override
    public JavaVersion getJavaVersion(String javaCommand) {
        File executable = new File(javaCommand);
        File parentFolder = executable.getParentFile();
        if(parentFolder == null || !parentFolder.exists()) {
            Exception cause = new NoSuchFileException(javaCommand);
            throw new ExecException("A problem occurred starting process 'command '" + javaCommand + "''", cause);
        }
        return getVersionFromJavaHome(parentFolder.getParentFile());
    }

    private JavaVersion getVersionFromJavaHome(File javaHome) {
        return validate(detector.getMetadata(new InstallationLocation(javaHome, "specific path"))).getLanguageVersion();
    }

    private JvmInstallationMetadata validate(JvmInstallationMetadata metadata) {
        if(metadata.isValidInstallation()) {
            return metadata;
        }
        throw new GradleException("Unable to determine version for JDK located at " + metadata.getJavaHome() + ". Reason: " + metadata.getErrorMessage(), metadata.getErrorCause());
    }
}
