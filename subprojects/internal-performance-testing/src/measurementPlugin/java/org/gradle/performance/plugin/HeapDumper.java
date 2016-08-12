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

import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.PlatformManagedObject;
import java.text.SimpleDateFormat;
import java.util.Date;

class HeapDumper {
    static void handle(final Project project, Logger logger) {
        if (project.hasProperty("heapdump")) {
            boolean skipHeapDump = false;

            if (project.hasProperty("buildExperimentPhase") && project.hasProperty("buildExperimentIterationNumber") && project.hasProperty("buildExperimentIterationMax")) {
                // only dump heap automaticly on the last iteration of the testrun (not in warmup)
                if (!"measurement".equals(project.property("buildExperimentPhase")) || !project.property("buildExperimentIterationNumber").equals(project.property("buildExperimentIterationMax"))) {
                    skipHeapDump = true;
                }
            }

            if (!skipHeapDump) {
                PlatformManagedObject hotspotDiagnosticMXBean = null;
                try {
                    Class<? extends PlatformManagedObject> hotspotDiagnosticMXBeanClass = (Class<? extends PlatformManagedObject>) Class.forName("com.sun.management.HotSpotDiagnosticMXBean");
                    hotspotDiagnosticMXBean = ManagementFactory.getPlatformMXBeans(hotspotDiagnosticMXBeanClass).get(0);
                } catch (Exception e) {
                    logger.error("Couldn't locate MBean for doing heap dump.", e);
                }

                if (hotspotDiagnosticMXBean != null) {
                    logger.lifecycle("Creating heap dump...");
                    final String dumpDescription = (project.hasProperty("buildExperimentDisplayName") ? (project.getName() + "_" + project.property("buildExperimentDisplayName")) : project.getName()).replaceAll("[^a-zA-Z0-9.-]", "_").replaceAll("[_]+", "_");
                    final File dumpFile = new File(System.getProperty("java.io.tmpdir"), "heapdump-" + dumpDescription + "-" + new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date()) + ".hprof");
                    DefaultGroovyMethods.invokeMethod(hotspotDiagnosticMXBean, "dumpHeap", new Object[]{dumpFile.getAbsolutePath(), true});
                    logger.lifecycle("Dumped to " + dumpFile.getAbsolutePath() + ".");
                }

            }

        }

    }
}
