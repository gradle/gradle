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
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.jvm.JavaInfo;
import org.gradle.internal.jvm.Jvm;
import org.gradle.process.internal.ExecHandleBuilder;
import org.gradle.process.internal.ExecHandleFactory;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Probes a JVM installation to determine the Java version it provides.
 */
public class JvmVersionDetector {
    private final Map<JavaInfo, JavaVersion> javaHomeResults = new HashMap<JavaInfo, JavaVersion>();
    private final Map<String, JavaVersion> javaCmdResults = new HashMap<String, JavaVersion>();
    private final ExecHandleFactory execHandleFactory;

    public JvmVersionDetector(ExecHandleFactory execHandleFactory) {
        this.execHandleFactory = execHandleFactory;
        javaHomeResults.put(Jvm.current(), JavaVersion.current());
        javaCmdResults.put(Jvm.current().getJavaExecutable().getPath(), JavaVersion.current());
    }

    /**
     * Probes the Java version for the given JVM installation.
     */
    public JavaVersion getJavaVersion(JavaInfo jvm) {
        JavaVersion version = javaHomeResults.get(jvm);
        if (version != null) {
            return version;
        }

        version = getJavaVersion(jvm.getJavaExecutable().getPath());
        javaHomeResults.put(jvm, version);

        return version;
    }

    /**
     * Probes the Java version for the given `java` command.
     */
    public JavaVersion getJavaVersion(String javaCommand) {
        JavaVersion version = javaCmdResults.get(javaCommand);
        if (version != null) {
            return version;
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        ExecHandleBuilder builder = execHandleFactory.newExec();
        builder.setWorkingDir(new File(".").getAbsolutePath());
        builder.setCommandLine(javaCommand, "-version");
        builder.setStandardOutput(new ByteArrayOutputStream());
        builder.setErrorOutput(outputStream);
        builder.build().start().waitForFinish().assertNormalExitValue();

        version = parseJavaVersionCommandOutput(javaCommand, new BufferedReader(new InputStreamReader(new ByteArrayInputStream(outputStream.toByteArray()))));
        javaCmdResults.put(javaCommand, version);
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
