/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.performance.android;

import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpecBuilder;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        OptionParser parser = new OptionParser();
        ArgumentAcceptingOptionSpec<String> projectFlag = parser.accepts("project-dir", "Gradle project directory").withRequiredArg();
        ArgumentAcceptingOptionSpec<String> gradleInstallFlag = parser.accepts("gradle-install", "Gradle installation to run the build with").withRequiredArg();
        OptionSpecBuilder sync = parser.accepts("sync", "Simulate Gradle project synchronization");
        OptionSpecBuilder embeddedFlag = parser.accepts("embedded", "Run build in-process");
        OptionSet options = parser.parse(args);
        if (!options.has(projectFlag)) {
            System.out.println("No project directory specified.");
            System.out.println();
            parser.printHelpOn(System.out);
            return;
        }

        File buildDir = new File(options.valueOf(projectFlag));
        File gradleInstallDir = options.hasArgument(gradleInstallFlag) ? new File(options.valueOf(gradleInstallFlag)) : null;
        boolean embedded = options.hasArgument(embeddedFlag);
        boolean hasSimulation = options.has(sync);
        if (options.has(sync) || hasSimulation) {
            // defaults to 'sync', which is the only mode right now, hence the weird test above
            fetch(buildDir, gradleInstallDir, embedded);
        }
        System.exit(0);
    }

    private static void fetch(File buildDir, File gradleInstallDir, boolean embedded) {
        System.out.println("* Fetching model for " + buildDir);
        System.out.println("* Using tooling API " + GradleVersion.current().getVersion());

        Timer timer = new Timer();

        GradleConnector gradleConnector = GradleConnector.newConnector();
        gradleConnector.forProjectDirectory(buildDir);
        ((DefaultGradleConnector) gradleConnector).embedded(embedded);
        if (gradleInstallDir != null) {
            gradleConnector.useInstallation(gradleInstallDir);
        }

        ProjectConnection connect = gradleConnector.connect();
        try {
            for (int i = 0; i < 5; i++) {
                SyncAction.withProjectConnection(connect, null);
            }
        } finally {
            connect.close();
        }

        timer.stop();
        System.out.println("total time: " + timer.duration());
    }
}
