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

package org.gradle.plugin.devel.plugins;

import org.gradle.api.internal.classpath.Module;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.instrumentation.agent.AgentUtils;
import org.gradle.process.CommandLineArgumentProvider;
import org.jspecify.annotations.NullMarked;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@NullMarked
class InstrumentationAgentCommandLineArgumentProvider implements CommandLineArgumentProvider {

    private final ModuleRegistry moduleRegistry;

    InstrumentationAgentCommandLineArgumentProvider(ModuleRegistry moduleRegistry) {
        this.moduleRegistry = moduleRegistry;
    }

    @Override
    public Iterable<String> asArguments() {
        // The agent module is absent in non-distribution layouts (e.g. running tests against a
        // development checkout without a full Gradle distribution). Degrade silently: the test
        // task simply won't get -javaagent, mirroring how Gradle's own runtime treats this case.
        Module agent = moduleRegistry.findModule(AgentUtils.AGENT_MODULE_NAME);
        if (agent == null) {
            return Collections.emptyList();
        }
        ClassPath classpath = agent.getImplementationClasspath();
        if (classpath.isEmpty()) {
            return Collections.emptyList();
        }
        List<File> files = classpath.getAsFiles();
        List<String> args = new ArrayList<>(files.size());
        for (File jar : files) {
            args.add("-javaagent:" + jar);
        }
        return args;
    }
}
