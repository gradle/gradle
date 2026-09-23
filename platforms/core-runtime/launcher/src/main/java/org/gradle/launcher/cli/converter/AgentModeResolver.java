/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.launcher.cli.converter;

import org.gradle.api.internal.StartParameterInternal;
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.gradle.initialization.StartParameterBuildOptions.AgentOption;
import org.jspecify.annotations.NullMarked;

import java.util.Map;

/**
 * Decides whether agent mode applies to an invocation.
 *
 * <p>Build options normally give environment variables the lowest precedence. Agent mode instead resolves as
 * command-line flag, then environment variable, then properties.
 * The properties are expected to have system properties already merged over those from {@code gradle.properties},
 * as done by {@link LayoutToPropertiesConverter}.</p>
 */
@NullMarked
public class AgentModeResolver {

    public enum AgentMode {
        NOT_REQUESTED,
        ENABLED;

        public boolean isEnabled() {
            return this != NOT_REQUESTED;
        }
    }

    private final AgentOption agentOption = new AgentOption();

    public void configure(CommandLineParser parser) {
        agentOption.configure(parser);
    }

    public AgentMode resolve(ParsedCommandLine commandLine, Map<String, String> properties, Map<String, String> environmentVariables) {
        StartParameterInternal settings = new StartParameterInternal();
        agentOption.applyFromProperty(properties, settings);
        agentOption.applyFromEnvVar(environmentVariables, settings);
        agentOption.applyFromCommandLine(commandLine, settings);

        return settings.isAgentMode() ? AgentMode.ENABLED : AgentMode.NOT_REQUESTED;
    }
}
