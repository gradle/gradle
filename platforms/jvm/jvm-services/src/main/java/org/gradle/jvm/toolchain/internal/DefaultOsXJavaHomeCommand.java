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

package org.gradle.jvm.toolchain.internal;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.process.internal.ExecException;
import org.gradle.process.internal.ExecHandleBuilder;
import org.gradle.process.internal.ExecHandleFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collections;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DefaultOsXJavaHomeCommand implements OsXJavaHomeCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultOsXJavaHomeCommand.class);

    private static final Pattern INSTALLATION_PATTERN = Pattern.compile(".+\\s+(/.+)");
    private final ExecHandleFactory execHandleFactory;

    public DefaultOsXJavaHomeCommand(ExecHandleFactory execHandleFactory) {
        this.execHandleFactory = execHandleFactory;
    }

    @VisibleForTesting
    static Set<File> parse(Reader output) {
        BufferedReader reader = new BufferedReader(output);
        return reader.lines().flatMap(line -> {
            Matcher matcher = INSTALLATION_PATTERN.matcher(line);
            if (matcher.matches()) {
                String javaHome = matcher.group(1);
                return Stream.of(javaHome);
            }
            return Stream.empty();
        }).map(File::new).collect(Collectors.toSet());
    }

    @Override
    public Set<File> findJavaHomes() {
        try {
            final Reader output = executeJavaHome();
            return parse(output);
        } catch (ExecException e) {
            String errorMessage = "Java Toolchain auto-detection failed to find local MacOS system JVMs";
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(errorMessage, e);
            } else {
                LOGGER.info(errorMessage);
            }
        }
        return Collections.emptySet();
    }

    private Reader executeJavaHome() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        executeCommand(outputStream);
        return new InputStreamReader(new ByteArrayInputStream(outputStream.toByteArray()));
    }

    @VisibleForTesting
    protected void executeCommand(ByteArrayOutputStream outputStream) {
        ExecHandleBuilder execHandleBuilder = execHandleFactory.newExec();
        execHandleBuilder.workingDir(new File(".").getAbsoluteFile());
        execHandleBuilder.commandLine("/usr/libexec/java_home", "-V");
        execHandleBuilder.getEnvironment().remove("JAVA_VERSION"); //JAVA_VERSION filters the content of java_home's output
        execHandleBuilder.getErrorOutput().set(outputStream); // verbose output is written to stderr
        execHandleBuilder.getStandardOutput().set(new ByteArrayOutputStream());
        execHandleBuilder.build().start().waitForFinish().assertNormalExitValue();
    }
}
