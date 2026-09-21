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
import org.gradle.cache.internal.BuildScopeCacheDir;
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.gradle.initialization.StartParameterBuildOptions.ProjectCacheDirOption;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.internal.id.UniqueId;
import org.gradle.launcher.configuration.BuildLayoutResult;
import org.jspecify.annotations.NullMarked;

import java.io.File;
import java.util.Map;

/**
 * Locates the file that build output is written to in agent mode.
 *
 * <p>The file lives in the project cache directory, which is resolved the same way the build itself resolves it,
 * in a directory that is unique to the invocation.</p>
 */
@NullMarked
public class AgentOutputLocation {
    // TODO Clean up the directories of old invocations
    private static final String BUILDS_DIR_PATH = "agent/builds";
    private static final String OUTPUT_FILE_NAME = "build-output.log";

    private final BuildLayoutFactory buildLayoutFactory;
    private final ProjectCacheDirOption projectCacheDirOption = new ProjectCacheDirOption();

    public AgentOutputLocation(BuildLayoutFactory buildLayoutFactory) {
        this.buildLayoutFactory = buildLayoutFactory;
    }

    public void configure(CommandLineParser parser) {
        projectCacheDirOption.configure(parser);
    }

    public File resolve(ParsedCommandLine commandLine, Map<String, String> properties, BuildLayoutResult buildLayout) {
        StartParameterInternal settings = new StartParameterInternal();
        buildLayout.applyTo(settings);
        projectCacheDirOption.applyFromProperty(properties, settings);
        projectCacheDirOption.applyFromCommandLine(commandLine, settings);

        File projectCacheDir = new BuildScopeCacheDir(
            buildLayout::getGradleUserHomeDir,
            buildLayoutFactory.getLayoutFor(buildLayout.toLayoutConfiguration()),
            settings
        ).getDir();
        // Generated here rather than taken from the build, as the file is needed before any daemon has been contacted
        String invocationId = UniqueId.generate().asString();
        return new File(projectCacheDir, BUILDS_DIR_PATH + "/" + invocationId + "/" + OUTPUT_FILE_NAME);
    }
}
