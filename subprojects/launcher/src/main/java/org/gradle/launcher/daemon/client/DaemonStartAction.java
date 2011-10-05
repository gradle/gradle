/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.launcher.daemon.client;

import org.gradle.api.GradleException;
import org.gradle.os.OperatingSystem;
import org.gradle.os.jna.WindowsProcessStarter;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DaemonStartAction {
    private final List<String> args = new ArrayList<String>();
    private File dir = GFileUtils.canonicalise(new File("."));

    public void args(Iterable<String> args) {
        for (String arg : args) {
            this.args.add(arg);
        }
    }

    public void workingDir(File dir) {
        this.dir = dir;
    }

    public void start() {
        try {
            dir.mkdirs();
            if (OperatingSystem.current().isWindows()) {
                StringBuilder commandLine = new StringBuilder();
                for (String arg : args) {
                    commandLine.append('"');
                    commandLine.append(arg);
                    commandLine.append("\" ");
                }

                new WindowsProcessStarter().start(dir, commandLine.toString());
            } else {
                ProcessBuilder builder = new ProcessBuilder(args);
                builder.directory(dir);
                Process process = builder.start();
                process.getOutputStream().close();
                process.getErrorStream().close();
                process.getInputStream().close();
            }
        } catch (Exception e) {
            throw new GradleException("Could not start Gradle daemon.", e);
        }
    }
}
