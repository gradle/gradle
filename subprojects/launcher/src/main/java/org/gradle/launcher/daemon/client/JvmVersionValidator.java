/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.launcher.daemon.client;

import org.gradle.api.GradleException;
import org.gradle.api.JavaVersion;
import org.gradle.internal.jvm.Jvm;
import org.gradle.launcher.daemon.configuration.DaemonParameters;
import org.gradle.process.internal.ExecHandleBuilder;
import org.gradle.util.GradleVersion;

import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JvmVersionValidator {
    void validate(DaemonParameters parameters) {
        if (parameters.getEffectiveJavaHome().equals(Jvm.current().getJavaHome())) {
            return;
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        ExecHandleBuilder builder = new ExecHandleBuilder();
        builder.setWorkingDir(new File(".").getAbsolutePath());
        builder.setCommandLine(parameters.getEffectiveJavaExecutable(), "-version");
        builder.setStandardOutput(new ByteArrayOutputStream());
        builder.setErrorOutput(outputStream);
        builder.build().start().waitForFinish().assertNormalExitValue();

        JavaVersion javaVersion = parse(new BufferedReader(new InputStreamReader(new ByteArrayInputStream(outputStream.toByteArray()))));
        if (!javaVersion.isJava6Compatible()) {
            throw new GradleException(String.format("%s requires Java 6 or later to run. Your build is currently configured to use Java %s.", GradleVersion.current(), javaVersion.getMajorVersion()));
        }
    }

    private JavaVersion parse(BufferedReader reader) {
        try {
            String versionStr = reader.readLine();
            if (versionStr != null) {
                Matcher matcher = Pattern.compile("java version \"(.+?)\"").matcher(versionStr);
                if (matcher.matches()) {
                    return JavaVersion.toVersion(matcher.group(1));
                }
            }
        } catch (IOException e) {
            throw new org.gradle.api.UncheckedIOException(e);
        }

        throw new RuntimeException("Could not determine Java version.");
    }
}
