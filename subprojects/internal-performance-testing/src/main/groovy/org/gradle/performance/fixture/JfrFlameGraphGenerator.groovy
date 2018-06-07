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
class JfrFlameGraphGenerator {

    private JfrToStacksConverter stacksConverter = new JfrToStacksConverter()
    private FlameGraphGenerator flameGraphGenerator = new FlameGraphGenerator()

    void generateGraphs(File jfrRecording) {
        EventType.values().each { EventType type ->
            DetailLevel.values().each { DetailLevel level ->
                def stacks = generateStacks(jfrRecording, type, level)
                generateFlameGraph(stacks, type, level)
                generateIcicleGraph(stacks, type, level)
            }
        }
    }

    private File generateStacks(File jfrRecording, EventType type, DetailLevel level) {
        File stacks = File.createTempFile("stacks", ".txt")
        String[] options = level.stackConversionOptions + ["--event", type.id]
        stacksConverter.convertToStacks(jfrRecording, stacks, options)
        File baseDir = jfrRecording.parentFile
        File sanitizedStacks = stacksFileName(baseDir, type, level)
        level.sanitizer.sanitize(stacks, sanitizedStacks)
        stacks.delete()
        sanitizedStacks
    }

    void generateDifferentialGraphs(File baseDir) {
        File[] experiments = baseDir.listFiles()
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

    private File stacksFileName(File baseDir, EventType type, DetailLevel level) {
        new File(baseDir, "${type.id}/${level.name().toLowerCase()}/stacks.txt")
    }

    private void generateFlameGraph(File stacks, EventType type, DetailLevel level) {
        File flames = new File(stacks.parentFile, "flames.svg")
        String[] options = ["--title", type.displayName + " Flame Graph", "--countname", type.unitOfMeasure] + level.flameGraphOptions
        flameGraphGenerator.generateFlameGraph(stacks, flames, options)
        flames
    }

    private void generateIcicleGraph(File stacks, EventType type, DetailLevel level) {
        File icicles = new File(stacks.parentFile, "icicles.svg")
        String[] options = ["--title", type.displayName + " Icicle Graph", "--countname", type.unitOfMeasure, "--reverse", "--invert", "--colors", "aqua"] + level.icicleGraphOptions
        flameGraphGenerator.generateFlameGraph(stacks, icicles, options)
        icicles
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
        List<String> options = ["--title", type.displayName + "${negate ? " Forward " : " Backward "}Differential Icicle Graph", "--countname", type.unitOfMeasure, "--reverse", "--invert"] + level.flameGraphOptions
        if (negate) {
            options << "--negate"
        }
        flameGraphGenerator.generateFlameGraph(stacks, icicles, options as String[])
        icicles
    }

    private static enum EventType {
        CPU("cpu", "CPU", "samples"),
        ALLOCATION("allocation-tlab", "Allocation in new TLAB", "kB"),
        MONITOR_BLOCKED("monitor-blocked", "Java Monitor Blocked", "ms"),
        IO("io", "File and Socket IO", "ms");

        private final String id
        private final String displayName
        private final String unitOfMeasure

        private EventType(String id, String displayName, String unitOfMeasure) {
            this.unitOfMeasure = unitOfMeasure
            this.displayName = displayName
            this.id = id
        }
    }

    private static enum DetailLevel {
        RAW(
            [],
            ["--minwidth", "0.5"],
            ["--minwidth", "1"],
            new FlameGraphSanitizer(FlameGraphSanitizer.COLLAPSE_BUILD_SCRIPTS)
        ),
        SIMPLIFIED(
            ["--hide-arguments", "--ignore-line-numbers"],
            ["--minwidth", "1"],
            ["--minwidth", "2"],
            new FlameGraphSanitizer(FlameGraphSanitizer.COLLAPSE_BUILD_SCRIPTS, FlameGraphSanitizer.COLLAPSE_GRADLE_INFRASTRUCTURE, FlameGraphSanitizer.SIMPLE_NAMES)
        )

        private List<String> stackConversionOptions
        private List<String> flameGraphOptions
        private List<String> icicleGraphOptions
        private FlameGraphSanitizer sanitizer

        DetailLevel(List<String> stackConversionOptions, List<String> flameGraphOptions, List<String> icicleGraphOptions, FlameGraphSanitizer sanitizer) {
            this.stackConversionOptions = stackConversionOptions
            this.flameGraphOptions = flameGraphOptions
            this.icicleGraphOptions = icicleGraphOptions
            this.sanitizer = sanitizer
        }

        List<String> getStackConversionOptions() {
            return stackConversionOptions
        }

        List<String> getFlameGraphOptions() {
            return flameGraphOptions
        }

        List<String> getIcicleGraphOptions() {
            return icicleGraphOptions
        }

        FlameGraphSanitizer getSanitizer() {
            return sanitizer
        }
    }

}
