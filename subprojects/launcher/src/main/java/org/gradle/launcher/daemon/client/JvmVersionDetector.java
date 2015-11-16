/*
 * Copyright 2014 the original author or authors.
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
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.jvm.JavaInfo;
import org.gradle.internal.jvm.Jvm;
import org.gradle.process.internal.ExecHandleBuilder;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Probes a JVM installation to determine the Java version it provides.
 */
public class JvmVersionDetector {
    private final Map<JavaInfo, JavaVersion> cachedResults = new HashMap<JavaInfo, JavaVersion>();

    public JvmVersionDetector() {
        cachedResults.put(Jvm.current(), JavaVersion.current());
    }

    public JavaVersion getJavaVersion(JavaInfo jvm) {
        JavaVersion version = cachedResults.get(jvm);
        if (version != null) {
            return version;
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        ExecHandleBuilder builder = new ExecHandleBuilder();
        builder.setWorkingDir(new File(".").getAbsolutePath());
        builder.setCommandLine(jvm.getJavaExecutable(), "-version");
        builder.setStandardOutput(new ByteArrayOutputStream());
        builder.setErrorOutput(outputStream);
        builder.build().start().waitForFinish().assertNormalExitValue();

        version = parseJavaVersionCommandOutput(jvm.getJavaExecutable().getPath(), new BufferedReader(new InputStreamReader(new ByteArrayInputStream(outputStream.toByteArray()))));
        cachedResults.put(jvm, version);
        return version;
    }

    JavaVersion parseJavaVersionCommandOutput(String javaExecutable, BufferedReader reader) {
        try {
            String versionStr = reader.readLine();
            while (versionStr != null) {
                Matcher matcher = Pattern.compile("(?:java|openjdk) version \"(.+?)\"").matcher(versionStr);
                if (matcher.matches()) {
                    return JavaVersion.toVersion(matcher.group(1));
                }
                versionStr = reader.readLine();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        throw new GradleException(String.format("Could not determine Java version using executable %s.", javaExecutable));
    }
}
