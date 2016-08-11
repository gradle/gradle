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

import org.codehaus.groovy.runtime.DateGroovyMethods;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.management.PlatformManagedObject;
import java.math.BigDecimal;
import java.util.Date;

public class MeasurementPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        Gradle gradle = project.getGradle();

        gradle.addBuildListener(new BuildAdapter() {
            @Override
            public void buildFinished(BuildResult result) {
                BuildEventTimeStamps.buildFinished(result);
                Project rootProject = result.getGradle().getRootProject();
                handleHeapDump(rootProject, rootProject.getLogger());
                handleHeapMeasurement(rootProject, rootProject.getLogger());
                handleExternalResourcesStats();
            }

        });

        gradle.getTaskGraph().addTaskExecutionGraphListener(new TaskExecutionGraphListener() {
            @Override
            public void graphPopulated(TaskExecutionGraph graph) {
                BuildEventTimeStamps.configurationEvaluated();
            }
        });
    }

    public void handleHeapDump(final Project project, Logger logger) {
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
                    final File dumpFile = new File(System.getProperty("java.io.tmpdir"), "heapdump-" + dumpDescription + "-" + DateGroovyMethods.format(new Date(), "yyyy-MM-dd-HH-mm-ss") + ".hprof");
                    DefaultGroovyMethods.invokeMethod(hotspotDiagnosticMXBean, "dumpHeap", new Object[]{dumpFile.getAbsolutePath(), true});
                    logger.lifecycle("Dumped to " + dumpFile.getAbsolutePath() + ".");
                }

            }

        }

    }

    public void handleHeapMeasurement(Project project, Logger logger) {
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        MemoryUsage nonHeap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        logger.lifecycle("BEFORE GC");
        logger.lifecycle("heap: " + format(heap.getUsed()) + " (initial " + format(heap.getInit()) + ", committed " + format(heap.getCommitted()) + ", max " + format(heap.getMax()));
        logger.lifecycle("nonHeap: " + format(nonHeap.getUsed()) + " (initial " + format(nonHeap.getInit()) + ", committed " + format(nonHeap.getCommitted()) + ", max " + format(nonHeap.getMax()));

        ManagementFactory.getMemoryMXBean().gc();

        heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        nonHeap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        logger.lifecycle("AFTER GC");
        logger.lifecycle("heap: " + format(heap.getUsed()) + " (initial " + format(heap.getInit()) + ", committed " + format(heap.getCommitted()) + ", max " + format(heap.getMax()));
        logger.lifecycle("nonHeap: " + format(nonHeap.getUsed()) + " (initial " + format(nonHeap.getInit()) + ", committed " + format(nonHeap.getCommitted()) + ", max " + format(nonHeap.getMax()));

        project.getBuildDir().mkdirs();
        File totalMemoryUsedFile = new File(project.getBuildDir(), "totalMemoryUsed.txt");
        try {
            FileWriter writer = new FileWriter(totalMemoryUsedFile);
            try {
                writer.write(String.valueOf(heap.getUsed()));
            } finally {
                writer.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String format(long value) {
        BigDecimal divisor = new BigDecimal(1024 * 1024);
        BigDecimal megabytes = new BigDecimal(value).divide(divisor).setScale(4, BigDecimal.ROUND_DOWN);
        return megabytes.toString() + "MB";
    }

    public void handleExternalResourcesStats() {
        if (System.getProperty("gradle.externalresources.recordstats") != null) {
            try {
                Object statistics = DefaultGroovyMethods.invokeMethod(Class.forName("org.gradle.internal.resource.transfer.DefaultExternalResourceConnector"), "getStatistics", new Object[0]);
                DefaultGroovyMethods.println(this, statistics);
                DefaultGroovyMethods.invokeMethod(statistics, "reset", new Object[0]);
            } catch (ClassNotFoundException e) {
                // ignore
            }
        }
    }
}
