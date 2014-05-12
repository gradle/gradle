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

package org.gradle.integtests.fixtures.jvm;

import org.gradle.api.JavaVersion;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the output of `java_home -V` to extract JVM installation details.
 */
class OsXJavaHomeParser {
    private static final Pattern NEW_FORMAT_PATTERN = Pattern.compile("\\s+(\\S+),\\s+(\\S+):\\s+\".*?\"\\s+(.+)");
    private static final Pattern OLD_FORMAT_PATTERN = Pattern.compile("\\s+(\\S+)\\s+\\((.*?)\\):\\s+(.+)");

    public List<JvmInstallation> parse(Reader output) throws IOException {
        ArrayList<JvmInstallation> result = new ArrayList<JvmInstallation>();
        BufferedReader reader = new BufferedReader(output);
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            Matcher matcher = NEW_FORMAT_PATTERN.matcher(line);
            if (matcher.matches()) {
                String version = matcher.group(1);
                String arch = matcher.group(2);
                String javaHome = matcher.group(3);
                result.add(new JvmInstallation(JavaVersion.toVersion(version), version, new File(javaHome), true, toArch(arch)));
            } else {
                matcher = OLD_FORMAT_PATTERN.matcher(line);
                if (matcher.matches()) {
                    String version = matcher.group(1);
                    String arch = matcher.group(2);
                    String javaHome = matcher.group(3);
                    result.add(new JvmInstallation(JavaVersion.toVersion(version), version, new File(javaHome), true, toArch(arch)));
                }
            }
        }
        return result;
    }

    private JvmInstallation.Arch toArch(String arch) {
        if (arch.equals("i386")) {
            return JvmInstallation.Arch.i386;
        } else if (arch.equals("x86_64")) {
            return JvmInstallation.Arch.x86_64;
        }
        return JvmInstallation.Arch.Unknown;
    }
}
