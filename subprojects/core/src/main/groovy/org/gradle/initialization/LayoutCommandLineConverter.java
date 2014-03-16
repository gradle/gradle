/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.initialization;

import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.file.FileLookup;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.cli.AbstractCommandLineConverter;
import org.gradle.cli.CommandLineArgumentException;
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;

public class LayoutCommandLineConverter extends AbstractCommandLineConverter<BuildLayoutParameters> {

    public static final String GRADLE_USER_HOME = "g";
    private static final String NO_SEARCH_UPWARDS = "u";
    private static final String PROJECT_DIR = "p";
    private final FileLookup fileLookup;

    public LayoutCommandLineConverter(FileLookup fileLookup) {
        this.fileLookup = fileLookup;
    }

    protected BuildLayoutParameters newInstance() {
        return new BuildLayoutParameters();
    }

    public BuildLayoutParameters convert(ParsedCommandLine options, BuildLayoutParameters target) throws CommandLineArgumentException {
        FileResolver resolver = fileLookup.getFileResolver(target.getProjectDir());
        if (options.hasOption(NO_SEARCH_UPWARDS)) {
            target.setSearchUpwards(false);
        }
        if (options.hasOption(PROJECT_DIR)) {
            target.setProjectDir(resolver.resolve(options.option(PROJECT_DIR).getValue()));
        }
        if (options.hasOption(GRADLE_USER_HOME)) {
            target.setGradleUserHomeDir(resolver.resolve(options.option(GRADLE_USER_HOME).getValue()));
        }
        return target;
    }

    public void configure(CommandLineParser parser) {
        parser.option(NO_SEARCH_UPWARDS, "no-search-upward").hasDescription(String.format("Don't search in parent folders for a %s file.", Settings.DEFAULT_SETTINGS_FILE));
        parser.option(PROJECT_DIR, "project-dir").hasArgument().hasDescription("Specifies the start directory for Gradle. Defaults to current directory.");
        parser.option(GRADLE_USER_HOME, "gradle-user-home").hasArgument().hasDescription("Specifies the gradle user home directory.");
    }
}