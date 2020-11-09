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

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.io.Files;
import org.gradle.api.JavaVersion;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.ExecException;
import org.gradle.process.internal.ExecHandleBuilder;
import org.gradle.process.internal.ExecHandleFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.EnumMap;



public class DefaultJvmMetadataDetector implements JvmMetadataDetector {

    private final ExecHandleFactory execHandleFactory;
    private final Supplier<File> probeClass = Suppliers.memoize(this::writeProbeClass);

    public DefaultJvmMetadataDetector(ExecHandleFactory execHandleFactory) {
        this.execHandleFactory = execHandleFactory;
    }

    @Override
    public JvmInstallationMetadata getMetadata(File javaHome) {
        if (!javaHome.exists()) {
            return JvmInstallationMetadata.failure(javaHome, "No such directory: " + javaHome);
        }
        EnumMap<ProbedSystemProperty, String> metadata;
        if (Jvm.current().getJavaHome().equals(javaHome)) {
            metadata = getMetadataFromCurrentJvm();
        } else {
            metadata = getMetadataFromInstallation(javaHome);
        }
        return asMetadata(javaHome, metadata);
    }

    private EnumMap<ProbedSystemProperty, String> getMetadataFromCurrentJvm() {
        EnumMap<ProbedSystemProperty, String> result = new EnumMap<>(ProbedSystemProperty.class);
        for (ProbedSystemProperty type : ProbedSystemProperty.values()) {
            if (type != ProbedSystemProperty.Z_ERROR) {
                result.put(type, System.getProperty(type.getSystemPropertyKey()));
            }
        }
        return result;
    }

    private JvmInstallationMetadata asMetadata(File javaHome, EnumMap<ProbedSystemProperty, String> metadata) {
        String implementationVersion = metadata.get(ProbedSystemProperty.VERSION);
        if (implementationVersion == null) {
            return JvmInstallationMetadata.failure(javaHome, metadata.get(ProbedSystemProperty.Z_ERROR));
        }
        try {
            JavaVersion.toVersion(implementationVersion);
        } catch(IllegalArgumentException e) {
            return JvmInstallationMetadata.failure(javaHome, "Cannot parse version number: " + implementationVersion);
        }
        String vendor = metadata.get(ProbedSystemProperty.VENDOR);
        String implementationName = metadata.get(ProbedSystemProperty.VM);
        return JvmInstallationMetadata.from(javaHome, implementationVersion, vendor, implementationName);
    }

    private EnumMap<ProbedSystemProperty, String> getMetadataFromInstallation(File jdkPath) {
        File probe = probeClass.get();
        ExecHandleBuilder exec = execHandleFactory.newExec();
        exec.setWorkingDir(probe.getParentFile());
        exec.executable(javaExecutable(jdkPath));
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
            String mainClassname = Files.getNameWithoutExtension(probe.getName());
            exec.args("-cp", ".", mainClassname);
            exec.setStandardOutput(out);
            exec.setErrorOutput(errorOutput);
            exec.setIgnoreExitValue(true);
            ExecResult result = exec.build().start().waitForFinish();
            int exitValue = result.getExitValue();
            if (exitValue == 0) {
                return parseExecOutput(out.toString());
            }
            return error("Command returned unexpected result code: " + exitValue + "\nError output:\n" + errorOutput);
        } catch (ExecException ex) {
            return error(ex.getMessage());
        }
    }


    private static File javaExecutable(File jdkPath) {
        return new File(new File(jdkPath, "bin"), OperatingSystem.current().getExecutableName("java"));
    }

    private static EnumMap<ProbedSystemProperty, String> parseExecOutput(String probeResult) {
        String[] split = probeResult.split(System.getProperty("line.separator"));
        if (split.length != ProbedSystemProperty.values().length - 1) { // -1 because of Z_ERROR
            return error("Unexpected command output: \n" + probeResult);
        }
        EnumMap<ProbedSystemProperty, String> result = new EnumMap<>(ProbedSystemProperty.class);
        for (ProbedSystemProperty type : ProbedSystemProperty.values()) {
            if (type != ProbedSystemProperty.Z_ERROR) {
                result.put(type, split[type.ordinal()].trim());
            }
        }
        return result;
    }

    private static EnumMap<ProbedSystemProperty, String> error(String message) {
        EnumMap<ProbedSystemProperty, String> result = new EnumMap<>(ProbedSystemProperty.class);
        result.put(ProbedSystemProperty.Z_ERROR, message);
        return result;
    }

    private File writeProbeClass() {
        File probe = new MetadataProbe().writeClass(Files.createTempDir());
        probe.deleteOnExit();
        return probe;
    }

}
