/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.cacheable;

import com.google.common.base.Joiner;
import org.gradle.api.GradleException;
import org.gradle.internal.UncheckedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class RunCxx implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(RunCxx.class);
    private final File workingDirectory;
    private final String command;
    private final List<String> arguments;

    @Inject
    public RunCxx(File workingDirectory, String command, List<String> arguments) {
        this.workingDirectory = workingDirectory;
        this.command = command;
        this.arguments = arguments;
    }

    @Override
    public void run() {
        try {
            LOGGER.info("Starting process '{}'. Working directory: {} Arguments: {}",
                command, workingDirectory, Joiner.on(' ').useForNull("null").join(arguments));
            List<String> commandWithArguments = new ArrayList<String>();
            commandWithArguments.add(command);
            commandWithArguments.addAll(arguments);

            Process process = new ProcessBuilder(commandWithArguments).directory(workingDirectory).start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new GradleException("Error running " + command + "\nExit code: " + exitCode);
            }
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
