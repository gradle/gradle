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
import org.gradle.util.internal.KotlinDslVersion;
import org.jspecify.annotations.NullMarked;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Renders the output of {@code --version}.
 */
@NullMarked
public class VersionInfoRenderer {

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
        printAligned(out, "Kotlin", KotlinDslVersion.current().getKotlinVersion(), maxKey);
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

    private static void printAligned(PrintWriter out, String key, String value, int maxKey) {
        out.print(key + ": ");
        for (int i = key.length(); i < maxKey + 1; i++) {
            out.print(' ');
        }
        out.println(value);
    }
}
