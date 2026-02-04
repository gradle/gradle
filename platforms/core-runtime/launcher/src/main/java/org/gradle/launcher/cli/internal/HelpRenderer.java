/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.launcher.cli.internal;

import org.gradle.cli.CommandLineParser;
import org.gradle.configuration.DefaultBuildClientMetaData;
import org.gradle.configuration.GradleLauncherMetaData;
import org.gradle.initialization.BuildClientMetaData;
import org.gradle.launcher.cli.converter.BuildLayoutConverter;
import org.gradle.launcher.cli.converter.BuildOptionBackedConverter;
import org.gradle.launcher.cli.converter.InitialPropertiesConverter;
import org.gradle.launcher.cli.converter.StartParameterConverter;
import org.gradle.launcher.daemon.configuration.DaemonBuildOptions;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Renders the output of {@code --help}.
 */
@NullMarked
public final class HelpRenderer {

    private HelpRenderer() {
    }

    public static String render(@Nullable String suggestedTaskSelector, boolean includeHintToExecuteHelpTaskOnProject) {
        return render(commandLineParser(), suggestedTaskSelector, includeHintToExecuteHelpTaskOnProject);
    }

    public static String render(CommandLineParser parser, @Nullable String suggestedTaskSelector, boolean includeHintToExecuteHelpTaskOnProject) {
        BuildClientMetaData metaData = new DefaultBuildClientMetaData(new GradleLauncherMetaData());

        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);

        if (includeHintToExecuteHelpTaskOnProject) {
            out.println();
            out.print("To see help contextual to the project, use ");
            metaData.describeCommand(out, "help");
            out.println();
        }

        out.println();
        out.print("To see more detail about a task, run ");
        metaData.describeCommand(out, "help --task <task>");
        out.println();
        if (suggestedTaskSelector != null && !suggestedTaskSelector.isEmpty()) {
            out.print("For example, ");
            metaData.describeCommand(out, "help --task " + suggestedTaskSelector);
            out.println();
        }
        out.println();
        out.print("To see a list of available tasks, run ");
        metaData.describeCommand(out, "tasks");
        out.println();

        out.println();
        out.print("USAGE: ");
        metaData.describeCommand(out, "[option...]", "[task...]");
        out.println();
        out.println();
        parser.printUsage(out);
        out.println();

        out.flush();
        return sw.toString();
    }

    private static CommandLineParser commandLineParser() {
        // Build a parser with the same core options wiring and built-in flags
        CommandLineParser parser = new CommandLineParser();
        // Wire core converters so the option table matches CLI (initial/build layout/start parameter/daemon)
        new InitialPropertiesConverter().configure(parser);
        new BuildLayoutConverter().configure(parser);
        new StartParameterConverter().configure(parser);
        new BuildOptionBackedConverter<>(new DaemonBuildOptions()).configure(parser);
        // Built-in options: -h/--help/-?, -v/--version, -V/--show-version
        parser.option("h", "?", "help").hasDescription("Shows this help message.");
        parser.option("v", "version").hasDescription("Print version info and exit.");
        parser.option("V", "show-version").hasDescription("Print version info and continue.");
        return parser;
    }
}
