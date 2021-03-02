/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.performance.fixture

import groovy.transform.CompileStatic
import groovy.transform.PackageScope

/**
 * Generates flame graphs based on JFR recordings.
 */
@CompileStatic
@PackageScope
class JfrDifferentialFlameGraphGenerator implements ProfilerFlameGraphGenerator {

    private FlameGraphGenerator flameGraphGenerator = new FlameGraphGenerator()
    private final File flamesBaseDirectory

    JfrDifferentialFlameGraphGenerator(File flamesBaseDirectory) {
        this.flamesBaseDirectory = flamesBaseDirectory
    }

    @Override
    File getJfrOutputDirectory(BuildExperimentSpec spec) {
        def fileSafeName = spec.displayName.replaceAll('[^a-zA-Z0-9.-]', '-').replaceAll('-+', '-')
        // When the path is too long on Windows, then JProfiler can't write to the JPS file
        // Length 40 seems to work.
        // It may be better to create the flame graph in the tmp directory, and then move it to the right place after the build.
        def outputDir = new File(flamesBaseDirectory, shortenPath(fileSafeName, 40))
        outputDir.mkdirs()
        return outputDir
    }

    private static String shortenPath(String longName, int expectedMaxLength) {
        if (longName.length() <= expectedMaxLength) {
            return longName
        } else {
            return longName.substring(0, expectedMaxLength - 10) + "." + longName.substring(longName.length() - 9)
        }
    }

    @Override
    void generateDifferentialGraphs(BuildExperimentSpec experimentSpec) {
        Collection<File> experiments = getJfrOutputDirectory(experimentSpec).listFiles().findAll { it.directory }
        experiments.each { File experiment ->
            EventType.values().each { EventType type ->
                DetailLevel.values().each { DetailLevel level ->
                    experiments.findAll { it != experiment }.each { File baseline ->
                        def backwardDiff = generateDiff(experiment, baseline, type, level, false)
                        generateDifferentialFlameGraph(backwardDiff, type, level, false)
                        generateDifferentialIcicleGraph(backwardDiff, type, level, false)
                        def forwardDiff = generateDiff(experiment, baseline, type, level, true)
                        generateDifferentialFlameGraph(forwardDiff, type, level, true)
                        generateDifferentialIcicleGraph(forwardDiff, type, level, true)
                    }
                }
            }
        }
    }

    private File generateDiff(File versionUnderTest, File baseline, EventType type, DetailLevel level, boolean negate) {
        File underTestStacks = stacksFileName(versionUnderTest, type, level)
        File baselineStacks = stacksFileName(baseline, type, level)
        File diff = new File(underTestStacks.parentFile, "diffs/${negate ? "forward-" : "backward-"}diff-vs-${baseline.name}.txt")
        diff.parentFile.mkdirs()
        if (negate) {
            flameGraphGenerator.generateDiff(underTestStacks, baselineStacks, diff)
        } else {
            flameGraphGenerator.generateDiff(baselineStacks, underTestStacks, diff)
        }
        diff
    }

    private static File stacksFileName(File baseDir, EventType type, DetailLevel level) {
        new File(baseDir, "${type.id}/${level.name().toLowerCase()}/stacks.txt")
    }

    private void generateDifferentialFlameGraph(File stacks, EventType type, DetailLevel level, boolean negate) {
        File flames = new File(stacks.parentFile, "flame-" + stacks.name.replace(".txt", ".svg"))
        List<String> options = ["--title", type.displayName + "${negate ? " Forward " : " Backward "}Differential Flame Graph", "--countname", type.unitOfMeasure] + level.flameGraphOptions
        if (negate) {
            options << "--negate"
        }
        flameGraphGenerator.generateFlameGraph(stacks, flames, options as String[])
        flames
    }

    private void generateDifferentialIcicleGraph(File stacks, EventType type, DetailLevel level, boolean negate) {
        File icicles = new File(stacks.parentFile, "icicle-" + stacks.name.replace(".txt", ".svg"))
        List<String> options = ["--title", type.displayName + "${negate ? " Forward " : " Backward "}Differential Icicle Graph", "--countname", type.unitOfMeasure, "--reverse", "--invert"] + level.icicleGraphOptions
        if (negate) {
            options << "--negate"
        }
        flameGraphGenerator.generateFlameGraph(stacks, icicles, options as String[])
        icicles
    }

    enum EventType {
        CPU("cpu", "CPU", "samples"),
        ALLOCATION("allocation", "Allocation size", "kB"),
        MONITOR_BLOCKED("monitor-blocked", "Java Monitor Blocked", "ms"),
        IO("io", "File and Socket IO", "ms");

        private final String id
        private final String displayName
        private final String unitOfMeasure

        EventType(String id, String displayName, String unitOfMeasure) {
            this.id = id
            this.displayName = displayName
            this.unitOfMeasure = unitOfMeasure
        }
    }

    enum DetailLevel {
        RAW(['--minwidth', '0.5'], ['--minwidth', '1']),
        SIMPLIFIED(['--minwidth', '1'], ['--minwidth', '2'])

        private List<String> flameGraphOptions
        private List<String> icicleGraphOptions

        DetailLevel(List<String> flameGraphOptions, List<String> icicleGraphOptions) {
            this.flameGraphOptions = flameGraphOptions
            this.icicleGraphOptions = icicleGraphOptions
        }

        List<String> getFlameGraphOptions() {
            return flameGraphOptions
        }

        List<String> getIcicleGraphOptions() {
            return icicleGraphOptions
        }
    }
}
