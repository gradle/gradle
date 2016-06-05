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

package org.gradle.process.internal.worker;

import org.apache.commons.io.IOUtils;
import org.gradle.api.JavaVersion;
import org.gradle.api.Nullable;
import org.gradle.process.JavaExecSpec;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DefaultJavaExecutableVersionProber implements JavaExecutableVersionProber {
    static final Pattern JAVA_VERSION_PATTERN = Pattern.compile("^java version \"(.*?)\"$");

    @Override
    @Nullable
    public JavaVersion probeVersion(JavaExecSpec execSpec) {
        return findJavaVersion(execJavaVersion(execSpec));
    }

    private List<String> execJavaVersion(JavaExecSpec execSpec) {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.redirectErrorStream(true);
        processBuilder.command(execSpec.getExecutable(), "-version");
        try {
            Process process = processBuilder.start();
            return IOUtils.readLines(process.getInputStream());
        } catch (IOException e) {
            // ignore
        }
        return Collections.emptyList();
    }

    @Nullable
    JavaVersion findJavaVersion(List<String> lines) {
        for (String line : lines) {
            Matcher matcher = JAVA_VERSION_PATTERN.matcher(line);
            if (matcher.matches()) {
                return JavaVersion.toVersion(matcher.group(1));
            }
        }
        return null;
    }
}
