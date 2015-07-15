/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.testkit.runner.daemon;

import org.gradle.api.Transformer;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GradleDaemonAnalyzer {
    public static final String LOG_FILE_EXTENSION = ".out.log";
    private final File daemonLogsDir;

    public GradleDaemonAnalyzer(File daemonBaseDir, String gradleVersion) {
        daemonLogsDir = new File(daemonBaseDir, gradleVersion);
    }

    public List<GradleDaemon> getDaemons() {
        if(daemonLogsDir.exists()) {
            File[] daemonLogFiles = daemonLogsDir.listFiles(new DaemonLogFilenameFilter());

            return CollectionUtils.collect(daemonLogFiles, new Transformer<GradleDaemon, File>() {
                public GradleDaemon transform(File file) {
                    return new GradleDaemon(parsePid(file));
                }
            });
        }

        return Collections.emptyList();
    }

    private String parsePid(File daemonLogFile) {
        Pattern pattern = Pattern.compile("daemon\\-(\\d*)\\.out\\.log");
        Matcher matcher = pattern.matcher(daemonLogFile.getName());

        if(matcher.find()) {
            return matcher.group(1);
        }

        throw new IllegalStateException(String.format("PID could not be parsed from daemon log file name '%s'", daemonLogFile.getName()));
    }

    private class DaemonLogFilenameFilter implements FilenameFilter {
        public boolean accept(File dir, String name) {
            return name.endsWith(LOG_FILE_EXTENSION);
        }
    }
}
