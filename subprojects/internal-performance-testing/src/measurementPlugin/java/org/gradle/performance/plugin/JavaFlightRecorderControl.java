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

package org.gradle.performance.plugin;

import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import java.io.File;

/**
 * Add -Porg.gradle.performance.jfrdump parameter to build parameters to trigger JFR dump at the end of the build.
 *
 * The expected JFR recording name is GradleProfiling. Example of JVM args to use for profiling:
 * -XX:+UnlockCommercialFeatures -XX:+FlightRecorder -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints
 * -XX:FlightRecorderOptions=stackdepth=1024 -XX:StartFlightRecording=settings=profile,name=GradleProfiling
 *
 */
public class JavaFlightRecorderControl {
    public static final String JFR_CREATE_DUMP = "org.gradle.performance.jfrdump";

    static void handle(final Project project, Logger logger) {
        if (project.hasProperty(JFR_CREATE_DUMP)) {
            Object dumpPropertyValue = project.property(JFR_CREATE_DUMP);
            File targetDirectory = dumpPropertyValue != null && !"".equals(dumpPropertyValue) ? new File(String.valueOf(dumpPropertyValue)) : null;
            File recordingFile = MeasurementPlugin.createFileName(project, targetDirectory, "GradleProfiling", ".jfr");
            try {
                logger.lifecycle("Creating JFR dump...");
                String output = DiagnosticCommandMBeanHelper.jfrDump("GradleProfiling", recordingFile);
                logger.lifecycle(output);
            } catch (Exception e) {
                logger.warn("Error creating JFR dump", e);
            }
        }
    }

}
