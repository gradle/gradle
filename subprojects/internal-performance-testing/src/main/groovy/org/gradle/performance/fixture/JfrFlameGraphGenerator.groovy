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
import org.openjdk.jmc.common.item.IItemCollection
import org.openjdk.jmc.flightrecorder.JfrLoaderToolkit

import static org.gradle.performance.fixture.JfrToStacksConverter.EventType
import static org.gradle.performance.fixture.JfrToStacksConverter.Options

/**
 * Generates flame graphs based on JFR recordings.
 */
@CompileStatic
@PackageScope
class JfrFlameGraphGenerator {

    private JfrToStacksConverter stacksConverter = new JfrToStacksConverter()
    private FlameGraphGenerator flameGraphGenerator = new FlameGraphGenerator()

    void generateGraphs(File jfrOutputDir) {
        List<IItemCollection> recordings = jfrOutputDir.listFiles()
            .findAll { it.name.endsWith(".jfr") }
            .collect { JfrLoaderToolkit.loadEvents(it) }
        File flamegraphDir = jfrOutputDir.getParentFile()
        EventType.values().each { EventType type ->
            DetailLevel.values().each { DetailLevel level ->
                def stacks = generateStacks(flamegraphDir, recordings, type, level)
                generateFlameGraph(stacks, type, level)
                generateIcicleGraph(stacks, type, level)
            }
        }
    }

    private File generateStacks(File baseDir, Collection<IItemCollection> recordings, EventType type, DetailLevel level) {
        File stacks = File.createTempFile("stacks", ".txt")
        stacksConverter.convertToStacks(recordings, stacks, new Options(type, level.isShowArguments(), level.isShowLineNumbers()))
        File sanitizedStacks = stacksFileName(baseDir, type, level)
        level.getSanitizer().sanitize(stacks, sanitizedStacks)
        stacks.delete()
        return sanitizedStacks
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

    enum DetailLevel {
        RAW(
            true,
            true,
            Arrays.asList("--minwidth", "0.5"),
            Arrays.asList("--minwidth", "1"),
            new FlameGraphSanitizer(FlameGraphSanitizer.COLLAPSE_BUILD_SCRIPTS, FlameGraphSanitizer.NORMALIZE_LAMBDA_NAMES)
        ),
        SIMPLIFIED(
            false,
            false,
            Arrays.asList("--minwidth", "1"),
            Arrays.asList("--minwidth", "2"),
            new FlameGraphSanitizer(FlameGraphSanitizer.COLLAPSE_BUILD_SCRIPTS, FlameGraphSanitizer.NORMALIZE_LAMBDA_NAMES, FlameGraphSanitizer.COLLAPSE_GRADLE_INFRASTRUCTURE, FlameGraphSanitizer.SIMPLE_NAMES)
        )

        private final boolean showArguments
        private final boolean showLineNumbers
        private List<String> flameGraphOptions
        private List<String> icicleGraphOptions
        private FlameGraphSanitizer sanitizer

        DetailLevel(boolean showArguments, boolean showLineNumbers, List<String> flameGraphOptions, List<String> icicleGraphOptions, FlameGraphSanitizer sanitizer) {
            this.showArguments = showArguments
            this.showLineNumbers = showLineNumbers
            this.flameGraphOptions = flameGraphOptions
            this.icicleGraphOptions = icicleGraphOptions
            this.sanitizer = sanitizer
        }

        boolean isShowArguments() {
            return showArguments
        }

        boolean isShowLineNumbers() {
            return showLineNumbers
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
