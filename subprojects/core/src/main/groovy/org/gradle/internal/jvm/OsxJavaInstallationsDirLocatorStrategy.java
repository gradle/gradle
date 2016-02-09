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

package org.gradle.internal.jvm;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.GradleException;
import org.gradle.process.internal.ExecAction;
import org.gradle.process.internal.ExecActionFactory;

import java.io.*;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OsxJavaInstallationsDirLocatorStrategy implements JavaInstallationsDirLocatorStrategy {

    private static final Pattern NEW_FORMAT_PATTERN = Pattern.compile("\\s+(\\S+),\\s+(\\S+):\\s+\".*?\"\\s+(.+)");
    private static final Pattern OLD_FORMAT_PATTERN = Pattern.compile("\\s+(\\S+)\\s+\\((.*?)\\):\\s+(.+)");

    private final ExecActionFactory execFactory;

    public OsxJavaInstallationsDirLocatorStrategy(ExecActionFactory execFactory) {
        this.execFactory = execFactory;
    }

    @Override
    public Set<File> findJavaInstallationsDirs() {
        ExecAction execAction = execFactory.newExecAction();
        execAction.setWorkingDir(new File(".").getAbsoluteFile());
        execAction.commandLine("/usr/libexec/java_home", "-V");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        // Output is written to stderr for some reason
        execAction.setErrorOutput(outputStream);
        execAction.setStandardOutput(new ByteArrayOutputStream());
        execAction.execute().assertNormalExitValue();
        Reader output = new InputStreamReader(new ByteArrayInputStream(outputStream.toByteArray()));
        return parseOutput(output);
    }

    private Set<File> parseOutput(Reader output) {
        try {
            BufferedReader reader = new BufferedReader(output);
            ImmutableSet.Builder<File> builder = ImmutableSet.<File>builder();
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                Matcher matcher = NEW_FORMAT_PATTERN.matcher(line);
                if (matcher.matches()) {
                    String javaHome = matcher.group(3);
                    builder.add(new File(javaHome));
                } else {
                    matcher = OLD_FORMAT_PATTERN.matcher(line);
                    if (matcher.matches()) {
                        String javaHome = matcher.group(3);
                        builder.add(new File(javaHome));
                    }
                }
            }
            return builder.build();
        } catch (IOException ex) {
            throw new GradleException("Could not locate installed JVMs.", ex);
        }
    }
}
