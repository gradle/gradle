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

package org.gradle.performance.fixture;

import org.gradle.performance.measure.MeasuredOperation;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CompilationLoggingCollector implements DataCollector {
    private static final boolean HOTSPOT_LOGGING_ENABLED = System.getProperty("org.gradle.performance.hotspotlog") != null;

    @Override
    public List<String> getAdditionalJvmOpts(File workingDir) {
        List<String> jvmOpts = new ArrayList<String>();
        if (HOTSPOT_LOGGING_ENABLED) {
            // required by jitwatch, https://github.com/AdoptOpenJDK/jitwatch/wiki/Instructions
            jvmOpts.add("-XX:+UnlockDiagnosticVMOptions");
            // https://wiki.openjdk.java.net/display/HotSpot/LogCompilation
            jvmOpts.add("-XX:+LogCompilation");
            jvmOpts.add("-XX:LogFile=hotspot.log");
            jvmOpts.add("-XX:+TraceClassLoading");
            jvmOpts.add("-XX:+LogVMOutput");
            jvmOpts.add("-XX:-DisplayVMOutput");
        }
        return jvmOpts;
    }

    @Override
    public List<String> getAdditionalArgs(File workingDir) {
        return Collections.emptyList();
    }

    @Override
    public void collect(BuildExperimentInvocationInfo invocationInfo, MeasuredOperation operation) {
        if (HOTSPOT_LOGGING_ENABLED) {
            File hotspotLog = new File(invocationInfo.getProjectDir(), "hotspot.log");
            if (hotspotLog.exists()) {
                LogFiles.copyLogFile(hotspotLog, invocationInfo, "hotspot_", ".log");
            }
        }
    }

}
