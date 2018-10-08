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
import org.gradle.internal.io.NullOutputStream;
import org.gradle.internal.io.StreamByteBuffer;
import org.gradle.internal.jvm.JavaInfo;
import org.gradle.process.internal.ExecHandleBuilder;
import org.gradle.process.internal.ExecHandleFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DefaultJvmVersionDetector implements JvmVersionDetector {
    private final ExecHandleFactory execHandleFactory;

    public DefaultJvmVersionDetector(ExecHandleFactory execHandleFactory) {
        this.execHandleFactory = execHandleFactory;
    }

    @Override
    public JavaVersion getJavaVersion(JavaInfo jvm) {
        return getJavaVersion(jvm.getJavaExecutable().getPath());
    }

    @Override
    public JavaVersion getJavaVersion(String javaCommand) {
        StreamByteBuffer buffer = new StreamByteBuffer();

        ExecHandleBuilder builder = execHandleFactory.newExec();
        builder.setWorkingDir(new File(".").getAbsolutePath());
        builder.setCommandLine(javaCommand, "-version");
        builder.setStandardOutput(NullOutputStream.INSTANCE);
        builder.setErrorOutput(buffer.getOutputStream());
        builder.build().start().waitForFinish().assertNormalExitValue();

        return parseJavaVersionCommandOutput(javaCommand, new BufferedReader(new InputStreamReader(buffer.getInputStream())));
    }

    private JavaVersion parseJavaVersionCommandOutput(String javaExecutable, BufferedReader reader) {
        try {
            String versionStr = reader.readLine();
            while (versionStr != null) {
                Matcher matcher = Pattern.compile("(?:java|openjdk) version \"(.+?)\"( \\d{4}-\\d{2}-\\d{2}( LTS)?)?").matcher(versionStr);
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
