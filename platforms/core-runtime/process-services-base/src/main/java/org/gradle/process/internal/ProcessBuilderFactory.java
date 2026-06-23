/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.process.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Creates a {@link java.lang.ProcessBuilder} based on a {@link ExecHandle}.
 */
public class ProcessBuilderFactory {
    public ProcessBuilder createProcessBuilder(ProcessSettings processSettings) {
        List<String> commandWithArguments = new ArrayList<String>();
        commandWithArguments.add(processSettings.getCommand());
        commandWithArguments.addAll(processSettings.getArguments());

        ProcessBuilder processBuilder = new ProcessBuilder(commandWithArguments);
        processBuilder.directory(processSettings.getDirectory());
        processBuilder.redirectErrorStream(processSettings.getRedirectErrorStream());

        Map<String, String> environment = processBuilder.environment();
        environment.clear();
        environment.putAll(processSettings.getEnvironment());

        return processBuilder;
    }
}
