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

package org.gradle.performance.plugin

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.gradle.BuildAdapter
import org.gradle.BuildResult
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger

import java.lang.management.ManagementFactory
import java.math.RoundingMode

@CompileStatic
class MeasurementPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.getGradle().addBuildListener(new BuildAdapter() {
            @Override
            void buildFinished(BuildResult result) {
                def rootProject = result.gradle.rootProject
                handleHeapDump(rootProject, rootProject.logger)
                handleHeapMeasurement(rootProject, rootProject.logger)
                handleExternalResourcesStats()
            }
        })
    }

    @CompileDynamic
    void handleHeapDump(Project project, Logger logger) {
        if (project.hasProperty('heapdump')) {
            boolean skipHeapDump = false

            if (['buildExperimentPhase', 'buildExperimentIterationNumber', 'buildExperimentIterationMax'].every { project.hasProperty(it) }) {
                // only dump heap automaticly on the last iteration of the testrun (not in warmup)
                if (project.buildExperimentPhase != 'measurement' || project.buildExperimentIterationNumber != project.buildExperimentIterationMax) {
                    skipHeapDump = true
                }
            }

            if (!skipHeapDump) {
                def hotspotDiagnosticMXBean
                try {
                    def hotspotDiagnosticMXBeanClass = Class.forName("com.sun.management.HotSpotDiagnosticMXBean")
                    hotspotDiagnosticMXBean = ManagementFactory.getPlatformMXBeans(hotspotDiagnosticMXBeanClass).get(0)
                } catch (Exception e) {
                    logger.error("Couldn't locate MBean for doing heap dump.", e)
                }
                if (hotspotDiagnosticMXBean) {
                    logger.lifecycle "Creating heap dump..."
                    def dumpDescription = (project.hasProperty("buildExperimentDisplayName") ? (project.name + "_" + project.buildExperimentDisplayName) : project.name).replaceAll("[^a-zA-Z0-9.-]", "_").replaceAll("[_]+", "_")
                    File dumpFile = new File(System.getProperty("java.io.tmpdir"), "heapdump-${dumpDescription}-${new Date().format('yyyy-MM-dd-HH-mm-ss')}.hprof")
                    hotspotDiagnosticMXBean.dumpHeap(dumpFile.absolutePath, true)
                    logger.lifecycle "Dumped to ${dumpFile.absolutePath}."
                }
            }
        }
    }

    void handleHeapMeasurement(Project project, Logger logger) {
        def heap = ManagementFactory.memoryMXBean.heapMemoryUsage
        def nonHeap = ManagementFactory.memoryMXBean.nonHeapMemoryUsage
        logger.lifecycle "BEFORE GC"
        logger.lifecycle "heap: ${format(heap.used)} (initial ${format(heap.init)}, committed ${format(heap.committed)}, max ${format(heap.max)}"
        logger.lifecycle "nonHeap: ${format(nonHeap.used)} (initial ${format(nonHeap.init)}, committed ${format(nonHeap.committed)}, max ${format(nonHeap.max)}"

        ManagementFactory.memoryMXBean.gc()

        heap = ManagementFactory.memoryMXBean.heapMemoryUsage
        nonHeap = ManagementFactory.memoryMXBean.nonHeapMemoryUsage
        logger.lifecycle "AFTER GC"
        logger.lifecycle "heap: ${format(heap.used)} (initial ${format(heap.init)}, committed ${format(heap.committed)}, max ${format(heap.max)}"
        logger.lifecycle "nonHeap: ${format(nonHeap.used)} (initial ${format(nonHeap.init)}, committed ${format(nonHeap.committed)}, max ${format(nonHeap.max)}"
        project.buildDir.mkdirs()
        new File(project.buildDir, "totalMemoryUsed.txt").text = heap.used
    }

    @CompileDynamic
    def format(def value) {
        value = value / (1024 * 1024)
        value = value.setScale(4, RoundingMode.DOWN)
        return "${value}MB"
    }

    @CompileDynamic
    void handleExternalResourcesStats() {
        if (System.getProperty('gradle.externalresources.recordstats')) {
            def statistics = Class.forName('org.gradle.internal.resource.transfer.DefaultExternalResourceConnector').statistics
            println statistics
            statistics.reset()
        }
    }
}
