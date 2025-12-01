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
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.util.internal.DefaultGradleVersion;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Properties;

/**
 * Renders the output of {@code --version}.
 */
@NullMarked
public class VersionInfoRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger(VersionInfoRenderer.class);

    private VersionInfoRenderer() {
    }

    public static String render(String daemonJvm) {
        return render(daemonJvm, null);
    }

    public static String renderWithLauncherJvm(String daemonJvm) {
        return render(daemonJvm, Jvm.current().toString());
    }

    private static String render(String daemonJvm, String launcherJvm) {
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
        if (launcherJvm != null) {
            printAligned(out, "Launcher JVM", launcherJvm, maxKey);
        }
        printAligned(out, "Daemon JVM", daemonJvm, maxKey);
        printAligned(out, "OS", OperatingSystem.current().toString(), maxKey);
        out.println();
        out.flush();
        return sw.toString();
    }

    private static String resolveKotlinVersion() {
        // Read the same resource used by DefaultCommandLineActionFactory.KotlinDslVersion
        try (InputStream in = HelpRenderer.class.getClassLoader().getResourceAsStream("gradle-kotlin-dsl-versions.properties")) {
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
}
