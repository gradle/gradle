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

package org.gradle.launcher.cli.converter;

import org.gradle.cli.AbstractCommandLineConverter;
import org.gradle.cli.CommandLineArgumentException;
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.gradle.initialization.BuildLayoutParameters;
import org.gradle.launcher.daemon.configuration.DaemonParameters;

public class DaemonCommandLineConverter extends AbstractCommandLineConverter<DaemonParameters> {

    private static final String DAEMON = "daemon";
    private static final String NO_DAEMON = "no-daemon";

    protected DaemonParameters newInstance() {
        return new DaemonParameters(new BuildLayoutParameters());
    }

    public DaemonParameters convert(ParsedCommandLine args, DaemonParameters target) throws CommandLineArgumentException {
        if (args.hasOption(NO_DAEMON)) {
            return target.setEnabled(false);
        }
        if (args.hasOption(DAEMON)) {
            return target.setEnabled(true);
        }
        return target;
    }

    public void configure(CommandLineParser parser) {
        parser.option(DAEMON).hasDescription("Uses the Gradle daemon to run the build. Starts the daemon if not running.");
        parser.option(NO_DAEMON).hasDescription("Do not use the Gradle daemon to run the build.");
        parser.allowOneOf(DAEMON, NO_DAEMON);
    }
}
