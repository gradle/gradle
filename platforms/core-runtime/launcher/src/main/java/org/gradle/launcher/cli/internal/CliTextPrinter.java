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

import org.apache.tools.ant.Main;
import org.codehaus.groovy.util.ReleaseInfo;
import org.gradle.cli.CommandLineParser;
import org.gradle.initialization.BuildClientMetaData;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.launcher.cli.converter.BuildLayoutConverter;
import org.gradle.launcher.cli.converter.BuildOptionBackedConverter;
import org.gradle.launcher.cli.converter.InitialPropertiesConverter;
import org.gradle.launcher.cli.converter.StartParameterConverter;
import org.gradle.launcher.daemon.configuration.DaemonBuildOptions;
import org.gradle.util.internal.DefaultGradleVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Properties;

/**
 * Produces the exact plaintext for {@code --help} and {@code --version} so both the CLI launcher and the Tooling API can reuse it.
 */
public final class CliTextPrinter {
    private static final Logger LOGGER = LoggerFactory.getLogger(CliTextPrinter.class);
    private CliTextPrinter() {}

    public static String renderVersionInfo(BuildClientMetaData metaData, String daemonJvmCriteria) {
        DefaultGradleVersion currentVersion = DefaultGradleVersion.current();
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        out.println();
        out.println("------------------------------------------------------------");
        out.println("Gradle " + currentVersion.getVersion());
        out.println("------------------------------------------------------------");
        out.println();
        int maxKey = "Launcher JVM".length();
        printAligned(out, "Build time", currentVersion.getBuildTimestamp(), maxKey);
        printAligned(out, "Revision", currentVersion.getGitRevision(), maxKey);
        out.println();
        printAligned(out, "Kotlin", resolveKotlinVersion(), maxKey);
        printAligned(out, "Groovy", ReleaseInfo.getVersion(), maxKey);
        printAligned(out, "Ant", Main.getAntVersion(), maxKey);
        printAligned(out, "Launcher JVM", Jvm.current().toString(), maxKey);
        printAligned(out, "Daemon JVM", daemonJvmCriteria, maxKey);
        printAligned(out, "OS", OperatingSystem.current().toString(), maxKey);
        out.println();
        out.flush();
        return sw.toString();
    }

    private static String resolveKotlinVersion() {
        // Read the same resource used by DefaultCommandLineActionFactory.KotlinDslVersion
        try (InputStream in = CliTextPrinter.class.getClassLoader().getResourceAsStream("gradle-kotlin-dsl-versions.properties")) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                String v = props.getProperty("kotlin");
                if (v != null) {
                    return v;
                }
            }
        } catch (IOException ex) {
            // Best-effort: if we cannot read the kotlin version resource, fall back to "unknown" and log at debug level.
            LOGGER.debug("Unable to read gradle-kotlin-dsl-versions.properties for Kotlin version", ex);
        }
        return "unknown";
    }

    private static void printAligned(PrintWriter out, String key, String value, int maxKey) {
        out.print(key + ": ");
        for (int i = key.length(); i < maxKey + 1; i++) {
            out.print(' ');
        }
        out.println(value);
    }


    public static String renderHelp(BuildClientMetaData metaData, CommandLineParser parser, String suggestedTaskSelector) {
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);

        out.println();
        out.print("To see help contextual to the project, use ");
        metaData.describeCommand(out, "help");
        out.println();

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

    /**
     * Renders full CLI help output, including the dynamic options table produced by the command line parser.
     * This mirrors DefaultCommandLineActionFactory.ShowUsageAction + showUsage(), but writes to a String and
     * avoids starting the daemon.
     */
    public static String renderFullHelp(BuildClientMetaData metaData, String suggestedTaskSelector) {
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

        return renderHelp(metaData, parser, suggestedTaskSelector);
    }
}
