/*
 * Copyright 2020 the original author or authors.
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

import com.google.common.io.Files;
import org.gradle.api.JavaVersion;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.jvm.toolchain.internal.InstallationLocation;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.ClientExecHandleBuilder;
import org.gradle.process.internal.ClientExecHandleBuilderFactory;
import org.gradle.process.internal.ExecException;
import org.gradle.util.internal.GFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Arrays;
import java.util.EnumMap;


public class DefaultJvmMetadataDetector implements JvmMetadataDetector {

    private final ClientExecHandleBuilderFactory execHandleFactory;
    private final TemporaryFileProvider temporaryFileProvider;

    private final Logger logger = LoggerFactory.getLogger(DefaultJvmMetadataDetector.class);

    @Inject
    public DefaultJvmMetadataDetector(
        final ClientExecHandleBuilderFactory execHandleFactory,
        final TemporaryFileProvider temporaryFileProvider
    ) {
        this.execHandleFactory = execHandleFactory;
        this.temporaryFileProvider = temporaryFileProvider;
    }

    @Override
    public JvmInstallationMetadata getMetadata(InstallationLocation javaInstallationLocation) {
        File javaHome = javaInstallationLocation.getLocation();
        if (javaHome == null || !javaHome.exists()) {
            return failure(javaHome, "No such directory: " + javaHome);
        }
        if (Jvm.current().getJavaHome().equals(javaHome)) {
            return getMetadataFromCurrentJvm(javaHome);
        }
        return getMetadataFromInstallation(javaHome);
    }

    private JvmInstallationMetadata getMetadataFromCurrentJvm(File javaHome) {
        EnumMap<ProbedSystemProperty, String> result = new EnumMap<>(ProbedSystemProperty.class);
        for (ProbedSystemProperty type : ProbedSystemProperty.values()) {
            if (type != ProbedSystemProperty.Z_ERROR) {
                result.put(type, System.getProperty(type.getSystemPropertyKey()));
            }
        }
        logger.info("Received JVM installation metadata from '{}': {}", javaHome.getAbsolutePath(), result);
        return asMetadata(javaHome, result);
    }

    private JvmInstallationMetadata asMetadata(File javaHome, EnumMap<ProbedSystemProperty, String> metadata) {
        String javaVersion = metadata.get(ProbedSystemProperty.JAVA_VERSION);
        if (javaVersion == null) {
            return failure(javaHome, metadata.get(ProbedSystemProperty.Z_ERROR));
        }
        try {
            JavaVersion.toVersion(javaVersion);
        } catch (IllegalArgumentException e) {
            return failure(javaHome, "Cannot parse version number: " + javaVersion);
        }
        String javaVendor = metadata.get(ProbedSystemProperty.JAVA_VENDOR);
        String runtimeName = metadata.get(ProbedSystemProperty.RUNTIME_NAME);
        String runtimeVersion = metadata.get(ProbedSystemProperty.RUNTIME_VERSION);
        String jvmName = metadata.get(ProbedSystemProperty.VM_NAME);
        String jvmVersion = metadata.get(ProbedSystemProperty.VM_VERSION);
        String jvmVendor = metadata.get(ProbedSystemProperty.VM_VENDOR);
        String architecture = metadata.get(ProbedSystemProperty.OS_ARCH);
        return JvmInstallationMetadata.from(javaHome, javaVersion, javaVendor, runtimeName, runtimeVersion, jvmName, jvmVersion, jvmVendor, architecture);
    }

    private JvmInstallationMetadata getMetadataFromInstallation(File jdkPath) {
        File tmpDir = temporaryFileProvider.createTemporaryDirectory("jvm", "probe");
        File probe = writeProbeClass(tmpDir);
        ClientExecHandleBuilder exec = execHandleFactory.newExecHandleBuilder();
        exec.setWorkingDir(probe.getParentFile());
        exec.setExecutable(javaExecutable(jdkPath).getAbsolutePath());
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
            String mainClassname = Files.getNameWithoutExtension(probe.getName());
            exec.args("-Xmx32m", "-Xms32m", "-cp", ".", mainClassname);
            exec.setStandardOutput(out);
            exec.setErrorOutput(errorOutput);
            ExecResult result = exec.build().start().waitForFinish();
            int exitValue = result.getExitValue();
            if (exitValue == 0) {
                return parseExecOutput(jdkPath, out.toString());
            }
            String errorMessage = "Command returned unexpected result code: " + exitValue + "\nError output:\n" + errorOutput;
            logger.debug("Failed to get metadata from JVM installation at '{}'. {}", jdkPath, errorMessage);
            return failure(jdkPath, errorMessage);
        } catch (ExecException ex) {
            logger.debug("Failed to get metadata from JVM installation at '{}'.", jdkPath, ex);
            return failure(jdkPath, ex);
        } finally {
            GFileUtils.deleteQuietly(tmpDir);
        }
    }


    private static File javaExecutable(File jdkPath) {
        return new File(new File(jdkPath, "bin"), OperatingSystem.current().getExecutableName("java"));
    }

    private JvmInstallationMetadata parseExecOutput(File jdkPath, String probeResult) {
        String[] split = Arrays.stream(probeResult.split(System.getProperty("line.separator")))
                .filter(line -> line.startsWith(MetadataProbe.MARKER_PREFIX))
                .map(line -> line.substring(MetadataProbe.MARKER_PREFIX.length()))
                .toArray(String[]::new);
        if (split.length != ProbedSystemProperty.values().length - 1) { // -1 because of Z_ERROR
            final String errorMessage = "Unexpected command output: \n" + probeResult;
            logger.info("Failed to parse JVM installation metadata output at '" + jdkPath + "'. " + errorMessage);
            return failure(jdkPath, errorMessage);
        }
        EnumMap<ProbedSystemProperty, String> result = new EnumMap<>(ProbedSystemProperty.class);
        for (ProbedSystemProperty type : ProbedSystemProperty.values()) {
            if (type != ProbedSystemProperty.Z_ERROR) {
                result.put(type, split[type.ordinal()].trim());
            }
        }
        logger.info("Received JVM installation metadata from '{}': {}", jdkPath.getAbsolutePath(), result);
        return asMetadata(jdkPath, result);
    }

    private JvmInstallationMetadata failure(File jdkPath, String errorMessage) {
        return JvmInstallationMetadata.failure(jdkPath, errorMessage);
    }

    private JvmInstallationMetadata failure(File jdkPath, Exception cause) {
        return JvmInstallationMetadata.failure(jdkPath, cause);
    }

    private File writeProbeClass(File tmpDir) {
        return new MetadataProbe().writeClass(tmpDir);
    }

}
