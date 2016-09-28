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
import java.lang.management.ManagementFactory;
import java.lang.management.PlatformManagedObject;

import static org.gradle.performance.plugin.ReflectionUtil.getMethodByName;
import static org.gradle.performance.plugin.ReflectionUtil.invokeMethod;

/**
 * Add -Porg.gradle.performance.heapdump parameter to build parameters to trigger heapdump at the end of the build.
 *
 * The heapdump gets triggered once, on the last iteration of the measurement phase.
 *
 * Use -Porg.gradle.performance.heapdump=all to capture all objects on the heap. By default, it captures only the live objects.
 */
class HeapDumper {
    public static final String HEAP_DUMP_PROPERTY = "org.gradle.performance.heapdump";
    static void handle(final Project project, Logger logger) {
        if (project.hasProperty(HEAP_DUMP_PROPERTY)) {
            if (shouldDumpHeap(project)) {
                dumpHeap(logger, MeasurementPlugin.createFileName(project, null, "heapdump", ".hprof"), !"all".equals(project.property(HEAP_DUMP_PROPERTY)));
            }
        }
    }

    private static void dumpHeap(Logger logger, File dumpFile, boolean liveObjectsOnly) {
        PlatformManagedObject hotspotDiagnosticMXBean = null;
        try {
            Class<? extends PlatformManagedObject> hotspotDiagnosticMXBeanClass = (Class<? extends PlatformManagedObject>) Class.forName("com.sun.management.HotSpotDiagnosticMXBean");
            hotspotDiagnosticMXBean = ManagementFactory.getPlatformMXBean(hotspotDiagnosticMXBeanClass);
        } catch (Exception e) {
            logger.error("Couldn't locate MBean for doing heap dump.", e);
        }

        if (hotspotDiagnosticMXBean != null) {
            logger.lifecycle("Creating heap dump...");
            invokeMethod(hotspotDiagnosticMXBean, getMethodByName(hotspotDiagnosticMXBean.getClass(), "dumpHeap"), dumpFile.getAbsolutePath(), liveObjectsOnly);
            logger.lifecycle("Dumped to " + dumpFile.getAbsolutePath() + ".");
        }
    }

    private static boolean shouldDumpHeap(Project project) {
        if (isRunningInPerformanceTest(project) && isNotLastIterationOfMeasurementPhase(project)) {
            // only dump heap automaticly on the last iteration of the testrun (not in warmup)
            return false;
        }
        return true;
    }

    private static boolean isNotLastIterationOfMeasurementPhase(Project project) {
        return !"measurement".equals(project.property("buildExperimentPhase")) || !project.property("buildExperimentIterationNumber").equals(project.property("buildExperimentIterationMax"));
    }

    private static boolean isRunningInPerformanceTest(Project project) {
        return project.hasProperty("buildExperimentPhase") && project.hasProperty("buildExperimentIterationNumber") && project.hasProperty("buildExperimentIterationMax");
    }
}
